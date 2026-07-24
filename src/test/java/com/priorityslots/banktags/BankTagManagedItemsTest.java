package com.priorityslots.banktags;

import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.CellPlacement;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BankTagManagedItemsTest
{
	private static final int HIGH_PRIORITY_ITEM = 1005;
	private static final int LOW_PRIORITY_ITEM = 1003;
	private static final int UNRELATED_FALLBACK_ITEM = 2000;

	@Test
	public void collectsOnlyDefinitionCandidates()
	{
		PriorityDefinition definition =
				new PriorityDefinition(
						"definition-1",
						"Herbs",
						List.of(
								new PriorityTier(
										"tier-1",
										List.of(
												HIGH_PRIORITY_ITEM
										)
								),
								new PriorityTier(
										"tier-2",
										List.of(
												LOW_PRIORITY_ITEM
										)
								)
						)
				);

		BankTagBinding binding = binding(
				definition.getId(),
				4,
				UNRELATED_FALLBACK_ITEM
		);

		PriorityState state =
				new PriorityState(
						List.of(definition),
						List.of(),
						List.of(binding)
				);

		Set<Integer> managedItemIds =
				BankTagManagedItems.collect(
						binding,
						state
				);

		assertEquals(
				Set.of(
						HIGH_PRIORITY_ITEM,
						LOW_PRIORITY_ITEM
				),
				managedItemIds
		);

		assertFalse(
				managedItemIds.contains(
						UNRELATED_FALLBACK_ITEM
				)
		);
	}

	@Test
	public void missingDefinitionProducesNoManagedItems()
	{
		BankTagBinding binding = binding(
				"missing-definition",
				4,
				UNRELATED_FALLBACK_ITEM
		);

		Set<Integer> managedItemIds =
				BankTagManagedItems.collect(
						binding,
						new PriorityState(
								List.of(),
								List.of(),
								List.of(binding)
						)
				);

		assertTrue(managedItemIds.isEmpty());
	}

	@Test
	public void exposesReservedLayoutIndices()
	{
		BankTagBinding binding = binding(
				"definition-1",
				7,
				UNRELATED_FALLBACK_ITEM
		);

		assertEquals(
				Set.of(7),
				BankTagManagedItems.reservedIndices(
						binding
				)
		);
	}

	private static BankTagBinding binding(
			String definitionId,
			int index,
			int fallbackItemId)
	{
		CellPlacement placement =
				new CellPlacement(
						"cell-1",
						definitionId,
						index
				);

		BankTagSlotBinding slot =
				BankTagSlotBinding.create(
						placement,
						fallbackItemId
				);

		return new BankTagBinding(
				"binding-1",
				"Herbs",
				List.of(slot)
		);
	}
}
