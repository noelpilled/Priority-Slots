package com.priorityslots.authoring;

import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityGroup;
import com.priorityslots.domain.PriorityLibraryEntry;
import com.priorityslots.domain.PriorityState;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PriorityGroupAuthoringRegressionTest
{
	@Test
	public void createsNestedGroupsAtRequestedPositions()
	{
		PrioritySlotAuthoringService service = serviceWithIds(
			"group-root",
			"group-child"
		);

		PrioritySlotAuthoringService.CreateGroupResult root =
			service.createGroup(
				PriorityState.empty(),
				"Gear",
				null,
				0
			);

		PrioritySlotAuthoringService.CreateGroupResult child =
			service.createGroup(
				root.getState(),
				"Melee",
				root.getGroup().getId(),
				0
			);

		assertEquals(
			List.of(PriorityLibraryEntry.group("group-root")),
			child.getState().getRootEntries()
		);
		assertEquals(
			List.of(PriorityLibraryEntry.group("group-child")),
			child.getState().groupsById()
				.get("group-root")
				.getChildren()
		);
	}

	@Test
	public void renamesGroupWithoutChangingIdentityOrChildren()
	{
		PriorityDefinition definition = new PriorityDefinition(
			"definition-herbs",
			"Herbs",
			List.of()
		);
		PriorityGroup group = new PriorityGroup(
			"group-herblore",
			"Old name",
			List.of(PriorityLibraryEntry.definition(
				definition.getId()
			))
		);
		PriorityState state = new PriorityState(
			List.of(definition),
			List.of(group),
			List.of(),
			List.of(PriorityLibraryEntry.group(group.getId()))
		);

		PriorityState renamed = serviceWithIds().renameGroup(
			state,
			group.getId(),
			"Herblore"
		);
		PriorityGroup updated = renamed.groupsById().get(group.getId());

		assertEquals(group.getId(), updated.getId());
		assertEquals("Herblore", updated.getName());
		assertEquals(group.getChildren(), updated.getChildren());
	}

	@Test
	public void deletesEmptyNestedGroupAndPreservesParent()
	{
		PriorityGroup child = new PriorityGroup(
			"group-child",
			"Empty",
			List.of()
		);
		PriorityGroup parent = new PriorityGroup(
			"group-parent",
			"Gear",
			List.of(PriorityLibraryEntry.group(child.getId()))
		);
		PriorityState state = new PriorityState(
			List.of(),
			List.of(parent, child),
			List.of(),
			List.of(PriorityLibraryEntry.group(parent.getId()))
		);

		PriorityState deleted = serviceWithIds().deleteGroup(
			state,
			child.getId()
		);

		assertFalse(deleted.groupsById().containsKey(child.getId()));
		assertTrue(deleted.groupsById().containsKey(parent.getId()));
		assertTrue(
			deleted.groupsById().get(parent.getId())
				.getChildren().isEmpty()
		);
		assertEquals(
			List.of(PriorityLibraryEntry.group(parent.getId())),
			deleted.getRootEntries()
		);
	}

	@Test
	public void rejectsDeletingNonEmptyGroup()
	{
		PriorityDefinition definition = new PriorityDefinition(
			"definition-herbs",
			"Herbs",
			List.of()
		);
		PriorityGroup group = new PriorityGroup(
			"group-herblore",
			"Herblore",
			List.of(PriorityLibraryEntry.definition(
				definition.getId()
			))
		);
		PriorityState state = new PriorityState(
			List.of(definition),
			List.of(group),
			List.of(),
			List.of(PriorityLibraryEntry.group(group.getId()))
		);

		try
		{
			serviceWithIds().deleteGroup(state, group.getId());
			fail("Expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			assertFalse(expected.getMessage().trim().isEmpty());
		}
	}

	private static PrioritySlotAuthoringService serviceWithIds(
		String... ids)
	{
		Queue<String> remainingIds =
			new ArrayDeque<>(List.of(ids));

		return new PrioritySlotAuthoringService(() ->
		{
			if (remainingIds.isEmpty())
			{
				throw new AssertionError("No generated ID available");
			}

			return remainingIds.remove();
		});
	}
}
