package com.priorityslots.ui;

import java.util.List;
import java.util.Objects;

final class PriorityTreeDropPlanner
{
	private PriorityTreeDropPlanner()
	{
	}


	static int requestedInsertionIndex(
		int pointerY,
		List<Integer> rowMidpoints)
	{
		Objects.requireNonNull(
			rowMidpoints,
			"rowMidpoints"
		);

		for (int index = 0;
		     index < rowMidpoints.size();
		     index++)
		{
			Integer midpoint = rowMidpoints.get(index);

			if (midpoint == null)
			{
				throw new IllegalArgumentException(
					"rowMidpoints must not contain null"
				);
			}

			if (pointerY < midpoint)
			{
				return index;
			}
		}

		return rowMidpoints.size();
	}


	static int normalizeInsertionIndex(
		String sourceParentId,
		int sourceIndex,
		String targetParentId,
		int requestedIndex,
		int targetSizeBeforeMove)
	{
		if (sourceIndex < 0)
		{
			throw new IllegalArgumentException(
				"sourceIndex must not be negative"
			);
		}

		if (requestedIndex < 0
			|| requestedIndex > targetSizeBeforeMove)
		{
			throw new IllegalArgumentException(
				"requestedIndex is outside the target list"
			);
		}

		if (targetSizeBeforeMove < 0)
		{
			throw new IllegalArgumentException(
				"targetSizeBeforeMove must not be negative"
			);
		}

		boolean sameParent = Objects.equals(
			sourceParentId,
			targetParentId
		);

		int targetSizeAfterRemoval =
			targetSizeBeforeMove - (sameParent ? 1 : 0);

		if (targetSizeAfterRemoval < 0)
		{
			throw new IllegalArgumentException(
				"source is not present in the target list"
			);
		}

		int normalizedIndex = requestedIndex;

		if (sameParent && sourceIndex < requestedIndex)
		{
			normalizedIndex--;
		}

		if (normalizedIndex < 0
			|| normalizedIndex > targetSizeAfterRemoval)
		{
			throw new IllegalArgumentException(
				"normalized index is outside the target list"
			);
		}

		return normalizedIndex;
	}
}
