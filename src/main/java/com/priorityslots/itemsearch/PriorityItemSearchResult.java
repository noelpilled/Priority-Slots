package com.priorityslots.itemsearch;

import java.util.Objects;

public final class PriorityItemSearchResult
{
	private final int exactItemId;
	private final String name;

	public PriorityItemSearchResult(
		int exactItemId,
		String name)
	{
		if (exactItemId <= 0)
		{
			throw new IllegalArgumentException(
				"exactItemId must be positive"
			);
		}

		String requiredName = Objects.requireNonNull(
			name,
			"name"
		).trim();

		if (requiredName.isEmpty())
		{
			throw new IllegalArgumentException(
				"name must not be blank"
			);
		}

		this.exactItemId = exactItemId;
		this.name = requiredName;
	}

	public int getExactItemId()
	{
		return exactItemId;
	}

	public String getName()
	{
		return name;
	}

	@Override
	public String toString()
	{
		return name + " [ID " + exactItemId + "]";
	}

	@Override
	public boolean equals(Object object)
	{
		if (this == object)
		{
			return true;
		}

		if (!(object instanceof PriorityItemSearchResult))
		{
			return false;
		}

		PriorityItemSearchResult other =
			(PriorityItemSearchResult) object;

		return exactItemId == other.exactItemId
			&& name.equals(other.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(exactItemId, name);
	}
}
