package com.priorityslots.banktags;

import com.priorityslots.domain.BankSnapshot;
import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.CellPlacement;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DuplicateWinnerProjectionRegressionTest
{
	private static final int LANTADYME = 101;
	private static final int CADANTINE = 102;
	private static final int PARTY_POPPER = 103;
	private static final int SHOVEL = 104;
	private static final int UNRELATED = 201;
	private static final int EMPTY = -1;

	private final BankTagPlacementReconciler reconciler =
			new BankTagPlacementReconciler();

	private final BankTagProjectionPlanner planner =
			new BankTagProjectionPlanner();

	@Test
	public void duplicateWinnerSurvivesInstallRebuildAndDivergence()
	{
		Fixture fixture = fixture();

		BankTagProjectionPlanner.Plan initialPlan = planner.plan(
				fixture.binding,
				fixture.state,
				bank(LANTADYME),
				List.of(LANTADYME, PARTY_POPPER)
		);

		assertEquals(
				List.of(LANTADYME, LANTADYME),
				initialPlan.getProjectedLayoutItems()
		);
		assertEquals(
				LANTADYME,
				initialPlan.getBinding()
						.getSlots()
						.get(1)
						.getLastProjectedExactItemId()
		);

		int[] temporarilyEmptyLayout = {
			LANTADYME,
			EMPTY
		};

		BankTagPlacementReconciler.Result reconciliation =
				reconciler.reconcile(
						initialPlan.getBinding(),
						index -> temporarilyEmptyLayout[index],
						temporarilyEmptyLayout.length
				);

		assertTrue(reconciliation.isProjectionSafe());
		assertSame(
				initialPlan.getBinding(),
				reconciliation.getBinding()
		);

		PriorityState reconciledState = new PriorityState(
				fixture.state.getDefinitions(),
				fixture.state.getGroups(),
				List.of(reconciliation.getBinding()),
				fixture.state.getRootEntries()
		);

		BankTagProjectionPlanner.Plan restoredPlan = planner.plan(
				reconciliation.getBinding(),
				reconciledState,
				bank(LANTADYME),
				List.of(LANTADYME, EMPTY)
		);

		assertEquals(
				List.of(LANTADYME, LANTADYME),
				restoredPlan.getProjectedLayoutItems()
		);
		assertTrue(restoredPlan.isLayoutChanged());
		assertTrue(restoredPlan.getConflictsByCellId().isEmpty());
		assertCellIdentityPreserved(
				fixture.binding,
				restoredPlan.getBinding()
		);

		PriorityState restoredState = new PriorityState(
				fixture.state.getDefinitions(),
				fixture.state.getGroups(),
				List.of(restoredPlan.getBinding()),
				fixture.state.getRootEntries()
		);

		BankTagProjectionPlanner.Plan divergedPlan = planner.plan(
				restoredPlan.getBinding(),
				restoredState,
				bank(LANTADYME, PARTY_POPPER),
				restoredPlan.getProjectedLayoutItems()
		);

		assertEquals(
				List.of(LANTADYME, PARTY_POPPER),
				divergedPlan.getProjectedLayoutItems()
		);
		assertCellIdentityPreserved(
				fixture.binding,
				divergedPlan.getBinding()
		);
	}

	@Test
	public void reconcilerKeepsDistinctSavedDuplicateCells()
	{
		Fixture fixture = fixtureWithDuplicateLastWinners();
		int[] layout = {
			LANTADYME,
			LANTADYME
		};

		BankTagPlacementReconciler.Result result =
				reconciler.reconcile(
						fixture.binding,
						index -> layout[index],
						layout.length
				);

		assertTrue(result.isProjectionSafe());
		assertSame(fixture.binding, result.getBinding());
	}

	@Test
	public void reconcilerStillRejectsUnownedAmbiguousDuplicates()
	{
		BankTagSlotBinding slot = new BankTagSlotBinding(
				new CellPlacement(
						"cell-first",
						"definition-first",
						0
				),
				LANTADYME,
				LANTADYME
		);

		BankTagBinding binding = new BankTagBinding(
				"binding-ambiguous",
				"Herbs",
				List.of(slot)
		);

		int[] layout = {
			UNRELATED,
			LANTADYME,
			LANTADYME
		};

		BankTagPlacementReconciler.Result result =
				reconciler.reconcile(
						binding,
						index -> layout[index],
						layout.length
				);

		assertFalse(result.isProjectionSafe());
		assertSame(binding, result.getBinding());
	}

	private static Fixture fixture()
	{
		PriorityDefinition firstDefinition = new PriorityDefinition(
				"definition-first",
				"First herbs",
				List.of(
						new PriorityTier(
								"first-lantadyme",
								List.of(LANTADYME)
						),
						new PriorityTier(
								"first-cadantine",
								List.of(CADANTINE)
						)
				)
		);

		PriorityDefinition secondDefinition = new PriorityDefinition(
				"definition-second",
				"Second mixed items",
				List.of(
						new PriorityTier(
								"second-party-popper",
								List.of(PARTY_POPPER)
						),
						new PriorityTier(
								"second-lantadyme",
								List.of(LANTADYME)
						),
						new PriorityTier(
								"second-cadantine",
								List.of(CADANTINE)
						),
						new PriorityTier(
								"second-shovel",
								List.of(SHOVEL)
						)
				)
		);

		BankTagSlotBinding firstSlot = new BankTagSlotBinding(
				new CellPlacement(
						"cell-first",
						firstDefinition.getId(),
						0
				),
				LANTADYME,
				LANTADYME
		);

		BankTagSlotBinding secondSlot = new BankTagSlotBinding(
				new CellPlacement(
						"cell-second",
						secondDefinition.getId(),
						1
				),
				PARTY_POPPER,
				PARTY_POPPER
		);

		BankTagBinding binding = new BankTagBinding(
				"binding-1",
				"Herbs",
				List.of(firstSlot, secondSlot)
		);

		PriorityState state = new PriorityState(
				List.of(firstDefinition, secondDefinition),
				List.of(),
				List.of(binding)
		);

		return new Fixture(binding, state);
	}

	private static Fixture fixtureWithDuplicateLastWinners()
	{
		Fixture fixture = fixture();
		BankTagSlotBinding secondSlot = fixture.binding
				.getSlots()
				.get(1)
				.withLastProjectedExactItemId(LANTADYME);

		BankTagBinding duplicateBinding = fixture.binding.withSlots(
				List.of(
						fixture.binding.getSlots().get(0),
						secondSlot
				)
		);

		PriorityState duplicateState = new PriorityState(
				fixture.state.getDefinitions(),
				fixture.state.getGroups(),
				List.of(duplicateBinding),
				fixture.state.getRootEntries()
		);

		return new Fixture(duplicateBinding, duplicateState);
	}

	private static BankSnapshot bank(int... itemIds)
	{
		Map<Integer, Integer> quantities = new HashMap<>();

		for (int itemId : itemIds)
		{
			quantities.put(itemId, 1);
		}

		return new BankSnapshot(quantities);
	}

	private static void assertCellIdentityPreserved(
			BankTagBinding original,
			BankTagBinding updated)
	{
		assertEquals(original.getId(), updated.getId());
		assertEquals(
				original.getSlots().get(0).getPlacement().getCellId(),
				updated.getSlots().get(0).getPlacement().getCellId()
		);
		assertEquals(
				original.getSlots().get(1).getPlacement().getCellId(),
				updated.getSlots().get(1).getPlacement().getCellId()
		);
		assertEquals(
				original.getSlots().get(0).getPlacement().getDefinitionId(),
				updated.getSlots().get(0).getPlacement().getDefinitionId()
		);
		assertEquals(
				original.getSlots().get(1).getPlacement().getDefinitionId(),
				updated.getSlots().get(1).getPlacement().getDefinitionId()
		);
	}

	private static final class Fixture
	{
		private final BankTagBinding binding;
		private final PriorityState state;

		private Fixture(
				BankTagBinding binding,
				PriorityState state)
		{
			this.binding = binding;
			this.state = state;
		}
	}
}
