package com.priorityslots.domain;

import java.util.Objects;
import lombok.Value;

@Value
public class SlotResolution
{
	public enum State
	{
		OWNED,
		GHOST,
		UNRESOLVED
	}

	String cellId;
	String definitionId;
	int index;
	State state;
	int exactItemId;

	private SlotResolution(
			String cellId,
			String definitionId,
			int index,
			State state,
			int exactItemId)
	{
		this.cellId = requireNonBlank(cellId, "cellId");
		this.definitionId = requireNonBlank(
				definitionId,
				"definitionId"
		);

		if (index < 0)
		{
			throw new IllegalArgumentException(
					"index must not be negative"
			);
		}

		this.index = index;
		this.state = Objects.requireNonNull(
				state,
				"state"
		);

		if (state == State.UNRESOLVED)
		{
			if (exactItemId != -1)
			{
				throw new IllegalArgumentException(
						"UNRESOLVED must use item ID -1"
				);
			}
		}
		else if (exactItemId <= 0)
		{
			throw new IllegalArgumentException(
					"OWNED and GHOST require "
							+ "a positive item ID"
			);
		}

		this.exactItemId = exactItemId;
	}

	public static SlotResolution owned(
			String cellId,
			String definitionId,
			int index,
			int exactItemId)
	{
		return new SlotResolution(
				cellId,
				definitionId,
				index,
				State.OWNED,
				exactItemId
		);
	}

	public static SlotResolution ghost(
			String cellId,
			String definitionId,
			int index,
			int exactItemId)
	{
		return new SlotResolution(
				cellId,
				definitionId,
				index,
				State.GHOST,
				exactItemId
		);
	}

	public static SlotResolution unresolved(
			String cellId,
			String definitionId,
			int index)
	{
		return new SlotResolution(
				cellId,
				definitionId,
				index,
				State.UNRESOLVED,
				-1
		);
	}

	public boolean isOwned()
	{
		return state == State.OWNED;
	}

	private static String requireNonBlank(
			String value,
			String fieldName)
	{
		Objects.requireNonNull(value, fieldName);

		String trimmed = value.trim();
		if (trimmed.isEmpty())
		{
			throw new IllegalArgumentException(
					fieldName + " must not be blank"
			);
		}

		return trimmed;
	}
}
