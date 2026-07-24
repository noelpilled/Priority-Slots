package com.priorityslots.domain;

import java.util.Objects;
import lombok.Value;

@Value
public class PriorityLibraryEntry
{
	public enum Type
	{
		GROUP,
		DEFINITION
	}

	Type type;
	String targetId;

	public PriorityLibraryEntry(
		Type type,
		String targetId)
	{
		this.type = Objects.requireNonNull(
			type,
			"type"
		);

		this.targetId = requireNonBlank(
			targetId,
			"targetId"
		);
	}

	public static PriorityLibraryEntry group(
		String groupId)
	{
		return new PriorityLibraryEntry(
			Type.GROUP,
			groupId
		);
	}

	public static PriorityLibraryEntry definition(
		String definitionId)
	{
		return new PriorityLibraryEntry(
			Type.DEFINITION,
			definitionId
		);
	}

	public boolean isGroup()
	{
		return type == Type.GROUP;
	}

	public boolean isDefinition()
	{
		return type == Type.DEFINITION;
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
