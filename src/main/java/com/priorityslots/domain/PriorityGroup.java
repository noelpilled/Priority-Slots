package com.priorityslots.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Value;
import lombok.With;

@Value
public class PriorityGroup
{
	String id;

	@With
	String name;

	@With
	List<String> definitionIds;

	public PriorityGroup(
			String id,
			String name,
			List<String> definitionIds)
	{
		this.id = requireNonBlank(id, "id");
		this.name = requireNonBlank(name, "name");

		Objects.requireNonNull(
				definitionIds,
				"definitionIds"
		);

		List<String> normalizedDefinitionIds =
				new ArrayList<>();

		Set<String> seenDefinitionIds =
				new HashSet<>();

		for (String definitionId : definitionIds)
		{
			String normalizedDefinitionId =
					requireNonBlank(
							definitionId,
							"definitionIds entry"
					);

			if (!seenDefinitionIds.add(
					normalizedDefinitionId))
			{
				throw new IllegalArgumentException(
						"Duplicate definition ID: "
								+ normalizedDefinitionId
				);
			}

			normalizedDefinitionIds.add(
					normalizedDefinitionId
			);
		}

		this.definitionIds =
				List.copyOf(normalizedDefinitionIds);
	}

	public static PriorityGroup create(
			String name,
			List<String> definitionIds)
	{
		return new PriorityGroup(
				UUID.randomUUID().toString(),
				name,
				definitionIds
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
