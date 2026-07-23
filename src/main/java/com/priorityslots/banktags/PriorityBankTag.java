package com.priorityslots.banktags;

import java.util.Objects;
import java.util.Set;
import net.runelite.client.plugins.banktags.BankTag;

final class PriorityBankTag implements BankTag
{
	private volatile Set<Integer> itemIds = Set.of();

	public void replaceItems(Set<Integer> itemIds)
	{
		Objects.requireNonNull(itemIds, "itemIds");

		for (Integer itemId : itemIds)
		{
			if (itemId == null || itemId <= 0)
			{
				throw new IllegalArgumentException(
						"itemIds must contain only "
								+ "positive item IDs"
				);
			}
		}

		this.itemIds = Set.copyOf(itemIds);
	}

	@Override
	public boolean contains(int itemId)
	{
		return itemIds.contains(itemId);
	}
}
