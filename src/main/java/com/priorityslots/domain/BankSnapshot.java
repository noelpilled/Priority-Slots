package com.priorityslots.domain;

import java.util.Map;
import java.util.Objects;
import lombok.Value;

@Value
public class BankSnapshot
{
	Map<Integer, Integer> quantitiesByExactItemId;

	public BankSnapshot(
			Map<Integer, Integer> quantitiesByExactItemId)
	{
		Objects.requireNonNull(
				quantitiesByExactItemId,
				"quantitiesByExactItemId"
		);

		for (Map.Entry<Integer, Integer> entry
				: quantitiesByExactItemId.entrySet())
		{
			Integer itemId = entry.getKey();
			Integer quantity = entry.getValue();

			if (itemId == null || itemId <= 0)
			{
				throw new IllegalArgumentException(
						"Bank item IDs must be positive"
				);
			}

			if (quantity == null || quantity <= 0)
			{
				throw new IllegalArgumentException(
						"Bank quantities must be positive"
				);
			}
		}

		this.quantitiesByExactItemId =
				Map.copyOf(quantitiesByExactItemId);
	}

	public static BankSnapshot empty()
	{
		return new BankSnapshot(Map.of());
	}

	public boolean contains(int exactItemId)
	{
		return quantitiesByExactItemId.containsKey(
				exactItemId
		);
	}

	public int quantityOf(int exactItemId)
	{
		return quantitiesByExactItemId.getOrDefault(
				exactItemId,
				0
		);
	}

	public int distinctItemCount()
	{
		return quantitiesByExactItemId.size();
	}
}
