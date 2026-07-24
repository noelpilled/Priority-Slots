package com.priorityslots.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Value;
import lombok.With;
import net.runelite.client.util.Text;

@Value
public class PriorityState
{
	@With
	List<PriorityDefinition> definitions;

	@With
	List<PriorityGroup> groups;

	@With
	List<BankTagBinding> bindings;

	@With
	List<PriorityLibraryEntry> rootEntries;

	public PriorityState(
		List<PriorityDefinition> definitions,
		List<PriorityGroup> groups,
		List<BankTagBinding> bindings)
	{
		this(
			definitions,
			groups,
			bindings,
			deriveRootEntries(definitions, groups)
		);
	}

	public PriorityState(
		List<PriorityDefinition> definitions,
		List<PriorityGroup> groups,
		List<BankTagBinding> bindings,
		List<PriorityLibraryEntry> rootEntries)
	{
		Objects.requireNonNull(
			definitions,
			"definitions"
		);
		Objects.requireNonNull(groups, "groups");
		Objects.requireNonNull(bindings, "bindings");
		Objects.requireNonNull(
			rootEntries,
			"rootEntries"
		);

		validateDefinitions(definitions);
		validateGroups(groups);
		validateBindings(bindings);
		validateLibrary(
			definitions,
			groups,
			rootEntries
		);

		this.definitions = List.copyOf(definitions);
		this.groups = List.copyOf(groups);
		this.bindings = List.copyOf(bindings);
		this.rootEntries = List.copyOf(rootEntries);
	}

	public static PriorityState empty()
	{
		return new PriorityState(
			Collections.emptyList(),
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

	private static List<PriorityLibraryEntry>
	deriveRootEntries(
		List<PriorityDefinition> definitions,
		List<PriorityGroup> groups)
	{
		Objects.requireNonNull(
			definitions,
			"definitions"
		);
		Objects.requireNonNull(groups, "groups");

		Set<String> childGroupIds = new HashSet<>();
		Set<String> childDefinitionIds = new HashSet<>();

		for (PriorityGroup group : groups)
		{
			if (group == null)
			{
				continue;
			}

			for (PriorityLibraryEntry child
				: group.getChildren())
			{
				if (child == null)
				{
					continue;
				}

				if (child.isGroup())
				{
					childGroupIds.add(
						child.getTargetId()
					);
				}
				else
				{
					childDefinitionIds.add(
						child.getTargetId()
					);
				}
			}
		}

		List<PriorityLibraryEntry> result =
			new ArrayList<>();

		for (PriorityGroup group : groups)
		{
			if (group != null
				&& !childGroupIds.contains(group.getId()))
			{
				result.add(
					PriorityLibraryEntry.group(
						group.getId()
					)
				);
			}
		}

		for (PriorityDefinition definition
			: definitions)
		{
			if (definition != null
				&& !childDefinitionIds.contains(
					definition.getId()))
			{
				result.add(
					PriorityLibraryEntry.definition(
						definition.getId()
					)
				);
			}
		}

		return List.copyOf(result);
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

	private static void validateLibrary(
		List<PriorityDefinition> definitions,
		List<PriorityGroup> groups,
		List<PriorityLibraryEntry> rootEntries)
	{
		Map<String, PriorityDefinition> definitionsById =
			new HashMap<>();

		for (PriorityDefinition definition
			: definitions)
		{
			definitionsById.put(
				definition.getId(),
				definition
			);
		}

		Map<String, PriorityGroup> groupsById =
			new HashMap<>();

		for (PriorityGroup group : groups)
		{
			groupsById.put(group.getId(), group);
		}

		Set<String> placedDefinitionIds =
			new HashSet<>();

		Set<String> placedGroupIds = new HashSet<>();
		Set<String> groupPath = new HashSet<>();

		validateLibraryEntries(
			rootEntries,
			definitionsById,
			groupsById,
			placedDefinitionIds,
			placedGroupIds,
			groupPath
		);

		if (placedDefinitionIds.size()
			!= definitionsById.size())
		{
			Set<String> missing =
				new HashSet<>(definitionsById.keySet());

			missing.removeAll(placedDefinitionIds);

			throw new IllegalArgumentException(
				"Definitions missing from library: "
					+ missing
			);
		}

		if (placedGroupIds.size() != groupsById.size())
		{
			Set<String> missing =
				new HashSet<>(groupsById.keySet());

			missing.removeAll(placedGroupIds);

			throw new IllegalArgumentException(
				"Groups missing from library: " + missing
			);
		}
	}

	private static void validateLibraryEntries(
		List<PriorityLibraryEntry> entries,
		Map<String, PriorityDefinition> definitionsById,
		Map<String, PriorityGroup> groupsById,
		Set<String> placedDefinitionIds,
		Set<String> placedGroupIds,
		Set<String> groupPath)
	{
		Objects.requireNonNull(
			entries,
			"library entries"
		);

		for (PriorityLibraryEntry entry : entries)
		{
			PriorityLibraryEntry requiredEntry =
				Objects.requireNonNull(
					entry,
					"library entries must not contain null"
				);

			String targetId =
				requiredEntry.getTargetId();

			if (requiredEntry.isDefinition())
			{
				if (!definitionsById.containsKey(targetId))
				{
					throw new IllegalArgumentException(
						"Unknown library definition: "
							+ targetId
					);
				}

				if (!placedDefinitionIds.add(targetId))
				{
					throw new IllegalArgumentException(
						"Definition appears more than once "
							+ "in library: "
							+ targetId
					);
				}

				continue;
			}

			PriorityGroup group = groupsById.get(targetId);

			if (group == null)
			{
				throw new IllegalArgumentException(
					"Unknown library group: " + targetId
				);
			}

			if (!groupPath.add(targetId))
			{
				throw new IllegalArgumentException(
					"Nested group cycle: " + targetId
				);
			}

			if (!placedGroupIds.add(targetId))
			{
				throw new IllegalArgumentException(
					"Group appears more than once "
						+ "in library: "
						+ targetId
				);
			}

			validateLibraryEntries(
				group.getChildren(),
				definitionsById,
				groupsById,
				placedDefinitionIds,
				placedGroupIds,
				groupPath
			);

			groupPath.remove(targetId);
		}
	}

	private static void validateBindings(
		List<BankTagBinding> bindings)
	{
		Set<String> bindingIds = new HashSet<>();

		Set<String> standardizedBankTagNames =
			new HashSet<>();

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

			String standardizedBankTagName =
				Text.standardize(
					binding.getBankTagName()
				);

			if (!standardizedBankTagNames.add(
				standardizedBankTagName))
			{
				throw new IllegalArgumentException(
					"Duplicate Bank Tag binding: "
						+ binding.getBankTagName()
				);
			}
		}
	}
}
