package com.priorityslots.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import lombok.Value;
import lombok.With;

@Value
public class PriorityState
{
	@With
	List<PriorityDefinition> definitions;

	@With
	List<PriorityView> views;

	public PriorityState(
			List<PriorityDefinition> definitions,
			List<PriorityView> views)
	{
		Objects.requireNonNull(
				definitions,
				"definitions"
		);
		Objects.requireNonNull(
				views,
				"views"
		);

		validateDefinitions(definitions);
		validateViews(views);

		this.definitions = List.copyOf(definitions);
		this.views = List.copyOf(views);
	}

	public static PriorityState empty()
	{
		return new PriorityState(
				Collections.emptyList(),
				Collections.emptyList()
		);
	}

	public Map<String, PriorityDefinition>
	definitionsById()
	{
		Map<String, PriorityDefinition> result =
				new LinkedHashMap<>();

		for (PriorityDefinition definition
				: definitions)
		{
			result.put(
					definition.getId(),
					definition
			);
		}

		return Collections.unmodifiableMap(result);
	}

	private static void validateDefinitions(
			List<PriorityDefinition> definitions)
	{
		Set<String> definitionIds = new HashSet<>();

		for (PriorityDefinition definition
				: definitions)
		{
			Objects.requireNonNull(
					definition,
					"definitions must not contain null"
			);

			if (!definitionIds.add(definition.getId()))
			{
				throw new IllegalArgumentException(
						"Duplicate definition ID: "
								+ definition.getId()
				);
			}
		}
	}

	private static void validateViews(
			List<PriorityView> views)
	{
		Set<String> viewIds = new HashSet<>();

		for (PriorityView view : views)
		{
			Objects.requireNonNull(
					view,
					"views must not contain null"
			);

			if (!viewIds.add(view.getId()))
			{
				throw new IllegalArgumentException(
						"Duplicate view ID: "
								+ view.getId()
				);
			}
		}
	}
}
