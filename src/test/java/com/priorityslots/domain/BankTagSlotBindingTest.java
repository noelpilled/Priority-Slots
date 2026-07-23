package com.priorityslots.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BankTagSlotBindingTest
{
	private static final int FALLBACK_ITEM = 1005;
	private static final int PROJECTED_ITEM = 1003;

	@Test
	public void startsWithFallbackAsProjection()
	{
		CellPlacement placement =
				createPlacement();

		BankTagSlotBinding binding =
				BankTagSlotBinding.create(
						placement,
						FALLBACK_ITEM
				);

		assertEquals(
				placement,
				binding.getPlacement()
		);
		assertEquals(
				FALLBACK_ITEM,
				binding.getFallbackExactItemId()
		);
		assertEquals(
				FALLBACK_ITEM,
				binding.getLastProjectedExactItemId()
		);
		assertTrue(
				binding.matchesLayoutItem(
						FALLBACK_ITEM
				)
		);
	}

	@Test
	public void updatingProjectionPreservesBindingData()
	{
		BankTagSlotBinding original =
				BankTagSlotBinding.create(
						createPlacement(),
						FALLBACK_ITEM
				);

		BankTagSlotBinding updated =
				original.withLastProjectedExactItemId(
						PROJECTED_ITEM
				);

		assertEquals(
				original.getPlacement(),
				updated.getPlacement()
		);
		assertEquals(
				FALLBACK_ITEM,
				updated.getFallbackExactItemId()
		);
		assertEquals(
				PROJECTED_ITEM,
				updated.getLastProjectedExactItemId()
		);

		assertTrue(
				updated.matchesLayoutItem(
						PROJECTED_ITEM
				)
		);
		assertFalse(
				updated.matchesLayoutItem(
						FALLBACK_ITEM
				)
		);
	}

	@Test
	public void rejectsInvalidFallbackItem()
	{
		assertIllegalArgument(() ->
				new BankTagSlotBinding(
						createPlacement(),
						0,
						PROJECTED_ITEM
				)
		);
	}

	@Test
	public void rejectsInvalidProjectedItem()
	{
		assertIllegalArgument(() ->
				new BankTagSlotBinding(
						createPlacement(),
						FALLBACK_ITEM,
						0
				)
		);
	}

	private static CellPlacement createPlacement()
	{
		return new CellPlacement(
				"cell-1",
				"definition-1",
				4
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
