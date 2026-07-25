package com.priorityslots.ui;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PriorityTreeDropPlannerTest
{

	@Test
	public void pointsBeforeFirstRow()
	{
		assertEquals(
			0,
			PriorityTreeDropPlanner.requestedInsertionIndex(
				5,
				List.of(20, 60, 100)
			)
		);
	}

	@Test
	public void pointsBetweenRows()
	{
		assertEquals(
			1,
			PriorityTreeDropPlanner.requestedInsertionIndex(
				40,
				List.of(20, 60, 100)
			)
		);
	}

	@Test
	public void pointsAfterLastRow()
	{
		assertEquals(
			3,
			PriorityTreeDropPlanner.requestedInsertionIndex(
				120,
				List.of(20, 60, 100)
			)
		);
	}

	@Test
	public void movesDownWithinSameParent()
	{
		assertEquals(
			1,
			PriorityTreeDropPlanner.normalizeInsertionIndex(
				"parent",
				0,
				"parent",
				2,
				3
			)
		);
	}

	@Test
	public void movesUpWithinSameParent()
	{
		assertEquals(
			0,
			PriorityTreeDropPlanner.normalizeInsertionIndex(
				"parent",
				2,
				"parent",
				0,
				3
			)
		);
	}

	@Test
	public void appendsWithinSameParent()
	{
		assertEquals(
			2,
			PriorityTreeDropPlanner.normalizeInsertionIndex(
				"parent",
				0,
				"parent",
				3,
				3
			)
		);
	}

	@Test
	public void movesBetweenParentsWithoutOffset()
	{
		assertEquals(
			2,
			PriorityTreeDropPlanner.normalizeInsertionIndex(
				"source",
				0,
				"target",
				2,
				2
			)
		);
	}

	@Test
	public void rejectsRequestedIndexPastTargetEnd()
	{
		assertIllegalArgument(() ->
			PriorityTreeDropPlanner.normalizeInsertionIndex(
				"source",
				0,
				"target",
				3,
				2
			)
		);
	}

	private static void assertIllegalArgument(
		Runnable action)
	{
		try
		{
			action.run();
			fail("Expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// Expected.
		}
	}
}
