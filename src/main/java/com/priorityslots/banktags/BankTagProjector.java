package com.priorityslots.banktags;

import com.priorityslots.domain.BankSnapshot;
import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.PriorityResolver;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.SlotResolution;
import com.priorityslots.persistence.PriorityStateStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
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
	private final BankTagLayoutReader layoutReader;
	private final LayoutManager layoutManager;
	private final TagManager tagManager;
	private final BankTagsService bankTagsService;
	private final BankSearch bankSearch;
	private final PriorityStateStore stateStore;

	private final PriorityResolver resolver =
			new PriorityResolver();

	private final Map<String, PriorityBankTag>
			registeredTags = new HashMap<>();

	@Inject
	public BankTagProjector(
			BankTagLayoutReader layoutReader,
			LayoutManager layoutManager,
			TagManager tagManager,
			BankTagsService bankTagsService,
			BankSearch bankSearch,
			PriorityStateStore stateStore)
	{
		this.layoutReader = Objects.requireNonNull(
				layoutReader,
				"layoutReader"
		);
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

		removeUnusedTags(state);

		List<BankTagBinding> updatedBindings =
				new ArrayList<>();

		boolean stateChanged = false;
		boolean activeLayoutChanged = false;

		for (BankTagBinding binding
				: state.getBindings())
		{
			ProjectionResult result = projectBinding(
					binding,
					state,
					bankSnapshot
			);

			updatedBindings.add(result.binding);

			stateChanged |= result.stateChanged;
			activeLayoutChanged |=
					result.activeLayoutChanged;
		}

		PriorityState updatedState =
				state.withBindings(updatedBindings);

		if (stateChanged)
		{
			stateStore.save(updatedState);
		}

		if (activeLayoutChanged)
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
	}

	private ProjectionResult projectBinding(
			BankTagBinding binding,
			PriorityState state,
			BankSnapshot bankSnapshot)
	{
		Optional<BankTagLayoutSnapshot> loadedLayout;

		try
		{
			loadedLayout = layoutReader.load(
					binding.getBankTagName()
			);
		}
		catch (BankTagLayoutFormatException exception)
		{
			log.warn(
					"Unable to project Priority Slots "
							+ "binding for Bank Tag '{}'",
					binding.getBankTagName(),
					exception
			);

			updateDynamicTag(
					binding.getBankTagName(),
					Set.of()
			);

			return ProjectionResult.unchanged(binding);
		}

		if (!loadedLayout.isPresent())
		{
			log.debug(
					"Bank Tags layout '{}' does not exist",
					binding.getBankTagName()
			);

			updateDynamicTag(
					binding.getBankTagName(),
					Set.of()
			);

			return ProjectionResult.unchanged(binding);
		}

		BankTagLayoutSnapshot layout =
				loadedLayout.get();

		List<Integer> projectedLayout =
				new ArrayList<>(layout.getItemIds());

		List<SlotResolution> resolutions =
				resolver.resolveBinding(
						binding,
						state.definitionsById(),
						bankSnapshot
				);

		Map<String, SlotResolution>
				resolutionsByCellId = new HashMap<>();

		for (SlotResolution resolution : resolutions)
		{
			resolutionsByCellId.put(
					resolution.getCellId(),
					resolution
			);
		}

		List<BankTagSlotBinding> updatedSlots =
				new ArrayList<>();

		Set<Integer> dynamicItemIds =
				new HashSet<>();

		boolean layoutChanged = false;
		boolean bindingChanged = false;

		for (BankTagSlotBinding slot
				: binding.getSlots())
		{
			SlotResolution resolution =
					resolutionsByCellId.get(
							slot.getPlacement().getCellId()
					);

			if (resolution == null
					|| resolution.getState()
					== SlotResolution.State.UNRESOLVED)
			{
				updatedSlots.add(slot);
				continue;
			}

			int index =
					slot.getPlacement().getIndex();

			OptionalInt currentItem =
					layout.itemAt(index);

			if (!currentItem.isPresent())
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

			if (!slot.matchesLayoutItem(
					currentItem.getAsInt()))
			{
				log.debug(
						"Priority slot {} was changed "
								+ "outside Priority Slots; "
								+ "expected {}, found {}",
						slot.getPlacement().getCellId(),
						slot.getLastProjectedExactItemId(),
						currentItem.getAsInt()
				);

				updatedSlots.add(slot);
				continue;
			}

			int resolvedItemId =
					resolution.getExactItemId();

			dynamicItemIds.add(resolvedItemId);

			if (currentItem.getAsInt()
					!= resolvedItemId)
			{
				projectedLayout.set(
						index,
						resolvedItemId
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
			layoutManager.saveLayout(
					new Layout(
							binding.getBankTagName(),
							toArray(projectedLayout)
					)
			);
		}

		BankTagBinding updatedBinding =
				bindingChanged
						? binding.withSlots(updatedSlots)
						: binding;

		boolean activeLayoutChanged =
				layoutChanged
						&& isActiveTag(
						binding.getBankTagName()
				);

		return new ProjectionResult(
				updatedBinding,
				bindingChanged,
				activeLayoutChanged
		);
	}

	private void updateDynamicTag(
			String bankTagName,
			Set<Integer> itemIds)
	{
		PriorityBankTag priorityBankTag =
				registeredTags.get(bankTagName);

		if (priorityBankTag == null)
		{
			priorityBankTag =
					new PriorityBankTag();

			registeredTags.put(
					bankTagName,
					priorityBankTag
			);

			tagManager.registerTag(
					bankTagName,
					priorityBankTag
			);
		}

		priorityBankTag.replaceItems(itemIds);
	}

	private void removeUnusedTags(PriorityState state)
	{
		Set<String> requiredNames =
				new HashSet<>();

		for (BankTagBinding binding
				: state.getBindings())
		{
			requiredNames.add(
					binding.getBankTagName()
			);
		}

		List<String> obsoleteNames =
				new ArrayList<>();

		for (String registeredName
				: registeredTags.keySet())
		{
			if (!requiredNames.contains(registeredName))
			{
				obsoleteNames.add(registeredName);
			}
		}

		for (String obsoleteName : obsoleteNames)
		{
			tagManager.unregisterTag(obsoleteName);
			registeredTags.remove(obsoleteName);
		}
	}

	private boolean isActiveTag(String bankTagName)
	{
		String activeTag =
				bankTagsService.getActiveTag();

		return activeTag != null
				&& Text.standardize(activeTag).equals(
				Text.standardize(bankTagName)
		);
	}

	private static int[] toArray(
			List<Integer> itemIds)
	{
		int[] result = new int[itemIds.size()];

		for (int index = 0;
		     index < itemIds.size();
		     index++)
		{
			result[index] = itemIds.get(index);
		}

		return result;
	}

	private static final class ProjectionResult
	{
		private final BankTagBinding binding;
		private final boolean stateChanged;
		private final boolean activeLayoutChanged;

		private ProjectionResult(
				BankTagBinding binding,
				boolean stateChanged,
				boolean activeLayoutChanged)
		{
			this.binding = binding;
			this.stateChanged = stateChanged;
			this.activeLayoutChanged =
					activeLayoutChanged;
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
