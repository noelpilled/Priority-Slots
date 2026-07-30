package com.priorityslots.testing;

import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityGroup;
import com.priorityslots.domain.PriorityLibraryEntry;
import com.priorityslots.domain.PriorityState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PriorityStateFixtures
{
	private PriorityStateFixtures()
	{
	}

	public static PriorityState state(
		List<PriorityDefinition> definitions,
		List<PriorityGroup> groups,
		List<BankTagBinding> bindings)
	{
		Set<String> childGroupIds = new HashSet<>();
		Set<String> childDefinitionIds = new HashSet<>();

		for (PriorityGroup group : groups)
		{
			for (PriorityLibraryEntry child : group.getChildren())
			{
				if (child.isGroup())
				{
					childGroupIds.add(child.getTargetId());
				}
				else
				{
					childDefinitionIds.add(child.getTargetId());
				}
			}
		}

		List<PriorityLibraryEntry> rootEntries = new ArrayList<>();

		for (PriorityGroup group : groups)
		{
			if (!childGroupIds.contains(group.getId()))
			{
				rootEntries.add(
					PriorityLibraryEntry.group(group.getId())
				);
			}
		}

		for (PriorityDefinition definition : definitions)
		{
			if (!childDefinitionIds.contains(definition.getId()))
			{
				rootEntries.add(
					PriorityLibraryEntry.definition(
						definition.getId()
					)
				);
			}
		}

		return new PriorityState(
			definitions,
			groups,
			bindings,
			rootEntries
		);
	}
}
