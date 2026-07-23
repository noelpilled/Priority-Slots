package com.priorityslots.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

		Set<String> cellIds = new HashSet<>();
		Set<Integer> indices = new HashSet<>();

		for (CellPlacement placement : placements)
		{
			Objects.requireNonNull(
					placement,
					"placements must not contain null"
			);

			if (!cellIds.add(placement.getCellId()))
			{
				throw new IllegalArgumentException(
						"Duplicate cell ID: "
								+ placement.getCellId()
				);
			}

			if (!indices.add(placement.getIndex()))
			{
				throw new IllegalArgumentException(
						"Duplicate placement index: "
								+ placement.getIndex()
				);
			}
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
