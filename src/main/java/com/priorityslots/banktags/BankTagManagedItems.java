package com.priorityslots.banktags;

import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class BankTagManagedItems
{
	private BankTagManagedItems()
	{
	}

	static Set<Integer> collect(
			BankTagBinding binding,
			PriorityState state)
	{
		Objects.requireNonNull(binding, "binding");
		Objects.requireNonNull(state, "state");

		Set<Integer> result = new HashSet<>();

		Map<String, PriorityDefinition> definitionsById =
				state.definitionsById();

		for (BankTagSlotBinding slot
				: binding.getSlots())
		{
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

	static Set<Integer> reservedIndices(
			BankTagBinding binding)
	{
		Objects.requireNonNull(binding, "binding");

		Set<Integer> result = new HashSet<>();

		for (BankTagSlotBinding slot
				: binding.getSlots())
		{
			result.add(
					slot.getPlacement().getIndex()
			);
		}

		return Set.copyOf(result);
	}
}
