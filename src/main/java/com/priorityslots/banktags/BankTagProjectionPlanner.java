package com.priorityslots.banktags;

import com.priorityslots.domain.BankSnapshot;
import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.PriorityResolver;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.SlotResolution;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class BankTagProjectionPlanner
{
	private static final int EMPTY_LAYOUT_ITEM = -1;

	private final PriorityResolver resolver =
			new PriorityResolver();

	Plan plan(
			BankTagBinding binding,
			PriorityState state,
			BankSnapshot bankSnapshot,
			List<Integer> layoutItems)
	{
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(
				bankSnapshot,
				"bankSnapshot"
		);
		Objects.requireNonNull(
				layoutItems,
				"layoutItems"
		);

		List<Integer> originalLayoutItems =
				copyLayoutItems(layoutItems);

		Set<Integer> managedItemIds =
				BankTagManagedItems.collect(
						binding,
						state
				);

		List<Integer> projectedLayoutItems =
				new ArrayList<>(
						scrubManagedItemsOutsideSlots(
								originalLayoutItems,
								binding,
								managedItemIds
						)
				);

		Map<String, SlotResolution>
				resolutionsByCellId =
				resolutionsByCellId(
						binding,
						state,
						bankSnapshot
				);

		List<BankTagSlotBinding> updatedSlots =
				new ArrayList<>();

		Set<Integer> dynamicItemIds =
				new HashSet<>();

		Set<String> outsideLayoutCellIds =
				new HashSet<>();

		Map<String, Conflict> conflictsByCellId =
				new LinkedHashMap<>();

		boolean bindingChanged = false;

		for (BankTagSlotBinding slot
				: binding.getSlots())
		{
			int index =
					slot.getPlacement().getIndex();

			if (index >= projectedLayoutItems.size())
			{
				outsideLayoutCellIds.add(
						slot.getPlacement().getCellId()
				);

				updatedSlots.add(slot);
				continue;
			}

			int currentItemId =
					projectedLayoutItems.get(index);

			SlotResolution resolution =
					resolutionsByCellId.get(
							slot.getPlacement().getCellId()
					);

			if (resolution == null
					|| resolution.getState()
					== SlotResolution.State.UNRESOLVED)
			{
				addPositive(
						dynamicItemIds,
						currentItemId
				);

				updatedSlots.add(slot);
				continue;
			}

			if (!slot.matchesLayoutItem(currentItemId))
			{
				addPositive(
						dynamicItemIds,
						currentItemId
				);

				conflictsByCellId.put(
						slot.getPlacement().getCellId(),
						new Conflict(
								slot.getLastProjectedExactItemId(),
								currentItemId
						)
				);

				updatedSlots.add(slot);
				continue;
			}

			int resolvedItemId =
					resolution.getExactItemId();

			if (resolution.isOwned())
			{
				dynamicItemIds.add(resolvedItemId);
			}

			projectedLayoutItems.set(
					index,
					resolvedItemId
			);

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

		BankTagBinding updatedBinding =
				bindingChanged
						? binding.withSlots(updatedSlots)
						: binding;

		return new Plan(
				updatedBinding,
				bindingChanged,
				managedItemIds,
				dynamicItemIds,
				originalLayoutItems,
				projectedLayoutItems,
				outsideLayoutCellIds,
				conflictsByCellId
		);
	}

	List<Integer> scrubManagedItemsOutsideSlots(
			List<Integer> layoutItems,
			BankTagBinding binding,
			Set<Integer> managedItemIds)
	{
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(
				managedItemIds,
				"managedItemIds"
		);

		List<Integer> result =
				new ArrayList<>(
						copyLayoutItems(layoutItems)
				);

		Set<Integer> reservedIndices =
				BankTagManagedItems.reservedIndices(
						binding
				);

		for (int index = 0;
		     index < result.size();
		     index++)
		{
			int itemId = result.get(index);

			if (itemId > 0
					&& !reservedIndices.contains(index)
					&& managedItemIds.contains(itemId))
			{
				result.set(
						index,
						EMPTY_LAYOUT_ITEM
				);
			}
		}

		return List.copyOf(result);
	}

	private Map<String, SlotResolution>
	resolutionsByCellId(
			BankTagBinding binding,
			PriorityState state,
			BankSnapshot bankSnapshot)
	{
		List<SlotResolution> resolutions =
				resolver.resolveBinding(
						binding,
						state.definitionsById(),
						bankSnapshot
				);

		Map<String, SlotResolution> result =
				new HashMap<>();

		for (SlotResolution resolution
				: resolutions)
		{
			result.put(
					resolution.getCellId(),
					resolution
			);
		}

		return result;
	}

	private static List<Integer> copyLayoutItems(
			List<Integer> layoutItems)
	{
		Objects.requireNonNull(
				layoutItems,
				"layoutItems"
		);

		for (Integer itemId : layoutItems)
		{
			Objects.requireNonNull(
					itemId,
					"layoutItems must not contain null"
			);
		}

		return List.copyOf(layoutItems);
	}

	private static void addPositive(
			Set<Integer> itemIds,
			int itemId)
	{
		if (itemId > 0)
		{
			itemIds.add(itemId);
		}
	}

	static final class Plan
	{
		private final BankTagBinding binding;
		private final boolean bindingChanged;
		private final Set<Integer> managedItemIds;
		private final Set<Integer> dynamicItemIds;
		private final List<Integer> originalLayoutItems;
		private final List<Integer> projectedLayoutItems;
		private final Set<String> outsideLayoutCellIds;
		private final Map<String, Conflict>
				conflictsByCellId;

		private Plan(
				BankTagBinding binding,
				boolean bindingChanged,
				Set<Integer> managedItemIds,
				Set<Integer> dynamicItemIds,
				List<Integer> originalLayoutItems,
				List<Integer> projectedLayoutItems,
				Set<String> outsideLayoutCellIds,
				Map<String, Conflict>
						conflictsByCellId)
		{
			this.binding = Objects.requireNonNull(
					binding,
					"binding"
			);

			this.bindingChanged = bindingChanged;

			this.managedItemIds =
					Set.copyOf(managedItemIds);

			this.dynamicItemIds =
					Set.copyOf(dynamicItemIds);

			this.originalLayoutItems =
					List.copyOf(originalLayoutItems);

			this.projectedLayoutItems =
					List.copyOf(projectedLayoutItems);

			this.outsideLayoutCellIds =
					Set.copyOf(outsideLayoutCellIds);

			this.conflictsByCellId =
					Collections.unmodifiableMap(
							new LinkedHashMap<>(
									conflictsByCellId
							)
					);
		}

		BankTagBinding getBinding()
		{
			return binding;
		}

		Set<Integer> getManagedItemIds()
		{
			return managedItemIds;
		}

		Set<Integer> getDynamicItemIds()
		{
			return dynamicItemIds;
		}

		List<Integer> getProjectedLayoutItems()
		{
			return projectedLayoutItems;
		}

		Set<String> getOutsideLayoutCellIds()
		{
			return outsideLayoutCellIds;
		}

		Map<String, Conflict> getConflictsByCellId()
		{
			return conflictsByCellId;
		}

		boolean isBindingChanged()
		{
			return bindingChanged;
		}

		boolean isLayoutChanged()
		{
			return !originalLayoutItems.equals(
					projectedLayoutItems
			);
		}

	}

	static final class Conflict
	{
		private final int expectedItemId;
		private final int currentItemId;

		private Conflict(
				int expectedItemId,
				int currentItemId)
		{
			this.expectedItemId = expectedItemId;
			this.currentItemId = currentItemId;
		}

		int getExpectedItemId()
		{
			return expectedItemId;
		}

		int getCurrentItemId()
		{
			return currentItemId;
		}
	}
}
