package com.priorityslots.ui;

import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

final class PriorityDefinitionPresentation
{
	private PriorityDefinitionPresentation()
	{
	}

	static String displayName(
		PriorityDefinition definition,
		IntFunction<String> itemName)
	{
		Objects.requireNonNull(definition, "definition");
		Objects.requireNonNull(itemName, "itemName");

		String savedName = definition.getName();

		if (!savedName.startsWith("MVP "))
		{
			return savedName;
		}

		int primaryItemId = primaryItemId(definition);

		if (primaryItemId <= 0)
		{
			return "Priority definition";
		}

		return itemName.apply(primaryItemId) + " priority";
	}

	static List<String> candidateNames(
		PriorityDefinition definition,
		IntFunction<String> itemName)
	{
		Objects.requireNonNull(definition, "definition");
		Objects.requireNonNull(itemName, "itemName");

		List<String> result = new ArrayList<>();

		for (PriorityTier tier : definition.getTiers())
		{
			if (!tier.getExactItemIds().isEmpty())
			{
				result.add(itemName.apply(
					tier.getExactItemIds().get(0)
				));
			}
		}

		return List.copyOf(result);
	}

	static List<String> previewLines(
		PriorityDefinition definition,
		IntFunction<String> itemName,
		int maximumItems)
	{
		if (maximumItems <= 0)
		{
			throw new IllegalArgumentException(
				"maximumItems must be positive"
			);
		}

		List<String> names = candidateNames(
			definition,
			itemName
		);

		if (names.isEmpty())
		{
			return List.of("No priority items");
		}

		List<String> result = new ArrayList<>();
		int limit = Math.min(names.size(), maximumItems);

		for (int index = 0; index < limit; index++)
		{
			result.add(
				(index + 1) + ". " + names.get(index)
			);
		}

		if (names.size() > limit)
		{
			result.add(
				"… and " + (names.size() - limit) + " more"
			);
		}

		return List.copyOf(result);
	}

	private static int primaryItemId(
		PriorityDefinition definition)
	{
		for (PriorityTier tier : definition.getTiers())
		{
			if (!tier.getExactItemIds().isEmpty())
			{
				return tier.getExactItemIds().get(0);
			}
		}

		return -1;
	}
}
