package com.priorityslots.banktags;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import lombok.Value;

@Value
public class BankTagLayoutSnapshot
{
	String bankTagName;
	List<Integer> itemIds;

	public BankTagLayoutSnapshot(
			String bankTagName,
			List<Integer> itemIds)
	{
		Objects.requireNonNull(
				bankTagName,
				"bankTagName"
		);

		String trimmedName = bankTagName.trim();

		if (trimmedName.isEmpty())
		{
			throw new IllegalArgumentException(
					"bankTagName must not be blank"
			);
		}

		Objects.requireNonNull(
				itemIds,
				"itemIds"
		);

		for (Integer itemId : itemIds)
		{
			Objects.requireNonNull(
					itemId,
					"itemIds must not contain null"
			);
		}

		this.bankTagName = trimmedName;
		this.itemIds = List.copyOf(itemIds);
	}

	public int size()
	{
		return itemIds.size();
	}

	public OptionalInt itemAt(int index)
	{
		if (index < 0)
		{
			throw new IllegalArgumentException(
					"index must not be negative"
			);
		}

		if (index >= itemIds.size())
		{
			return OptionalInt.empty();
		}

		return OptionalInt.of(itemIds.get(index));
	}
}
