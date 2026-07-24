package com.priorityslots.banktags;

import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;

final class BankTagPlacementReconciler
{
	Result reconcile(
			BankTagBinding binding,
			IntUnaryOperator itemAtPosition,
			int layoutSize)
	{
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(
				itemAtPosition,
				"itemAtPosition"
		);

		if (layoutSize < 0)
		{
			throw new IllegalArgumentException(
					"layoutSize must not be negative"
			);
		}

		List<Integer> reconciledIndices =
				new ArrayList<>();

		Set<Integer> claimedIndices =
				new HashSet<>();

		boolean bindingChanged = false;

		for (BankTagSlotBinding slot
				: binding.getSlots())
		{
			int previousIndex =
					slot.getPlacement().getIndex();

			int expectedItemId =
					slot.getLastProjectedExactItemId();

			int reconciledIndex;

			if (previousIndex < layoutSize
					&& itemAtPosition.applyAsInt(
					previousIndex) == expectedItemId)
			{
				reconciledIndex = previousIndex;
			}
			else
			{
				reconciledIndex = findUniqueItemPosition(
						itemAtPosition,
						layoutSize,
						expectedItemId
				);
			}

			if (reconciledIndex < 0
					|| !claimedIndices.add(
					reconciledIndex))
			{
				return Result.unsafe(binding);
			}

			reconciledIndices.add(reconciledIndex);

			bindingChanged |=
					reconciledIndex != previousIndex;
		}

		if (!bindingChanged)
		{
			return Result.safe(binding);
		}

		List<BankTagSlotBinding> updatedSlots =
				new ArrayList<>();

		for (int slotIndex = 0;
		     slotIndex < binding.getSlots().size();
		     slotIndex++)
		{
			BankTagSlotBinding slot =
					binding.getSlots().get(slotIndex);

			int reconciledIndex =
					reconciledIndices.get(slotIndex);

			if (reconciledIndex
					== slot.getPlacement().getIndex())
			{
				updatedSlots.add(slot);
				continue;
			}

			updatedSlots.add(
					new BankTagSlotBinding(
							slot.getPlacement().withIndex(
									reconciledIndex
							),
							slot.getFallbackExactItemId(),
							slot.getLastProjectedExactItemId()
					)
			);
		}

		return Result.safe(
				binding.withSlots(updatedSlots)
		);
	}

	private static int findUniqueItemPosition(
			IntUnaryOperator itemAtPosition,
			int layoutSize,
			int exactItemId)
	{
		int foundIndex = -1;

		for (int index = 0;
		     index < layoutSize;
		     index++)
		{
			if (itemAtPosition.applyAsInt(index)
					!= exactItemId)
			{
				continue;
			}

			if (foundIndex >= 0)
			{
				return -1;
			}

			foundIndex = index;
		}

		return foundIndex;
	}

	static final class Result
	{
		private final BankTagBinding binding;
		private final boolean projectionSafe;

		private Result(
				BankTagBinding binding,
				boolean projectionSafe)
		{
			this.binding = Objects.requireNonNull(
					binding,
					"binding"
			);

			this.projectionSafe = projectionSafe;
		}

		static Result safe(BankTagBinding binding)
		{
			return new Result(binding, true);
		}

		static Result unsafe(BankTagBinding binding)
		{
			return new Result(binding, false);
		}

		BankTagBinding getBinding()
		{
			return binding;
		}

		boolean isProjectionSafe()
		{
			return projectionSafe;
		}
	}
}
