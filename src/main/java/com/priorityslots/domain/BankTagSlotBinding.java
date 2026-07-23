package com.priorityslots.domain;

import java.util.Objects;
import lombok.Value;
import lombok.With;

@Value
public class BankTagSlotBinding
{
	CellPlacement placement;
	int fallbackExactItemId;

	@With
	int lastProjectedExactItemId;

	public BankTagSlotBinding(
			CellPlacement placement,
			int fallbackExactItemId,
			int lastProjectedExactItemId)
	{
		this.placement = Objects.requireNonNull(
				placement,
				"placement"
		);

		if (fallbackExactItemId <= 0)
		{
			throw new IllegalArgumentException(
					"fallbackExactItemId must be positive"
			);
		}

		if (lastProjectedExactItemId <= 0)
		{
			throw new IllegalArgumentException(
					"lastProjectedExactItemId "
							+ "must be positive"
			);
		}

		this.fallbackExactItemId =
				fallbackExactItemId;

		this.lastProjectedExactItemId =
				lastProjectedExactItemId;
	}

	public static BankTagSlotBinding create(
			CellPlacement placement,
			int fallbackExactItemId)
	{
		return new BankTagSlotBinding(
				placement,
				fallbackExactItemId,
				fallbackExactItemId
		);
	}

	public boolean matchesLayoutItem(
			int currentExactItemId)
	{
		return lastProjectedExactItemId
				== currentExactItemId;
	}
}
