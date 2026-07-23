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
	public void preservesDefinitionOrder()
	{
		PriorityGroup group =
				new PriorityGroup(
						"group-1",
						"Melee gear",
						List.of(
								"helmet-definition",
								"body-definition",
								"legs-definition"
						)
				);

		assertEquals(
				List.of(
						"helmet-definition",
						"body-definition",
						"legs-definition"
				),
				group.getDefinitionIds()
		);
	}

	@Test
	public void copiesDefinitionIdList()
	{
		List<String> definitionIds =
				new ArrayList<>();

		definitionIds.add("helmet-definition");

		PriorityGroup group =
				new PriorityGroup(
						"group-1",
						"Melee gear",
						definitionIds
				);

		definitionIds.add("body-definition");

		assertEquals(
				List.of("helmet-definition"),
				group.getDefinitionIds()
		);
	}

	@Test
	public void renamingPreservesIdentity()
	{
		PriorityGroup original =
				new PriorityGroup(
						"group-1",
						"Old name",
						List.of()
				);

		PriorityGroup renamed =
				original.withName("New name");

		assertEquals(
				original.getId(),
				renamed.getId()
		);
		assertEquals(
				"New name",
				renamed.getName()
		);
		assertNotEquals(
				original.getName(),
				renamed.getName()
		);
	}

	@Test
	public void allowsEmptyDefinitionList()
	{
		PriorityGroup group =
				new PriorityGroup(
						"group-1",
						"Empty draft",
						List.of()
				);

		assertEquals(
				List.of(),
				group.getDefinitionIds()
		);
	}

	@Test
	public void trimsDefinitionIds()
	{
		PriorityGroup group =
				new PriorityGroup(
						"group-1",
						"Melee gear",
						List.of(
								"  helmet-definition  "
						)
				);

		assertEquals(
				List.of("helmet-definition"),
				group.getDefinitionIds()
		);
	}

	@Test
	public void rejectsDuplicateDefinitionIds()
	{
		assertIllegalArgument(() ->
				new PriorityGroup(
						"group-1",
						"Melee gear",
						List.of(
								"helmet-definition",
								"helmet-definition"
						)
				)
		);
	}

	@Test
	public void rejectsDuplicatesAfterTrimming()
	{
		assertIllegalArgument(() ->
				new PriorityGroup(
						"group-1",
						"Melee gear",
						List.of(
								"helmet-definition",
								" helmet-definition "
						)
				)
		);
	}

	@Test
	public void rejectsBlankDefinitionId()
	{
		assertIllegalArgument(() ->
				new PriorityGroup(
						"group-1",
						"Melee gear",
						List.of(" ")
				)
		);
	}

	private static void assertIllegalArgument(
			Runnable action)
	{
		try
		{
			action.run();
			fail(
					"Expected IllegalArgumentException"
			);
		}
		catch (IllegalArgumentException expected)
		{
			// Expected.
		}
	}
}
