package com.priorityslots.bank;

import com.priorityslots.domain.BankSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

public final class BankSnapshotFactory
{
	public BankSnapshot create(
			ItemContainer itemContainer)
	{
		Objects.requireNonNull(
				itemContainer,
				"itemContainer"
		);

		return create(itemContainer.getItems());
	}

	public BankSnapshot create(Item[] items)
	{
		Objects.requireNonNull(items, "items");

		Map<Integer, Integer> quantitiesByExactItemId =
				new HashMap<>();

		for (Item item : items)
		{
			if (item == null
					|| item.getId() <= 0
					|| item.getQuantity() <= 0)
			{
				continue;
			}

			quantitiesByExactItemId.merge(
					item.getId(),
					item.getQuantity(),
					Math::addExact
			);
		}

		return new BankSnapshot(
				quantitiesByExactItemId
		);
	}
}
