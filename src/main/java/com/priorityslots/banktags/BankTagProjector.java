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

	private final BankTagPlacementReconciler
		placementReconciler =
		new BankTagPlacementReconciler();

	private final Map<String, PriorityBankTag>
		registeredTags = new HashMap<>();

	private final DeferredCleanupTracker cleanupTracker =
		new DeferredCleanupTracker();

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

		List<ProjectionPlan> projectionPlans =
			new ArrayList<>();

		List<BankTagBinding> reconciledBindings =
			new ArrayList<>();

		Set<String> unsafeBindingIds =
			new HashSet<>();

		boolean stateChanged = false;

		for (BankTagBinding binding
			: state.getBindings())
		{
			Layout layout = coreLayoutFor(
				binding.getBankTagName()
			);

			if (layout == null)
			{
				reconciledBindings.add(binding);
				projectionPlans.add(
					ProjectionPlan.safe(
						binding,
						null
					)
				);
				continue;
			}

			BankTagPlacementReconciler.Result
				reconciliation =
				placementReconciler.reconcile(
					binding,
					layout::getItemAtPos,
					layout.size()
				);

			BankTagBinding reconciledBinding =
				reconciliation.getBinding();

			reconciledBindings.add(
				reconciledBinding
			);

			stateChanged |=
				reconciledBinding != binding;

			if (reconciledBinding != binding)
			{
				logMovedSlots(binding, reconciledBinding);
			}

			if (!reconciliation.isProjectionSafe())
			{
				unsafeBindingIds.add(binding.getId());

				projectionPlans.add(
					ProjectionPlan.unsafe(
						reconciledBinding,
						layout
					)
				);

				log.debug(
					"Priority binding {} could not be "
						+ "reconciled safely before projection",
					binding.getId()
				);

				continue;
			}

			projectionPlans.add(
				ProjectionPlan.safe(
					reconciledBinding,
					layout
				)
			);
		}

		PriorityState reconciledState =
			stateChanged
				? state.withBindings(
					reconciledBindings
				)
				: state;

		refreshCleanupState(
			reconciledState,
			unsafeBindingIds
		);

		removeUnusedRuntimeState(reconciledState);

		List<BankTagBinding> updatedBindings =
			new ArrayList<>();

		boolean activeRefreshRequired = false;

		for (ProjectionPlan plan : projectionPlans)
		{
			if (!plan.projectionSafe)
			{
				updatedBindings.add(plan.binding);
				continue;
			}

			ProjectionResult result = projectBinding(
				plan.binding,
				reconciledState,
				bankSnapshot,
				plan.layout
			);

			updatedBindings.add(result.binding);

			stateChanged |= result.stateChanged;

			activeRefreshRequired |=
				result.activeRefreshRequired;
		}

		PriorityState updatedState =
			stateChanged
				? reconciledState.withBindings(
					updatedBindings
				)
				: reconciledState;

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

	public PriorityState reconcileActiveLayout(
		PriorityState state)
	{
		Objects.requireNonNull(state, "state");

		String activeTag =
			bankTagsService.getActiveTag();

		Layout activeLayout =
			bankTagsService.getActiveLayout();

		if (activeTag == null
			|| activeLayout == null)
		{
			return state;
		}

		List<BankTagBinding> updatedBindings =
			new ArrayList<>();

		Set<String> unsafeBindingIds =
			new HashSet<>();

		boolean stateChanged = false;

		for (BankTagBinding binding
			: state.getBindings())
		{
			if (!sameTag(
				binding.getBankTagName(),
				activeTag))
			{
				updatedBindings.add(binding);
				continue;
			}

			BankTagPlacementReconciler.Result
				reconciliation =
				placementReconciler.reconcile(
					binding,
					activeLayout::getItemAtPos,
					activeLayout.size()
				);

			BankTagBinding updatedBinding =
				reconciliation.getBinding();

			updatedBindings.add(updatedBinding);

			if (!reconciliation.isProjectionSafe())
			{
				unsafeBindingIds.add(binding.getId());
			}

			stateChanged |=
				updatedBinding != binding;

			if (updatedBinding != binding)
			{
				logMovedSlots(binding, updatedBinding);
			}
		}

		PriorityState updatedState =
			stateChanged
				? state.withBindings(updatedBindings)
				: state;

		refreshCleanupState(
			updatedState,
			unsafeBindingIds
		);

		if (stateChanged)
		{
			stateStore.save(updatedState);
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

		cleanupTracker.reset();
	}

	private ProjectionResult projectBinding(
		BankTagBinding binding,
		PriorityState state,
		BankSnapshot bankSnapshot,
		Layout coreLayout)
	{
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
			BankTagManagedItems.collect(
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
		DeferredCleanupTracker.Signature signature =
			cleanupSignature(
				binding,
				managedItemIds
			);

		Optional<DeferredCleanupTracker.Token> token =
			cleanupTracker.begin(signature);

		if (!token.isPresent())
		{
			return;
		}

		Set<Integer> immutableItemIds =
			signature.getManagedItemIds();

		clientThread.invokeLater(() ->
			executeOrdinaryMembershipCleanup(
				binding,
				immutableItemIds,
				token.get()
			)
		);
	}

	private void executeOrdinaryMembershipCleanup(
		BankTagBinding binding,
		Set<Integer> managedItemIds,
		DeferredCleanupTracker.Token token)
	{
		if (!cleanupTracker.isCurrent(token))
		{
			return;
		}

		try
		{
			String bankTagName =
				binding.getBankTagName();

			for (Integer itemId : managedItemIds)
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
					managedItemIds
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

			cleanupTracker.complete(token);
		}
		catch (RuntimeException exception)
		{
			cleanupTracker.fail(token);

			log.warn(
				"Unable to clean ordinary Bank Tags "
					+ "membership for '{}'",
				binding.getBankTagName(),
				exception
			);
		}
	}

	private void refreshCleanupState(
		PriorityState state,
		Set<String> excludedBindingIds)
	{
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(
			excludedBindingIds,
			"excludedBindingIds"
		);

		List<DeferredCleanupTracker.Signature> signatures =
			new ArrayList<>();

		for (BankTagBinding binding
			: state.getBindings())
		{
			if (excludedBindingIds.contains(
				binding.getId()))
			{
				continue;
			}

			signatures.add(
				cleanupSignature(
					binding,
					BankTagManagedItems.collect(
						binding,
						state
					)
				)
			);
		}

		cleanupTracker.replaceCurrent(signatures);
	}

	private static void logMovedSlots(
		BankTagBinding previous,
		BankTagBinding updated)
	{
		Map<String, BankTagSlotBinding> previousByCellId =
			new HashMap<>();

		for (BankTagSlotBinding slot : previous.getSlots())
		{
			previousByCellId.put(
				slot.getPlacement().getCellId(),
				slot
			);
		}

		for (BankTagSlotBinding slot : updated.getSlots())
		{
			BankTagSlotBinding oldSlot =
				previousByCellId.get(
					slot.getPlacement().getCellId()
				);

			if (oldSlot == null
				|| oldSlot.getPlacement().getIndex()
				== slot.getPlacement().getIndex())
			{
				continue;
			}

			log.debug(
				"Priority slot {} followed item {} "
					+ "from layout index {} to {}",
				slot.getPlacement().getCellId(),
				slot.getLastProjectedExactItemId(),
				oldSlot.getPlacement().getIndex(),
				slot.getPlacement().getIndex()
			);
		}
	}

	private static DeferredCleanupTracker.Signature
	cleanupSignature(
		BankTagBinding binding,
		Set<Integer> managedItemIds)
	{
		return new DeferredCleanupTracker.Signature(
			binding.getId(),
			Text.standardize(
				binding.getBankTagName()
			),
			managedItemIds,
			BankTagManagedItems.reservedIndices(
				binding
			)
		);
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

		for (BankTagBinding binding
			: state.getBindings())
		{
			requiredTagNames.add(
				Text.standardize(
					binding.getBankTagName()
				)
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
	}

	private boolean isActiveTag(
		String bankTagName)
	{
		String activeTag =
			bankTagsService.getActiveTag();

		return activeTag != null
			&& sameTag(
				activeTag,
				bankTagName
			);
	}

	private static boolean sameTag(
		String first,
		String second)
	{
		return Text.standardize(first).equals(
			Text.standardize(second)
		);
	}


	private static final class ProjectionPlan
	{
		private final BankTagBinding binding;
		private final Layout layout;
		private final boolean projectionSafe;

		private ProjectionPlan(
			BankTagBinding binding,
			Layout layout,
			boolean projectionSafe)
		{
			this.binding = Objects.requireNonNull(
				binding,
				"binding"
			);
			this.layout = layout;
			this.projectionSafe = projectionSafe;
		}

		private static ProjectionPlan safe(
			BankTagBinding binding,
			Layout layout)
		{
			return new ProjectionPlan(
				binding,
				layout,
				true
			);
		}

		private static ProjectionPlan unsafe(
			BankTagBinding binding,
			Layout layout)
		{
			return new ProjectionPlan(
				binding,
				layout,
				false
			);
		}
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
