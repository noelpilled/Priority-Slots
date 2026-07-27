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
import static org.junit.Assert.assertTrue;

public class BankTagsMembershipChangeDetectorTest
{
	private static final int EXACT_ITEM_ID = 101;
	private static final int CANONICAL_ITEM_ID = 1001;
	private static final int VARIATION_ITEM_ID = 2001;

	private final BankTagsMembershipChangeDetector detector =
		new BankTagsMembershipChangeDetector(
			itemId -> itemId == EXACT_ITEM_ID
				? CANONICAL_ITEM_ID
				: itemId,
			itemId -> itemId == CANONICAL_ITEM_ID
				? VARIATION_ITEM_ID
				: itemId
		);

	@Test
	public void detectsReaddedCanonicalMembership()
	{
		assertEquals(
			Set.of("binding-1"),
			detector.affectedBindingIds(
				state(),
				"item_" + CANONICAL_ITEM_ID,
				"supplies, herbs"
			)
		);
	}

	@Test
	public void detectsReaddedVariationMembership()
	{
		assertEquals(
			Set.of("binding-1"),
			detector.affectedBindingIds(
				state(),
				"item_-" + VARIATION_ITEM_ID,
				"HERBS"
			)
		);
	}

	@Test
	public void ignoresCleanupGeneratedRemovalEvent()
	{
		assertTrue(detector.affectedBindingIds(
			state(),
			"item_" + CANONICAL_ITEM_ID,
			"supplies"
		).isEmpty());
	}

	@Test
	public void ignoresUnmanagedItemsAndUnrelatedTags()
	{
		assertTrue(detector.affectedBindingIds(
			state(),
			"item_9999",
			"herbs"
		).isEmpty());
		assertTrue(detector.affectedBindingIds(
			state(),
			"item_" + CANONICAL_ITEM_ID,
			"supplies"
		).isEmpty());
		assertTrue(detector.affectedBindingIds(
			state(),
			"layout_herbs",
			"herbs"
		).isEmpty());
	}

	private static PriorityState state()
	{
		PriorityDefinition definition = new PriorityDefinition(
			"definition-1",
			"Herbs",
			List.of(new PriorityTier(
				"tier-1",
				List.of(EXACT_ITEM_ID)
			))
		);
		BankTagSlotBinding slot = BankTagSlotBinding.create(
			new CellPlacement(
				"cell-1",
				definition.getId(),
				0
			),
			EXACT_ITEM_ID
		);
		BankTagBinding binding = new BankTagBinding(
			"binding-1",
			"Herbs",
			List.of(slot)
		);

		return new PriorityState(
			List.of(definition),
			List.of(),
			List.of(binding)
		);
	}
}
