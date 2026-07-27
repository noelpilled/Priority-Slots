package com.priorityslots.banktags;

import com.priorityslots.domain.BankSnapshot;
import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.lifecycle.LifecycleGeneration;
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
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
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

	private final BankTagPlacementReconciler
		placementReconciler =
		new BankTagPlacementReconciler();

	private final BankTagProjectionPlanner projectionPlanner =
		new BankTagProjectionPlanner();

	private final LifecycleGeneration lifecycle =
		new LifecycleGeneration();

	/*
	 * registeredTags and cleanupTracker are client-thread confined.
	 * Lifecycle methods only update the thread-safe lifecycle token before
	 * scheduling work.
	 */
	private final Map<String, PriorityBankTag>
		registeredTags = new HashMap<>();

	private final DeferredCleanupTracker cleanupTracker =
		new DeferredCleanupTracker();

	private final BankTagsMembershipChangeDetector
		membershipChangeDetector;

	private long appliedGeneration = -1L;

	@Inject
	public BankTagProjector(
		LayoutManager layoutManager,
		TagManager tagManager,
		BankTagsService bankTagsService,
		BankSearch bankSearch,
		PriorityStateStore stateStore,
		ClientThread clientThread,
		ItemManager itemManager)
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

		ItemManager requiredItemManager = Objects.requireNonNull(
			itemManager,
			"itemManager"
		);

		this.membershipChangeDetector =
			new BankTagsMembershipChangeDetector(
				requiredItemManager::canonicalize,
				ItemVariationMapping::map
			);
	}

	public void activate()
	{
		long generation = lifecycle.activate();

		clientThread.invokeLater(() ->
		{
			if (lifecycle.isCurrent(generation, true))
			{
				ensureRuntimeGeneration();
			}
		});
	}

	public void deactivate()
	{
		long generation = lifecycle.deactivate();

		clientThread.invokeLater(() ->
		{
			if (lifecycle.isCurrent(generation, false))
			{
				clearRuntimeState();
				appliedGeneration = generation;
			}
		});
	}

	/**
	 * Clears profile-scoped runtime state. The caller must dispatch this
	 * method through ClientThread.
	 */
	public void resetRuntimeState()
	{
		long generation = lifecycle.advanceGeneration();
		clearRuntimeState();
		appliedGeneration = generation;
	}

	/**
	 * Invalidates completed cleanup when ordinary Bank Tags membership is
	 * added back to a managed candidate. The caller must dispatch this
	 * method through ClientThread.
	 */
	public boolean invalidateOrdinaryMembershipCleanup(
		PriorityState state,
		String configKey,
		String newValue)
	{
		Objects.requireNonNull(state, "state");

		if (!lifecycle.isActive())
		{
			return false;
		}

		ensureRuntimeGeneration();

		Set<String> affectedBindingIds =
			membershipChangeDetector.affectedBindingIds(
				state,
				configKey,
				newValue
			);

		cleanupTracker.invalidateCompleted(affectedBindingIds);
		return !affectedBindingIds.isEmpty();
	}

	public PriorityState synchronize(
		PriorityState state,
		BankSnapshot bankSnapshot)
	{
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(bankSnapshot, "bankSnapshot");

		if (!lifecycle.isActive())
		{
			return state;
		}

		ensureRuntimeGeneration();

		List<ProjectionPlan> projectionPlans =
			new ArrayList<>();

		List<BankTagBinding> reconciledBindings =
			new ArrayList<>();

		Set<String> unsafeBindingIds =
			new HashSet<>();

		boolean stateChanged = false;

		for (BankTagBinding binding : state.getBindings())
		{
			Layout layout = coreLayoutFor(
				binding.getBankTagName()
			);

			if (layout == null)
			{
				reconciledBindings.add(binding);
				projectionPlans.add(
					ProjectionPlan.safe(binding, null)
				);
				continue;
			}

			BankTagPlacementReconciler.Result reconciliation =
				placementReconciler.reconcile(
					binding,
					layout::getItemAtPos,
					layout.size()
				);

			BankTagBinding reconciledBinding =
				reconciliation.getBinding();

			reconciledBindings.add(reconciledBinding);
			stateChanged |= reconciledBinding != binding;

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

		PriorityState reconciledState = stateChanged
			? state.withBindings(reconciledBindings)
			: state;

		refreshCleanupState(
			reconciledState,
			unsafeBindingIds
		);

		boolean activeRefreshRequired =
			removeUnusedRuntimeState(reconciledState);

		List<BankTagBinding> updatedBindings =
			new ArrayList<>();

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
			activeRefreshRequired |= result.activeRefreshRequired;
		}

		PriorityState updatedState = stateChanged
			? reconciledState.withBindings(updatedBindings)
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

		if (!lifecycle.isActive())
		{
			return state;
		}

		ensureRuntimeGeneration();

		String activeTag = bankTagsService.getActiveTag();
		Layout activeLayout = bankTagsService.getActiveLayout();

		if (activeTag == null || activeLayout == null)
		{
			return state;
		}

		List<BankTagBinding> updatedBindings =
			new ArrayList<>();

		Set<String> unsafeBindingIds =
			new HashSet<>();

		boolean stateChanged = false;

		for (BankTagBinding binding : state.getBindings())
		{
			if (!sameTag(binding.getBankTagName(), activeTag))
			{
				updatedBindings.add(binding);
				continue;
			}

			BankTagPlacementReconciler.Result reconciliation =
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

			stateChanged |= updatedBinding != binding;

			if (updatedBinding != binding)
			{
				logMovedSlots(binding, updatedBinding);
			}
		}

		PriorityState updatedState = stateChanged
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

			boolean dynamicTagChanged = updateDynamicTag(
				binding.getBankTagName(),
				Set.of()
			);

			return new ProjectionResult(
				binding,
				false,
				ProjectionRefreshPolicy.shouldRefresh(
					isActiveTag(binding.getBankTagName()),
					false,
					dynamicTagChanged
				)
			);
		}

		BankTagProjectionPlanner.Plan plan = projectionPlanner.plan(
			binding,
			state,
			bankSnapshot,
			layoutItems(coreLayout)
		);

		scheduleOrdinaryMembershipCleanup(
			binding,
			plan.getManagedItemIds()
		);

		logProjectionDiagnostics(binding, plan);

		if (plan.isLayoutChanged())
		{
			applyLayoutItems(
				coreLayout,
				plan.getProjectedLayoutItems()
			);
			layoutManager.saveLayout(coreLayout);
		}

		boolean dynamicTagChanged = updateDynamicTag(
			binding.getBankTagName(),
			plan.getDynamicItemIds()
		);

		return new ProjectionResult(
			plan.getBinding(),
			plan.isBindingChanged(),
			ProjectionRefreshPolicy.shouldRefresh(
				isActiveTag(binding.getBankTagName()),
				plan.isLayoutChanged(),
				dynamicTagChanged
			)
		);
	}

	private static void logProjectionDiagnostics(
		BankTagBinding binding,
		BankTagProjectionPlanner.Plan plan)
	{
		for (String cellId : plan.getOutsideLayoutCellIds())
		{
			log.debug(
				"Priority slot {} is outside Bank Tags layout '{}'",
				cellId,
				binding.getBankTagName()
			);
		}

		for (Map.Entry<String, BankTagProjectionPlanner.Conflict>
			entry : plan.getConflictsByCellId().entrySet())
		{
			BankTagProjectionPlanner.Conflict conflict =
				entry.getValue();

			log.debug(
				"Priority slot {} was changed outside Priority Slots; "
					+ "expected {}, found {}",
				entry.getKey(),
				conflict.getExpectedItemId(),
				conflict.getCurrentItemId()
			);
		}
	}

	private Layout coreLayoutFor(String bankTagName)
	{
		if (isActiveTag(bankTagName))
		{
			Layout activeLayout = bankTagsService.getActiveLayout();

			if (activeLayout != null)
			{
				return activeLayout;
			}
		}

		return layoutManager.loadLayout(bankTagName);
	}

	private boolean updateDynamicTag(
		String bankTagName,
		Set<Integer> itemIds)
	{
		String standardizedTagName =
			Text.standardize(bankTagName);

		PriorityBankTag priorityBankTag =
			registeredTags.get(standardizedTagName);

		boolean registered = false;

		if (priorityBankTag == null)
		{
			priorityBankTag = new PriorityBankTag();
			registeredTags.put(standardizedTagName, priorityBankTag);
			tagManager.registerTag(
				standardizedTagName,
				priorityBankTag
			);
			registered = true;
		}

		return priorityBankTag.replaceItems(itemIds) || registered;
	}

	private void scheduleOrdinaryMembershipCleanup(
		BankTagBinding binding,
		Set<Integer> managedItemIds)
	{
		DeferredCleanupTracker.Signature signature =
			cleanupSignature(binding, managedItemIds);

		Optional<DeferredCleanupTracker.Token> token =
			cleanupTracker.begin(signature);

		if (!token.isPresent())
		{
			return;
		}

		Set<Integer> immutableItemIds =
			signature.getManagedItemIds();

		long generation = lifecycle.currentGeneration();

		clientThread.invokeLater(() ->
		{
			if (!lifecycle.isCurrent(generation, true))
			{
				return;
			}

			executeOrdinaryMembershipCleanup(
				binding,
				immutableItemIds,
				token.get()
			);
		});
	}

	private void executeOrdinaryMembershipCleanup(
		BankTagBinding binding,
		Set<Integer> managedItemIds,
		DeferredCleanupTracker.Token token)
	{
		if (!lifecycle.isActive()
			|| !cleanupTracker.isCurrent(token))
		{
			return;
		}

		try
		{
			String bankTagName = binding.getBankTagName();

			for (Integer itemId : managedItemIds)
			{
				tagManager.removeTag(itemId, bankTagName);
			}

			Layout layout = coreLayoutFor(bankTagName);

			if (layout != null)
			{
				List<Integer> layoutItems = layoutItems(layout);
				List<Integer> scrubbedItems = projectionPlanner
					.scrubManagedItemsOutsideSlots(
						layoutItems,
						binding,
						managedItemIds
					);

				if (!layoutItems.equals(scrubbedItems))
				{
					applyLayoutItems(layout, scrubbedItems);
					layoutManager.saveLayout(layout);
				}
			}

			if (isActiveTag(bankTagName))
			{
				bankTagsService.openBankTag(
					bankTagName,
					BankTagsService.OPTION_ALLOW_MODIFICATIONS
				);
				bankSearch.layoutBank();
			}

			cleanupTracker.complete(token);
		}
		catch (RuntimeException exception)
		{
			cleanupTracker.fail(token);
			log.warn(
				"Unable to clean ordinary Bank Tags membership for '{}'",
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

		for (BankTagBinding binding : state.getBindings())
		{
			if (excludedBindingIds.contains(binding.getId()))
			{
				continue;
			}

			signatures.add(
				cleanupSignature(
					binding,
					BankTagManagedItems.collect(binding, state)
				)
			);
		}

		cleanupTracker.replaceCurrent(signatures);
	}

	private static DeferredCleanupTracker.Signature cleanupSignature(
		BankTagBinding binding,
		Set<Integer> managedItemIds)
	{
		return new DeferredCleanupTracker.Signature(
			binding.getId(),
			Text.standardize(binding.getBankTagName()),
			managedItemIds,
			BankTagManagedItems.reservedIndices(binding)
		);
	}

	private boolean removeUnusedRuntimeState(PriorityState state)
	{
		Set<String> requiredTagNames = new HashSet<>();

		for (BankTagBinding binding : state.getBindings())
		{
			requiredTagNames.add(
				Text.standardize(binding.getBankTagName())
			);
		}

		List<String> obsoleteTagNames = new ArrayList<>();

		for (String registeredTagName : registeredTags.keySet())
		{
			if (!requiredTagNames.contains(registeredTagName))
			{
				obsoleteTagNames.add(registeredTagName);
			}
		}

		boolean activeTagChanged = false;

		for (String obsoleteTagName : obsoleteTagNames)
		{
			activeTagChanged |= isActiveTag(obsoleteTagName);
			tagManager.unregisterTag(obsoleteTagName);
			registeredTags.remove(obsoleteTagName);
		}

		return activeTagChanged;
	}

	private void ensureRuntimeGeneration()
	{
		long generation = lifecycle.currentGeneration();

		if (appliedGeneration == generation)
		{
			return;
		}

		clearRuntimeState();
		appliedGeneration = generation;
	}

	private void clearRuntimeState()
	{
		for (String bankTagName
			: new ArrayList<>(registeredTags.keySet()))
		{
			tagManager.unregisterTag(bankTagName);
		}

		registeredTags.clear();
		cleanupTracker.reset();
	}

	private boolean isActiveTag(String bankTagName)
	{
		String activeTag = bankTagsService.getActiveTag();

		return activeTag != null && sameTag(activeTag, bankTagName);
	}

	private static boolean sameTag(String first, String second)
	{
		return Text.standardize(first).equals(
			Text.standardize(second)
		);
	}

	private static List<Integer> layoutItems(Layout layout)
	{
		int[] values = layout.getLayout();
		List<Integer> result = new ArrayList<>(values.length);

		for (int value : values)
		{
			result.add(value);
		}

		return List.copyOf(result);
	}

	private static void applyLayoutItems(
		Layout layout,
		List<Integer> itemIds)
	{
		layout.resize(itemIds.size());

		for (int index = 0; index < itemIds.size(); index++)
		{
			layout.setItemAtPos(itemIds.get(index), index);
		}
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
			BankTagSlotBinding oldSlot = previousByCellId.get(
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
			this.binding = Objects.requireNonNull(binding, "binding");
			this.layout = layout;
			this.projectionSafe = projectionSafe;
		}

		private static ProjectionPlan safe(
			BankTagBinding binding,
			Layout layout)
		{
			return new ProjectionPlan(binding, layout, true);
		}

		private static ProjectionPlan unsafe(
			BankTagBinding binding,
			Layout layout)
		{
			return new ProjectionPlan(binding, layout, false);
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
			this.binding = Objects.requireNonNull(binding, "binding");
			this.stateChanged = stateChanged;
			this.activeRefreshRequired = activeRefreshRequired;
		}
	}
}
