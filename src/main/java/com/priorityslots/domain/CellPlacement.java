package com.priorityslots.domain;

import java.util.Objects;
import java.util.UUID;
import lombok.Value;
import lombok.With;

@Value
public class CellPlacement
{
	String cellId;
	String definitionId;

	@With
	int index;

	public CellPlacement(
			String cellId,
			String definitionId,
			int index)
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
	}

	public static CellPlacement create(
			String definitionId,
			int index)
	{
		return new CellPlacement(
				UUID.randomUUID().toString(),
				definitionId,
				index
		);
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
