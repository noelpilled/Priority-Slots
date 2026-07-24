package com.priorityslots.banktags;

import com.priorityslots.domain.BankSnapshot;
import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.CellPlacement;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BankTagProjectionPlannerTest
{
	private static final int LANTADYME = 1005;
	private static final int CADANTINE = 1003;
	private static final int UNRELATED = 2000;
	private static final int EMPTY = -1;

	private final BankTagProjectionPlanner planner =
			new BankTagProjectionPlanner();

	@Test
	public void highestOwnedCandidateWins()
	{
		Fixture fixture = fixture(
				0,
				CADANTINE,
				List.of(CADANTINE, UNRELATED)
		);

		BankTagProjectionPlanner.Plan plan =
				planner.plan(
						fixture.binding,
						fixture.state,
						bank(
								LANTADYME,
								CADANTINE
						),
						fixture.layout
				);

		assertEquals(
				LANTADYME,
				(int) plan.getProjectedLayoutItems().get(0)
		);
		assertEquals(
				Set.of(LANTADYME),
				plan.getDynamicItemIds()
		);
		assertEquals(
				LANTADYME,
				plan.getBinding()
						.getSlots()
						.get(0)
						.getLastProjectedExactItemId()
		);
		assertTrue(plan.isBindingChanged());
		assertTrue(plan.isLayoutChanged());
		assertStableIdentity(
				fixture.binding,
				plan.getBinding()
		);
	}

	@Test
	public void lowerCandidateWinsWhenHigherCandidateIsAbsent()
	{
		Fixture fixture = fixture(
				0,
				LANTADYME,
				List.of(LANTADYME, UNRELATED)
		);

		BankTagProjectionPlanner.Plan plan =
				planner.plan(
						fixture.binding,
						fixture.state,
						bank(CADANTINE),
						fixture.layout
				);

		assertEquals(
				CADANTINE,
				(int) plan.getProjectedLayoutItems().get(0)
		);
		assertEquals(
				Set.of(CADANTINE),
				plan.getDynamicItemIds()
		);
		assertEquals(
				CADANTINE,
				plan.getBinding()
						.getSlots()
						.get(0)
						.getLastProjectedExactItemId()
		);
	}

	@Test
	public void ghostUsesHighestCandidateWithoutDynamicMembership()
	{
		Fixture fixture = fixture(
				0,
				CADANTINE,
				List.of(CADANTINE)
		);

		BankTagProjectionPlanner.Plan plan =
				planner.plan(
						fixture.binding,
						fixture.state,
						BankSnapshot.empty(),
						fixture.layout
				);

		assertEquals(
				LANTADYME,
				(int) plan.getProjectedLayoutItems().get(0)
		);
		assertTrue(plan.getDynamicItemIds().isEmpty());
	}

	@Test
	public void projectsAtReconciledMovedIndex()
	{
		Fixture fixture = fixture(
				2,
				CADANTINE,
				List.of(
						UNRELATED,
						EMPTY,
						CADANTINE
				)
		);

		BankTagProjectionPlanner.Plan plan =
				planner.plan(
						fixture.binding,
						fixture.state,
						bank(
								LANTADYME,
								CADANTINE
						),
						fixture.layout
				);

		assertEquals(
				UNRELATED,
				(int) plan.getProjectedLayoutItems().get(0)
		);
		assertEquals(
				LANTADYME,
				(int) plan.getProjectedLayoutItems().get(2)
		);
		assertEquals(
				2,
				plan.getBinding()
						.getSlots()
						.get(0)
						.getPlacement()
						.getIndex()
		);
	}

	@Test
	public void scrubsManagedDuplicatesOutsideReservedSlot()
	{
		Fixture fixture = fixture(
				0,
				LANTADYME,
				List.of(
						LANTADYME,
						UNRELATED,
						CADANTINE
				)
		);

		BankTagProjectionPlanner.Plan plan =
				planner.plan(
						fixture.binding,
						fixture.state,
						bank(LANTADYME),
						fixture.layout
				);

		assertEquals(
				EMPTY,
				(int) plan.getProjectedLayoutItems().get(2)
		);
		assertTrue(plan.isLayoutChanged());
	}

	@Test
	public void preservesUnrelatedOrdinaryTaggedItems()
	{
		Fixture fixture = fixture(
				0,
				LANTADYME,
				List.of(
						LANTADYME,
						UNRELATED
				)
		);

		BankTagProjectionPlanner.Plan plan =
				planner.plan(
						fixture.binding,
						fixture.state,
						bank(LANTADYME),
						fixture.layout
				);

		assertEquals(
				UNRELATED,
				(int) plan.getProjectedLayoutItems().get(1)
		);
		assertEquals(
				Set.of(
						LANTADYME,
						CADANTINE
				),
				plan.getManagedItemIds()
		);
	}

	@Test
	public void preservesManualReplacementAndReportsConflict()
	{
		Fixture fixture = fixture(
				0,
				LANTADYME,
				List.of(
						UNRELATED,
						EMPTY
				)
		);

		BankTagProjectionPlanner.Plan plan =
				planner.plan(
						fixture.binding,
						fixture.state,
						bank(LANTADYME),
						fixture.layout
				);

		assertEquals(
				UNRELATED,
				(int) plan.getProjectedLayoutItems().get(0)
		);
		assertEquals(
				Set.of(UNRELATED),
				plan.getDynamicItemIds()
		);
		assertFalse(plan.isBindingChanged());

		BankTagProjectionPlanner.Conflict conflict =
				plan.getConflictsByCellId().get(
						"cell-1"
				);

		assertEquals(
				LANTADYME,
				conflict.getExpectedItemId()
		);
		assertEquals(
				UNRELATED,
				conflict.getCurrentItemId()
		);
	}

	@Test
	public void unresolvedDefinitionPreservesCurrentLayoutItem()
	{
		CellPlacement placement =
				new CellPlacement(
						"cell-1",
						"missing-definition",
						0
				);

		BankTagSlotBinding slot =
				new BankTagSlotBinding(
						placement,
						UNRELATED,
						UNRELATED
				);

		BankTagBinding binding =
				new BankTagBinding(
						"binding-1",
						"Herbs",
						List.of(slot)
				);

		PriorityState state =
				new PriorityState(
						List.of(),
						List.of(),
						List.of(binding)
				);

		BankTagProjectionPlanner.Plan plan =
				planner.plan(
						binding,
						state,
						bank(UNRELATED),
						List.of(UNRELATED)
				);

		assertEquals(
				List.of(UNRELATED),
				plan.getProjectedLayoutItems()
		);
		assertEquals(
				Set.of(UNRELATED),
				plan.getDynamicItemIds()
		);
		assertSame(binding, plan.getBinding());
	}

	@Test
	public void reportsSlotOutsideLayoutWithoutMutation()
	{
		Fixture fixture = fixture(
				4,
				LANTADYME,
				List.of(UNRELATED)
		);

		BankTagProjectionPlanner.Plan plan =
				planner.plan(
						fixture.binding,
						fixture.state,
						bank(LANTADYME),
						fixture.layout
				);

		assertEquals(
				fixture.layout,
				plan.getProjectedLayoutItems()
		);
		assertEquals(
				Set.of("cell-1"),
				plan.getOutsideLayoutCellIds()
		);
		assertFalse(plan.isLayoutChanged());
		assertFalse(plan.isBindingChanged());
	}

	private static Fixture fixture(
			int index,
			int lastProjectedItemId,
			List<Integer> layout)
	{
		PriorityDefinition definition =
				new PriorityDefinition(
						"definition-1",
						"Herbs",
						List.of(
								new PriorityTier(
										"tier-1",
										List.of(LANTADYME)
								),
								new PriorityTier(
										"tier-2",
										List.of(CADANTINE)
								)
						)
				);

		CellPlacement placement =
				new CellPlacement(
						"cell-1",
						definition.getId(),
						index
				);

		BankTagSlotBinding slot =
				new BankTagSlotBinding(
						placement,
						lastProjectedItemId,
						lastProjectedItemId
				);

		BankTagBinding binding =
				new BankTagBinding(
						"binding-1",
						"Herbs",
						List.of(slot)
				);

		PriorityState state =
				new PriorityState(
						List.of(definition),
						List.of(),
						List.of(binding)
				);

		return new Fixture(
				binding,
				state,
				layout
		);
	}

	private static BankSnapshot bank(
			int... itemIds)
	{
		Map<Integer, Integer> quantities =
				new java.util.HashMap<>();

		for (int itemId : itemIds)
		{
			quantities.put(itemId, 1);
		}

		return new BankSnapshot(quantities);
	}

	private static void assertStableIdentity(
			BankTagBinding previous,
			BankTagBinding updated)
	{
		assertEquals(
				previous.getId(),
				updated.getId()
		);
		assertEquals(
				previous.getSlots()
						.get(0)
						.getPlacement()
						.getCellId(),
				updated.getSlots()
						.get(0)
						.getPlacement()
						.getCellId()
		);
		assertEquals(
				previous.getSlots()
						.get(0)
						.getPlacement()
						.getDefinitionId(),
				updated.getSlots()
						.get(0)
						.getPlacement()
						.getDefinitionId()
		);
	}

	private static final class Fixture
	{
		private final BankTagBinding binding;
		private final PriorityState state;
		private final List<Integer> layout;

		private Fixture(
				BankTagBinding binding,
				PriorityState state,
				List<Integer> layout)
		{
			this.binding = binding;
			this.state = state;
			this.layout = List.copyOf(layout);
		}
	}
}
