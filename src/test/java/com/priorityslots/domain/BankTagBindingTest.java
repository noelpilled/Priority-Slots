package com.priorityslots.domain;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class BankTagBindingTest
{
	private static final int FIRST_ITEM = 1005;
	private static final int SECOND_ITEM = 1003;

	@Test
	public void copiesSlotList()
	{
		BankTagSlotBinding slot =
				createSlot(
						"cell-1",
						"definition-1",
						2,
						FIRST_ITEM
				);

		List<BankTagSlotBinding> slots =
				new ArrayList<>();

		slots.add(slot);

		BankTagBinding binding =
				new BankTagBinding(
						"binding-1",
						"Melee",
						slots
				);

		slots.clear();

		assertEquals(
				List.of(slot),
				binding.getSlots()
		);
	}

	@Test
	public void preservesPlacementOrder()
	{
		BankTagSlotBinding first =
				createSlot(
						"cell-1",
						"definition-1",
						8,
						FIRST_ITEM
				);

		BankTagSlotBinding second =
				createSlot(
						"cell-2",
						"definition-2",
						3,
						SECOND_ITEM
				);

		BankTagBinding binding =
				new BankTagBinding(
						"binding-1",
						"Melee",
						List.of(first, second)
				);

		assertEquals(
				List.of(
						first.getPlacement(),
						second.getPlacement()
				),
				binding.placements()
		);
	}

	@Test
	public void renamingTagPreservesIdentity()
	{
		BankTagBinding original =
				new BankTagBinding(
						"binding-1",
						"Old tag",
						List.of()
				);

		BankTagBinding renamed =
				original.withBankTagName(
						"New tag"
				);

		assertEquals(
				original.getId(),
				renamed.getId()
		);
		assertEquals(
				"New tag",
				renamed.getBankTagName()
		);
		assertNotEquals(
				original.getBankTagName(),
				renamed.getBankTagName()
		);
	}

	@Test
	public void trimsBankTagName()
	{
		BankTagBinding binding =
				new BankTagBinding(
						"binding-1",
						"  Melee  ",
						List.of()
				);

		assertEquals(
				"Melee",
				binding.getBankTagName()
		);
	}

	@Test
	public void rejectsDuplicateCellIds()
	{
		BankTagSlotBinding first =
				createSlot(
						"cell-1",
						"definition-1",
						2,
						FIRST_ITEM
				);

		BankTagSlotBinding duplicate =
				createSlot(
						"cell-1",
						"definition-2",
						3,
						SECOND_ITEM
				);

		assertIllegalArgument(() ->
				new BankTagBinding(
						"binding-1",
						"Melee",
						List.of(first, duplicate)
				)
		);
	}

	@Test
	public void rejectsDuplicateLayoutIndices()
	{
		BankTagSlotBinding first =
				createSlot(
						"cell-1",
						"definition-1",
						2,
						FIRST_ITEM
				);

		BankTagSlotBinding duplicate =
				createSlot(
						"cell-2",
						"definition-2",
						2,
						SECOND_ITEM
				);

		assertIllegalArgument(() ->
				new BankTagBinding(
						"binding-1",
						"Melee",
						List.of(first, duplicate)
				)
		);
	}

	private static BankTagSlotBinding createSlot(
			String cellId,
			String definitionId,
			int index,
			int fallbackExactItemId)
	{
		CellPlacement placement =
				new CellPlacement(
						cellId,
						definitionId,
						index
				);

		return BankTagSlotBinding.create(
				placement,
				fallbackExactItemId
		);
	}

	private static void assertIllegalArgument(
			Runnable action)
	{
		try
		{
			action.run();
			fail(
					"Expected IllegalArgumentException"
			);
		}
		catch (IllegalArgumentException expected)
		{
			// Expected.
		}
	}
}
