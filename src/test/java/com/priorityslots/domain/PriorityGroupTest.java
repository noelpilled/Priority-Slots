package com.priorityslots.domain;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class PriorityGroupTest
{
	@Test
	public void preservesOrderedNestedChildren()
	{
		PriorityGroup group = new PriorityGroup(
			"group-1",
			"Herblore",
			List.of(
				PriorityLibraryEntry.group("group-herbs"),
				PriorityLibraryEntry.definition(
					"definition-potions"
				)
			)
		);

		assertEquals(
			List.of(
				PriorityLibraryEntry.group("group-herbs"),
				PriorityLibraryEntry.definition(
					"definition-potions"
				)
			),
			group.getChildren()
		);
	}

	@Test
	public void copiesChildList()
	{
		List<PriorityLibraryEntry> children =
			new ArrayList<>();
		children.add(
			PriorityLibraryEntry.definition("definition-1")
		);

		PriorityGroup group = new PriorityGroup(
			"group-1",
			"Herblore",
			children
		);

		children.clear();

		assertEquals(1, group.getChildren().size());
	}

	@Test
	public void renamingPreservesIdentityAndChildren()
	{
		PriorityGroup original = new PriorityGroup(
			"group-1",
			"Old name",
			List.of(
				PriorityLibraryEntry.definition("definition-1")
			)
		);

		PriorityGroup renamed = original.withName("New name");

		assertEquals(original.getId(), renamed.getId());
		assertEquals(original.getChildren(), renamed.getChildren());
		assertEquals("New name", renamed.getName());
		assertNotEquals(original.getName(), renamed.getName());
	}

	@Test
	public void rejectsDuplicateChildren()
	{
		PriorityLibraryEntry child =
			PriorityLibraryEntry.definition("definition-1");

		assertIllegalArgument(() ->
			new PriorityGroup(
				"group-1",
				"Herblore",
				List.of(child, child)
			)
		);
	}

	@Test
	public void allowsEmptyChildren()
	{
		PriorityGroup group = new PriorityGroup(
			"group-1",
			"Empty group",
			List.of()
		);

		assertEquals(List.of(), group.getChildren());
	}

	private static void assertIllegalArgument(
		Runnable action)
	{
		try
		{
			action.run();
			fail("Expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// Expected.
		}
	}
}
