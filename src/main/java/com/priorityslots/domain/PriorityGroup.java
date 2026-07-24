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
	List<PriorityLibraryEntry> children;

	public PriorityGroup(
		String id,
		String name,
		List<PriorityLibraryEntry> children)
	{
		this.id = requireNonBlank(id, "id");
		this.name = requireNonBlank(name, "name");

		Objects.requireNonNull(children, "children");

		List<PriorityLibraryEntry> copiedChildren =
			new ArrayList<>();

		Set<PriorityLibraryEntry> seenChildren =
			new HashSet<>();

		for (PriorityLibraryEntry child : children)
		{
			PriorityLibraryEntry requiredChild =
				Objects.requireNonNull(
					child,
					"children must not contain null"
				);

			if (!seenChildren.add(requiredChild))
			{
				throw new IllegalArgumentException(
					"Duplicate group child: "
						+ requiredChild.getType()
						+ " "
						+ requiredChild.getTargetId()
				);
			}

			copiedChildren.add(requiredChild);
		}

		this.children = List.copyOf(copiedChildren);
	}

	public static PriorityGroup create(
		String name,
		List<PriorityLibraryEntry> children)
	{
		return new PriorityGroup(
			UUID.randomUUID().toString(),
			name,
			children
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
