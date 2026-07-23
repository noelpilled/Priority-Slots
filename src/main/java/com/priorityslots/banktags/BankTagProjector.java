package com.priorityslots.banktags;

import com.priorityslots.domain.BankSnapshot;
import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityResolver;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import com.priorityslots.domain.SlotResolution;
import com.priorityslots.persistence.PriorityStateStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public final class BankTagProjector
{
	private final LayoutManager layoutManager;
	private final TagManager tagManager;
	private final BankTagsService bankTagsService;
	private final BankSearch bankSearch;
	private final PriorityStateStore stateStore;
	private final ClientThread clientThread;

	private final PriorityResolver resolver =
			new PriorityResolver();

	private final Map<String, PriorityBankTag>
			registeredTags = new HashMap<>();

	private final Map<String, Set<Integer>>
			cleanedManagedItemIdsByBindingId =
			new HashMap<>();

	@Inject
	public BankTagProjector(
			LayoutManager layoutManager,
			TagManager tagManager,
			BankTagsService bankTagsService,
			BankSearch bankSearch,
			PriorityStateStore stateStore,
			ClientThread clientThread)
	{
		this.layoutManager = Objects.requireNonNull(
				layoutManager,
				"layoutManager"
		);

		this.tagManager = Objects.requireNonNull(
				tagManager,
				"tagManager"
		);

		this.bankTagsService = Objects.requireNonNull(
				bankTagsService,
				"bankTagsService"
		);

		this.bankSearch = Objects.requireNonNull(
				bankSearch,
				"bankSearch"
		);

		this.stateStore = Objects.requireNonNull(
				stateStore,
				"stateStore"
		);

		this.clientThread = Objects.requireNonNull(
				clientThread,
				"clientThread"
		);
	}

	public PriorityState synchronize(
			PriorityState state,
			BankSnapshot bankSnapshot)
	{
		Objects.requireNonNull(state, "state");

		Objects.requireNonNull(
				bankSnapshot,
				"bankSnapshot"
		);

		removeUnusedRuntimeState(state);

		List<BankTagBinding> updatedBindings =
				new ArrayList<>();

		boolean stateChanged = false;
		boolean activeRefreshRequired = false;

		for (BankTagBinding binding
				: state.getBindings())
		{
			ProjectionResult result =
					projectBinding(
							binding,
							state,
							bankSnapshot
					);

			updatedBindings.add(result.binding);

			stateChanged |= result.stateChanged;

			activeRefreshRequired |=
					result.activeRefreshRequired;
		}

		PriorityState updatedState =
				state.withBindings(updatedBindings);

		if (stateChanged)
		{
			stateStore.save(updatedState);
		}

		if (activeRefreshRequired)
		{
			bankSearch.layoutBank();
		}

		return updatedState;
	}

	public void unregisterAll()
	{
		for (String bankTagName
				: registeredTags.keySet())
		{
			tagManager.unregisterTag(bankTagName);
		}

		registeredTags.clear();

		cleanedManagedItemIdsByBindingId.clear();
	}

	private ProjectionResult projectBinding(
			BankTagBinding binding,
			PriorityState state,
			BankSnapshot bankSnapshot)
	{
		Layout coreLayout =
				coreLayoutFor(
						binding.getBankTagName()
				);

		if (coreLayout == null)
		{
			log.debug(
					"Bank Tags layout '{}' does not exist",
					binding.getBankTagName()
			);

			updateDynamicTag(
					binding.getBankTagName(),
					Set.of()
			);

			return ProjectionResult.unchanged(
					binding
			);
		}

		List<SlotResolution> resolutions =
				resolver.resolveBinding(
						binding,
						state.definitionsById(),
						bankSnapshot
				);

		Map<String, SlotResolution>
				resolutionsByCellId =
				new HashMap<>();

		for (SlotResolution resolution
				: resolutions)
		{
			resolutionsByCellId.put(
					resolution.getCellId(),
					resolution
			);
		}

		Set<Integer> managedItemIds =
				managedExactItemIds(
						binding,
						state
				);

		scheduleOrdinaryMembershipCleanup(
				binding,
				managedItemIds
		);

		boolean layoutChanged =
				scrubManagedItemsOutsideSlots(
						coreLayout,
						binding,
						managedItemIds
				);

		boolean bindingChanged = false;

		List<BankTagSlotBinding> updatedSlots =
				new ArrayList<>();

		Set<Integer> dynamicItemIds =
				new HashSet<>();

		for (BankTagSlotBinding slot
				: binding.getSlots())
		{
			int index =
					slot.getPlacement().getIndex();

			if (index >= coreLayout.size())
			{
				log.debug(
						"Priority slot {} is outside "
								+ "Bank Tags layout '{}'",
						slot.getPlacement().getCellId(),
						binding.getBankTagName()
				);

				updatedSlots.add(slot);

				continue;
			}

			int currentItemId =
					coreLayout.getItemAtPos(index);

			SlotResolution resolution =
					resolutionsByCellId.get(
							slot.getPlacement().getCellId()
					);

			if (resolution == null
					|| resolution.getState()
					== SlotResolution.State.UNRESOLVED)
			{
				if (currentItemId > 0)
				{
					dynamicItemIds.add(
							currentItemId
					);
				}

				updatedSlots.add(slot);

				continue;
			}

			if (!slot.matchesLayoutItem(
					currentItemId))
			{
				if (currentItemId > 0)
				{
					dynamicItemIds.add(
							currentItemId
					);
				}

				log.debug(
						"Priority slot {} was changed "
								+ "outside Priority Slots; "
								+ "expected {}, found {}",
						slot.getPlacement().getCellId(),
						slot.getLastProjectedExactItemId(),
						currentItemId
				);

				updatedSlots.add(slot);

				continue;
			}

			int resolvedItemId =
					resolution.getExactItemId();

			if (resolution.isOwned())
			{
				dynamicItemIds.add(
						resolvedItemId
				);
			}

			if (currentItemId != resolvedItemId)
			{
				coreLayout.setItemAtPos(
						resolvedItemId,
						index
				);

				layoutChanged = true;
			}

			if (slot.getLastProjectedExactItemId()
					!= resolvedItemId)
			{
				updatedSlots.add(
						slot.withLastProjectedExactItemId(
								resolvedItemId
						)
				);

				bindingChanged = true;
			}
			else
			{
				updatedSlots.add(slot);
			}
		}

		updateDynamicTag(
				binding.getBankTagName(),
				dynamicItemIds
		);

		if (layoutChanged)
		{
			layoutManager.saveLayout(coreLayout);
		}

		BankTagBinding updatedBinding =
				bindingChanged
						? binding.withSlots(updatedSlots)
						: binding;

		return new ProjectionResult(
				updatedBinding,
				bindingChanged,
				isActiveTag(
						binding.getBankTagName()
				)
		);
	}

	private Layout coreLayoutFor(
			String bankTagName)
	{
		if (isActiveTag(bankTagName))
		{
			Layout activeLayout =
					bankTagsService.getActiveLayout();

			if (activeLayout != null)
			{
				return activeLayout;
			}
		}

		return layoutManager.loadLayout(
				bankTagName
		);
	}

	private void updateDynamicTag(
			String bankTagName,
			Set<Integer> itemIds)
	{
		String standardizedTagName =
				Text.standardize(bankTagName);

		PriorityBankTag priorityBankTag =
				registeredTags.get(
						standardizedTagName
				);

		if (priorityBankTag == null)
		{
			priorityBankTag =
					new PriorityBankTag();

			registeredTags.put(
					standardizedTagName,
					priorityBankTag
			);

			tagManager.registerTag(
					standardizedTagName,
					priorityBankTag
			);
		}

		priorityBankTag.replaceItems(itemIds);
	}

	private void scheduleOrdinaryMembershipCleanup(
			BankTagBinding binding,
			Set<Integer> managedItemIds)
	{
		Set<Integer> immutableItemIds =
				Set.copyOf(managedItemIds);

		Set<Integer> previousItemIds =
				cleanedManagedItemIdsByBindingId.put(
						binding.getId(),
						immutableItemIds
				);

		if (immutableItemIds.equals(
				previousItemIds))
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			String bankTagName =
					binding.getBankTagName();

			for (Integer itemId
					: immutableItemIds)
			{
				tagManager.removeTag(
						itemId,
						bankTagName
				);
			}

			Layout layout =
					coreLayoutFor(bankTagName);

			if (layout != null
					&& scrubManagedItemsOutsideSlots(
					layout,
					binding,
					immutableItemIds
			))
			{
				layoutManager.saveLayout(layout);
			}

			if (isActiveTag(bankTagName))
			{
				/*
				 * Reopen the tab so Bank Tags captures
				 * the custom PriorityBankTag that was
				 * registered by projectBinding().
				 */
				bankTagsService.openBankTag(
						bankTagName,
						BankTagsService
								.OPTION_ALLOW_MODIFICATIONS
				);

				bankSearch.layoutBank();
			}
		});
	}

	private static Set<Integer> managedExactItemIds(
			BankTagBinding binding,
			PriorityState state)
	{
		Set<Integer> result =
				new HashSet<>();

		Map<String, PriorityDefinition>
				definitionsById =
				state.definitionsById();

		for (BankTagSlotBinding slot
				: binding.getSlots())
		{
			result.add(
					slot.getFallbackExactItemId()
			);

			PriorityDefinition definition =
					definitionsById.get(
							slot.getPlacement()
									.getDefinitionId()
					);

			if (definition == null)
			{
				continue;
			}

			for (PriorityTier tier
					: definition.getTiers())
			{
				result.addAll(
						tier.getExactItemIds()
				);
			}
		}

		return Set.copyOf(result);
	}

	private static boolean
	scrubManagedItemsOutsideSlots(
			Layout layout,
			BankTagBinding binding,
			Set<Integer> managedItemIds)
	{
		Set<Integer> reservedIndices =
				new HashSet<>();

		for (BankTagSlotBinding slot
				: binding.getSlots())
		{
			reservedIndices.add(
					slot.getPlacement().getIndex()
			);
		}

		boolean changed = false;

		for (int index = 0;
		     index < layout.size();
		     index++)
		{
			int itemId =
					layout.getItemAtPos(index);

			if (itemId > 0
					&& !reservedIndices.contains(index)
					&& managedItemIds.contains(itemId))
			{
				layout.removeItemAtPos(index);

				changed = true;
			}
		}

		return changed;
	}

	private void removeUnusedRuntimeState(
			PriorityState state)
	{
		Set<String> requiredTagNames =
				new HashSet<>();

		Set<String> requiredBindingIds =
				new HashSet<>();

		for (BankTagBinding binding
				: state.getBindings())
		{
			requiredTagNames.add(
					Text.standardize(
							binding.getBankTagName()
					)
			);

			requiredBindingIds.add(
					binding.getId()
			);
		}

		List<String> obsoleteTagNames =
				new ArrayList<>();

		for (String registeredTagName
				: registeredTags.keySet())
		{
			if (!requiredTagNames.contains(
					registeredTagName))
			{
				obsoleteTagNames.add(
						registeredTagName
				);
			}
		}

		for (String obsoleteTagName
				: obsoleteTagNames)
		{
			tagManager.unregisterTag(
					obsoleteTagName
			);

			registeredTags.remove(
					obsoleteTagName
			);
		}

		cleanedManagedItemIdsByBindingId
				.keySet()
				.removeIf(
						bindingId ->
								!requiredBindingIds.contains(
										bindingId
								)
				);
	}

	private boolean isActiveTag(
			String bankTagName)
	{
		String activeTag =
				bankTagsService.getActiveTag();

		return activeTag != null
				&& Text.standardize(activeTag).equals(
				Text.standardize(bankTagName)
		);
	}

	private static final class ProjectionResult
	{
		private final BankTagBinding binding;
		private final boolean stateChanged;
		private final boolean activeRefreshRequired;

		private ProjectionResult(
				BankTagBinding binding,
				boolean stateChanged,
				boolean activeRefreshRequired)
		{
			this.binding = binding;
			this.stateChanged = stateChanged;

			this.activeRefreshRequired =
					activeRefreshRequired;
		}

		private static ProjectionResult unchanged(
				BankTagBinding binding)
		{
			return new ProjectionResult(
					binding,
					false,
					false
			);
		}
	}
}
