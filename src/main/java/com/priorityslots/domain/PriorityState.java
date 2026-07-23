package com.priorityslots.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Value;
import lombok.With;

@Value
public class PriorityState
{
	@With
	List<PriorityDefinition> definitions;

	@With
	List<PriorityGroup> groups;

	@With
	List<BankTagBinding> bindings;

	public PriorityState(
			List<PriorityDefinition> definitions,
			List<PriorityGroup> groups,
			List<BankTagBinding> bindings)
	{
		Objects.requireNonNull(
				definitions,
				"definitions"
		);
		Objects.requireNonNull(
				groups,
				"groups"
		);
		Objects.requireNonNull(
				bindings,
				"bindings"
		);

		validateDefinitions(definitions);
		validateGroups(groups);
		validateBindings(bindings);

		this.definitions = List.copyOf(definitions);
		this.groups = List.copyOf(groups);
		this.bindings = List.copyOf(bindings);
	}

	public static PriorityState empty()
	{
		return new PriorityState(
				Collections.emptyList(),
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

	public Map<String, PriorityGroup> groupsById()
	{
		Map<String, PriorityGroup> result =
				new LinkedHashMap<>();

		for (PriorityGroup group : groups)
		{
			result.put(group.getId(), group);
		}

		return Collections.unmodifiableMap(result);
	}

	public Map<String, BankTagBinding> bindingsById()
	{
		Map<String, BankTagBinding> result =
				new LinkedHashMap<>();

		for (BankTagBinding binding : bindings)
		{
			result.put(binding.getId(), binding);
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

	private static void validateGroups(
			List<PriorityGroup> groups)
	{
		Set<String> groupIds = new HashSet<>();

		for (PriorityGroup group : groups)
		{
			Objects.requireNonNull(
					group,
					"groups must not contain null"
			);

			if (!groupIds.add(group.getId()))
			{
				throw new IllegalArgumentException(
						"Duplicate group ID: "
								+ group.getId()
				);
			}
		}
	}

	private static void validateBindings(
			List<BankTagBinding> bindings)
	{
		Set<String> bindingIds = new HashSet<>();

		for (BankTagBinding binding : bindings)
		{
			Objects.requireNonNull(
					binding,
					"bindings must not contain null"
			);

			if (!bindingIds.add(binding.getId()))
			{
				throw new IllegalArgumentException(
						"Duplicate binding ID: "
								+ binding.getId()
				);
			}
		}
	}
}
