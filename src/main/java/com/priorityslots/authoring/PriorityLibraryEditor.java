package com.priorityslots.authoring;

import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityGroup;
import com.priorityslots.domain.PriorityLibraryEntry;
import com.priorityslots.domain.PriorityState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PriorityLibraryEditor
{
	PriorityState move(
		PriorityState state,
		PriorityLibraryEntry entry,
		String targetParentGroupId,
		int targetIndex)
	{
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(entry, "entry");

		if (entry.isGroup())
		{
			validateGroupMove(
				state,
				entry.getTargetId(),
				targetParentGroupId
			);
		}
		else if (!state.definitionsById().containsKey(
			entry.getTargetId()))
		{
			throw new IllegalArgumentException(
				"Unknown definition: "
					+ entry.getTargetId()
			);
		}

		LibraryLists lists = LibraryLists.from(state);

		if (!lists.remove(entry))
		{
			throw new IllegalArgumentException(
				"Library entry is not present: "
					+ entry.getType()
					+ " "
					+ entry.getTargetId()
			);
		}

		List<PriorityLibraryEntry> target =
			lists.entriesForParent(targetParentGroupId);

		validateInsertionIndex(targetIndex, target.size());
		target.add(targetIndex, entry);

		return lists.toState(
			state.getDefinitions(),
			state.getBindings(),
			state.getGroups()
		);
	}

	private static void validateGroupMove(
		PriorityState state,
		String groupId,
		String targetParentGroupId)
	{
		String requiredGroupId = requireNonBlank(
			groupId,
			"groupId"
		);

		if (!state.groupsById().containsKey(requiredGroupId))
		{
			throw new IllegalArgumentException(
				"Unknown group: " + requiredGroupId
			);
		}

		if (targetParentGroupId == null)
		{
			return;
		}

		String requiredTargetParentId = requireNonBlank(
			targetParentGroupId,
			"targetParentGroupId"
		);

		if (requiredGroupId.equals(requiredTargetParentId)
			|| isDescendantGroup(
				state,
				requiredGroupId,
				requiredTargetParentId
			))
		{
			throw new IllegalArgumentException(
				"A group cannot be moved into itself "
					+ "or one of its descendants"
			);
		}
	}

	private static boolean isDescendantGroup(
		PriorityState state,
		String ancestorGroupId,
		String possibleDescendantGroupId)
	{
		PriorityGroup group = state.groupsById().get(
			ancestorGroupId
		);

		for (PriorityLibraryEntry child
			: group.getChildren())
		{
			if (!child.isGroup())
			{
				continue;
			}

			if (child.getTargetId().equals(
				possibleDescendantGroupId))
			{
				return true;
			}

			if (isDescendantGroup(
				state,
				child.getTargetId(),
				possibleDescendantGroupId
			))
			{
				return true;
			}
		}

		return false;
	}

	private static void validateInsertionIndex(
		int targetIndex,
		int listSize)
	{
		if (targetIndex < 0 || targetIndex > listSize)
		{
			throw new IllegalArgumentException(
				"targetIndex must be between 0 and "
					+ listSize
			);
		}
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

	private static final class LibraryLists
	{
		private final List<PriorityLibraryEntry> rootEntries;
		private final Map<String, List<PriorityLibraryEntry>>
			childrenByGroupId;

		private LibraryLists(
			List<PriorityLibraryEntry> rootEntries,
			Map<String, List<PriorityLibraryEntry>>
				childrenByGroupId)
		{
			this.rootEntries = rootEntries;
			this.childrenByGroupId = childrenByGroupId;
		}

		private static LibraryLists from(
			PriorityState state)
		{
			Map<String, List<PriorityLibraryEntry>> children =
				new LinkedHashMap<>();

			for (PriorityGroup group : state.getGroups())
			{
				children.put(
					group.getId(),
					new ArrayList<>(group.getChildren())
				);
			}

			return new LibraryLists(
				new ArrayList<>(state.getRootEntries()),
				children
			);
		}

		private boolean remove(
			PriorityLibraryEntry entry)
		{
			if (rootEntries.remove(entry))
			{
				return true;
			}

			for (List<PriorityLibraryEntry> children
				: childrenByGroupId.values())
			{
				if (children.remove(entry))
				{
					return true;
				}
			}

			return false;
		}

		private List<PriorityLibraryEntry> entriesForParent(
			String parentGroupId)
		{
			if (parentGroupId == null)
			{
				return rootEntries;
			}

			String requiredParentId = requireNonBlank(
				parentGroupId,
				"targetParentGroupId"
			);

			List<PriorityLibraryEntry> children =
				childrenByGroupId.get(requiredParentId);

			if (children == null)
			{
				throw new IllegalArgumentException(
					"Unknown target group: "
						+ requiredParentId
				);
			}

			return children;
		}

		private PriorityState toState(
			List<PriorityDefinition> definitions,
			List<BankTagBinding> bindings,
			List<PriorityGroup> previousGroups)
		{
			List<PriorityGroup> groups = new ArrayList<>();

			for (PriorityGroup group : previousGroups)
			{
				groups.add(
					group.withChildren(
						childrenByGroupId.get(
							group.getId()
						)
					)
				);
			}

			return new PriorityState(
				definitions,
				groups,
				bindings,
				rootEntries
			);
		}
	}
}
