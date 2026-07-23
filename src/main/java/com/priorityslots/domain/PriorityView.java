package com.priorityslots.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Value;
import lombok.With;

@Value
public class PriorityView
{
	String id;

	@With
	String name;

	@With
	List<CellPlacement> placements;

	public PriorityView(
			String id,
			String name,
			List<CellPlacement> placements)
	{
		this.id = requireNonBlank(id, "id");
		this.name = requireNonBlank(name, "name");

		Objects.requireNonNull(placements, "placements");

		for (CellPlacement placement : placements)
		{
			Objects.requireNonNull(
					placement,
					"placements must not contain null"
			);
		}

		this.placements = List.copyOf(placements);
	}

	public static PriorityView create(
			String name,
			List<CellPlacement> placements)
	{
		return new PriorityView(
				UUID.randomUUID().toString(),
				name,
				placements
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
