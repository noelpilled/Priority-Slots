package com.priorityslots.domain;

import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PriorityResolverTest
{
	private static final int HIGH_PRIORITY_ITEM = 1005;
	private static final int MIDDLE_PRIORITY_ITEM = 1004;
	private static final int LOW_PRIORITY_ITEM = 1003;

	private final PriorityResolver resolver =
			new PriorityResolver();

	@Test
	public void choosesFirstOwnedItemInPriorityOrder()
	{
		PriorityDefinition definition =
				createDefinition();

		CellPlacement placement =
				createPlacement(definition);

		BankSnapshot bankSnapshot =
				new BankSnapshot(
						Map.of(
								MIDDLE_PRIORITY_ITEM, 1,
								LOW_PRIORITY_ITEM, 1
						)
				);

		SlotResolution resolution =
				resolver.resolve(
						placement,
						Map.of(
								definition.getId(),
								definition
						),
						bankSnapshot
				);

		assertEquals(
				SlotResolution.State.OWNED,
				resolution.getState()
		);
		assertEquals(
				MIDDLE_PRIORITY_ITEM,
				resolution.getExactItemId()
		);
		assertTrue(resolution.isOwned());
	}

	@Test
	public void choosesHighestItemWhenMultipleAreOwned()
	{
		PriorityDefinition definition =
				createDefinition();

		CellPlacement placement =
				createPlacement(definition);

		BankSnapshot bankSnapshot =
				new BankSnapshot(
						Map.of(
								HIGH_PRIORITY_ITEM, 1,
								MIDDLE_PRIORITY_ITEM, 1,
								LOW_PRIORITY_ITEM, 1
						)
				);

		SlotResolution resolution =
				resolver.resolve(
						placement,
						Map.of(
								definition.getId(),
								definition
						),
						bankSnapshot
				);

		assertEquals(
				HIGH_PRIORITY_ITEM,
				resolution.getExactItemId()
		);
	}

	@Test
	public void returnsGhostWhenNoCandidateIsOwned()
	{
		PriorityDefinition definition =
				createDefinition();

		CellPlacement placement =
				createPlacement(definition);

		SlotResolution resolution =
				resolver.resolve(
						placement,
						Map.of(
								definition.getId(),
								definition
						),
						BankSnapshot.empty()
				);

		assertEquals(
				SlotResolution.State.GHOST,
				resolution.getState()
		);
		assertEquals(
				HIGH_PRIORITY_ITEM,
				resolution.getExactItemId()
		);
		assertFalse(resolution.isOwned());
	}

	@Test
	public void treatsMissingHigherPriorityItemAsNotOwned()
	{
		PriorityDefinition definition =
				createDefinition();

		CellPlacement placement =
				createPlacement(definition);

		BankSnapshot bankSnapshot =
				new BankSnapshot(
						Map.of(
								LOW_PRIORITY_ITEM,
								2
						)
				);

		SlotResolution resolution =
				resolver.resolve(
						placement,
						Map.of(
								definition.getId(),
								definition
						),
						bankSnapshot
				);

		assertEquals(
				SlotResolution.State.OWNED,
				resolution.getState()
		);
		assertEquals(
				LOW_PRIORITY_ITEM,
				resolution.getExactItemId()
		);
	}

	@Test
	public void returnsUnresolvedWhenDefinitionIsMissing()
	{
		CellPlacement placement =
				new CellPlacement(
						"cell-1",
						"missing-definition",
						0
				);

		SlotResolution resolution =
				resolver.resolve(
						placement,
						Map.of(),
						BankSnapshot.empty()
				);

		assertEquals(
				SlotResolution.State.UNRESOLVED,
				resolution.getState()
		);
		assertEquals(
				-1,
				resolution.getExactItemId()
		);
	}

	@Test
	public void returnsUnresolvedForEmptyDefinition()
	{
		PriorityDefinition definition =
				new PriorityDefinition(
						"definition-1",
						"Empty definition",
						List.of()
				);

		CellPlacement placement =
				createPlacement(definition);

		SlotResolution resolution =
				resolver.resolve(
						placement,
						Map.of(
								definition.getId(),
								definition
						),
						BankSnapshot.empty()
				);

		assertEquals(
				SlotResolution.State.UNRESOLVED,
				resolution.getState()
		);
	}

	@Test
	public void preservesCellAndDefinitionIdentity()
	{
		PriorityDefinition definition =
				createDefinition();

		CellPlacement placement =
				createPlacement(definition);

		SlotResolution resolution =
				resolver.resolve(
						placement,
						Map.of(
								definition.getId(),
								definition
						),
						new BankSnapshot(
								Map.of(
										MIDDLE_PRIORITY_ITEM,
										1
								)
						)
				);

		assertEquals(
				placement.getCellId(),
				resolution.getCellId()
		);
		assertEquals(
				placement.getDefinitionId(),
				resolution.getDefinitionId()
		);
		assertEquals(
				placement.getIndex(),
				resolution.getIndex()
		);
	}

	@Test
	public void resolveBindingOrdersResultsByPlacementIndex()
	{
		PriorityDefinition definition =
				createDefinition();

		CellPlacement ownedPlacement =
				new CellPlacement(
						"owned-cell",
						definition.getId(),
						4
				);

		CellPlacement missingPlacement =
				new CellPlacement(
						"missing-cell",
						"missing-definition",
						1
				);

		BankTagSlotBinding ownedSlot =
				BankTagSlotBinding.create(
						ownedPlacement,
						HIGH_PRIORITY_ITEM
				);

		BankTagSlotBinding missingSlot =
				BankTagSlotBinding.create(
						missingPlacement,
						LOW_PRIORITY_ITEM
				);

		BankTagBinding binding =
				new BankTagBinding(
						"binding-1",
						"Test tag",
						List.of(
								ownedSlot,
								missingSlot
						)
				);

		List<SlotResolution> resolutions =
				resolver.resolveBinding(
						binding,
						Map.of(
								definition.getId(),
								definition
						),
						new BankSnapshot(
								Map.of(
										HIGH_PRIORITY_ITEM,
										1
								)
						)
				);

		assertEquals(2, resolutions.size());

		assertEquals(
				"missing-cell",
				resolutions.get(0).getCellId()
		);
		assertEquals(
				1,
				resolutions.get(0).getIndex()
		);
		assertEquals(
				SlotResolution.State.UNRESOLVED,
				resolutions.get(0).getState()
		);

		assertEquals(
				"owned-cell",
				resolutions.get(1).getCellId()
		);
		assertEquals(
				4,
				resolutions.get(1).getIndex()
		);
		assertEquals(
				SlotResolution.State.OWNED,
				resolutions.get(1).getState()
		);
	}

	private static PriorityDefinition createDefinition()
	{
		PriorityTier tier =
				new PriorityTier(
						"tier-1",
						List.of(
								HIGH_PRIORITY_ITEM,
								MIDDLE_PRIORITY_ITEM,
								LOW_PRIORITY_ITEM
						)
				);

		return new PriorityDefinition(
				"definition-1",
				"Teleport jewellery",
				List.of(tier)
		);
	}

	private static CellPlacement createPlacement(
			PriorityDefinition definition)
	{
		return new CellPlacement(
				"cell-1",
				definition.getId(),
				0
		);
	}
}
