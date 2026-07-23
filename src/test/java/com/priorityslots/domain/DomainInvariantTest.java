package com.priorityslots.domain;

import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.fail;

public class DomainInvariantTest
{
	private static final int HIGH_PRIORITY_ITEM = 1005;
	private static final int LOW_PRIORITY_ITEM = 1003;

	@Test
	public void bankSnapshotRejectsZeroQuantity()
	{
		assertIllegalArgument(() ->
				new BankSnapshot(
						Map.of(
								HIGH_PRIORITY_ITEM,
								0
						)
				)
		);
	}

	@Test
	public void definitionRejectsDuplicateTierIds()
	{
		PriorityTier firstTier =
				new PriorityTier(
						"tier-1",
						List.of(HIGH_PRIORITY_ITEM)
				);

		PriorityTier secondTier =
				new PriorityTier(
						"tier-1",
						List.of(LOW_PRIORITY_ITEM)
				);

		assertIllegalArgument(() ->
				new PriorityDefinition(
						"definition-1",
						"Duplicate tiers",
						List.of(
								firstTier,
								secondTier
						)
				)
		);
	}

	@Test
	public void definitionRejectsDuplicateItemsAcrossTiers()
	{
		PriorityTier firstTier =
				new PriorityTier(
						"tier-1",
						List.of(
								HIGH_PRIORITY_ITEM,
								LOW_PRIORITY_ITEM
						)
				);

		PriorityTier secondTier =
				new PriorityTier(
						"tier-2",
						List.of(LOW_PRIORITY_ITEM)
				);

		assertIllegalArgument(() ->
				new PriorityDefinition(
						"definition-1",
						"Duplicate items",
						List.of(
								firstTier,
								secondTier
						)
				)
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
