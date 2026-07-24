package com.priorityslots.banktags;

import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.CellPlacement;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BankTagPlacementReconcilerTest
{
	private static final int WINNER_ITEM = 1005;
	private static final int OTHER_ITEM = 2000;
	private static final int EMPTY = -1;

	private final BankTagPlacementReconciler reconciler =
			new BankTagPlacementReconciler();

	@Test
	public void keepsBindingWhenWinnerRemainsAtSavedIndex()
	{
		BankTagBinding binding = bindingAt(0);
		int[] layout = {WINNER_ITEM, OTHER_ITEM};

		BankTagPlacementReconciler.Result result =
				reconciler.reconcile(
						binding,
						index -> layout[index],
						layout.length
				);

		assertTrue(result.isProjectionSafe());
		assertSame(binding, result.getBinding());
	}

	@Test
	public void followsWinnerMovedWhilePluginWasDisabled()
	{
		BankTagBinding binding = bindingAt(0);
		int[] layout = {OTHER_ITEM, EMPTY, WINNER_ITEM};

		BankTagPlacementReconciler.Result result =
				reconciler.reconcile(
						binding,
						index -> layout[index],
						layout.length
				);

		assertTrue(result.isProjectionSafe());
		assertEquals(
				2,
				result.getBinding()
						.getSlots()
						.get(0)
						.getPlacement()
						.getIndex()
		);
	}

	@Test
	public void findsWinnerWhenSavedIndexIsOutsideLayout()
	{
		BankTagBinding binding = bindingAt(8);
		int[] layout = {EMPTY, WINNER_ITEM};

		BankTagPlacementReconciler.Result result =
				reconciler.reconcile(
						binding,
						index -> layout[index],
						layout.length
				);

		assertTrue(result.isProjectionSafe());
		assertEquals(
				1,
				result.getBinding()
						.getSlots()
						.get(0)
						.getPlacement()
						.getIndex()
		);
	}

	@Test
	public void rejectsMissingFrozenWinner()
	{
		BankTagBinding binding = bindingAt(0);
		int[] layout = {OTHER_ITEM, EMPTY};

		BankTagPlacementReconciler.Result result =
				reconciler.reconcile(
						binding,
						index -> layout[index],
						layout.length
				);

		assertFalse(result.isProjectionSafe());
		assertSame(binding, result.getBinding());
	}

	@Test
	public void rejectsDuplicatedFrozenWinner()
	{
		BankTagBinding binding = bindingAt(0);
		int[] layout = {OTHER_ITEM, WINNER_ITEM, WINNER_ITEM};

		BankTagPlacementReconciler.Result result =
				reconciler.reconcile(
						binding,
						index -> layout[index],
						layout.length
				);

		assertFalse(result.isProjectionSafe());
		assertSame(binding, result.getBinding());
	}

	private static BankTagBinding bindingAt(int index)
	{
		CellPlacement placement = new CellPlacement(
				"cell-1",
				"definition-1",
				index
		);

		BankTagSlotBinding slot = new BankTagSlotBinding(
				placement,
				WINNER_ITEM,
				WINNER_ITEM
		);

		return new BankTagBinding(
				"binding-1",
				"Herbs",
				List.of(slot)
		);
	}
}
