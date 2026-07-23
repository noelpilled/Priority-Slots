package com.priorityslots.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DomainModelTest
{
	private static final int ITEM_HIGH_PRIORITY = 1005;
	private static final int ITEM_LOW_PRIORITY = 1003;

	@Test
	public void priorityTierPreservesExactItemOrder()
	{
		PriorityTier tier = new PriorityTier(
				"tier-1",
				List.of(
						ITEM_HIGH_PRIORITY,
						ITEM_LOW_PRIORITY
				)
		);

		assertEquals(
				List.of(
						ITEM_HIGH_PRIORITY,
						ITEM_LOW_PRIORITY
				),
				tier.getExactItemIds()
		);
	}

	@Test
	public void priorityTierCopiesInputList()
	{
		List<Integer> itemIds = new ArrayList<>();
		itemIds.add(ITEM_HIGH_PRIORITY);

		PriorityTier tier = new PriorityTier(
				"tier-1",
				itemIds
		);

		itemIds.add(ITEM_LOW_PRIORITY);

		assertEquals(
				List.of(ITEM_HIGH_PRIORITY),
				tier.getExactItemIds()
		);
	}

	@Test
	public void priorityTierRejectsEmptyItemList()
	{
		assertIllegalArgument(() ->
				new PriorityTier("tier-1", List.of())
		);
	}

	@Test
	public void priorityTierRejectsInvalidItemId()
	{
		assertIllegalArgument(() ->
				new PriorityTier(
						"tier-1",
						List.of(-1)
				)
		);
	}

	@Test
	public void priorityTierRejectsDuplicateItemIds()
	{
		assertIllegalArgument(() ->
				new PriorityTier(
						"tier-1",
						List.of(
								ITEM_HIGH_PRIORITY,
								ITEM_HIGH_PRIORITY
						)
				)
		);
	}

	@Test
	public void renamingDefinitionPreservesIdentity()
	{
		PriorityDefinition original =
				new PriorityDefinition(
						"definition-1",
						"Old name",
						List.of()
				);

		PriorityDefinition renamed =
				original.withName("New name");

		assertEquals(
				"definition-1",
				renamed.getId()
		);
		assertEquals(
				"New name",
				renamed.getName()
		);
		assertNotEquals(
				original.getName(),
				renamed.getName()
		);
	}

	@Test
	public void definitionCopiesTierList()
	{
		PriorityTier tier = new PriorityTier(
				"tier-1",
				List.of(ITEM_HIGH_PRIORITY)
		);

		List<PriorityTier> tiers =
				new ArrayList<>();
		tiers.add(tier);

		PriorityDefinition definition =
				new PriorityDefinition(
						"definition-1",
						"Jewellery",
						tiers
				);

		tiers.clear();

		assertEquals(
				List.of(tier),
				definition.getTiers()
		);
	}

	@Test
	public void movingCellPreservesIdentity()
	{
		CellPlacement placement =
				new CellPlacement(
						"cell-1",
						"definition-1",
						3
				);

		CellPlacement moved =
				placement.withIndex(12);

		assertEquals(
				"cell-1",
				moved.getCellId()
		);
		assertEquals(
				"definition-1",
				moved.getDefinitionId()
		);
		assertEquals(12, moved.getIndex());
	}

	@Test
	public void cellRejectsNegativeIndex()
	{
		assertIllegalArgument(() ->
				new CellPlacement(
						"cell-1",
						"definition-1",
						-1
				)
		);
	}

	@Test
	public void bankSnapshotKeepsExactIdsSeparate()
	{
		BankSnapshot snapshot =
				new BankSnapshot(
						Map.of(
								ITEM_HIGH_PRIORITY, 1,
								ITEM_LOW_PRIORITY, 4
						)
				);

		assertTrue(
				snapshot.contains(ITEM_HIGH_PRIORITY)
		);
		assertTrue(
				snapshot.contains(ITEM_LOW_PRIORITY)
		);
		assertEquals(
				1,
				snapshot.quantityOf(
						ITEM_HIGH_PRIORITY
				)
		);
		assertEquals(
				4,
				snapshot.quantityOf(
						ITEM_LOW_PRIORITY
				)
		);
		assertFalse(snapshot.contains(1004));
	}

	@Test
	public void bankSnapshotCopiesInputMap()
	{
		Map<Integer, Integer> quantities =
				new HashMap<>();
		quantities.put(
				ITEM_HIGH_PRIORITY,
				1
		);

		BankSnapshot snapshot =
				new BankSnapshot(quantities);

		quantities.put(
				ITEM_LOW_PRIORITY,
				1
		);

		assertTrue(
				snapshot.contains(
						ITEM_HIGH_PRIORITY
				)
		);
		assertFalse(
				snapshot.contains(
						ITEM_LOW_PRIORITY
				)
		);
	}

	@Test
	public void ownedResolutionRetainsIdentities()
	{
		SlotResolution resolution =
				SlotResolution.owned(
						"cell-1",
						"definition-1",
						3,
						ITEM_HIGH_PRIORITY
				);

		assertEquals(
				"cell-1",
				resolution.getCellId()
		);
		assertEquals(
				"definition-1",
				resolution.getDefinitionId()
		);
		assertEquals(3, resolution.getIndex());
		assertEquals(
				SlotResolution.State.OWNED,
				resolution.getState()
		);
		assertEquals(
				ITEM_HIGH_PRIORITY,
				resolution.getExactItemId()
		);
		assertTrue(resolution.isOwned());
	}

	@Test
	public void unresolvedResolutionUsesSentinel()
	{
		SlotResolution resolution =
				SlotResolution.unresolved(
						"cell-1",
						"definition-1",
						3
				);

		assertEquals(
				SlotResolution.State.UNRESOLVED,
				resolution.getState()
		);
		assertEquals(3, resolution.getIndex());
		assertEquals(
				-1,
				resolution.getExactItemId()
		);
		assertFalse(resolution.isOwned());
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
