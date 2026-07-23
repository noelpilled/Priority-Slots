package com.priorityslots.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PriorityStateTest
{
	private static final int TEST_ITEM_ID = 1005;

	@Test
	public void emptyStateContainsNoSavedObjects()
	{
		PriorityState state = PriorityState.empty();

		assertTrue(state.getDefinitions().isEmpty());
		assertTrue(state.getGroups().isEmpty());
		assertTrue(state.getViews().isEmpty());

		assertTrue(state.definitionsById().isEmpty());
		assertTrue(state.groupsById().isEmpty());
	}

	@Test
	public void copiesSavedObjectLists()
	{
		PriorityDefinition definition =
				createDefinition("definition-1");

		PriorityGroup group =
				createGroup(
						"group-1",
						definition.getId()
				);

		PriorityView view =
				createView("view-1");

		List<PriorityDefinition> definitions =
				new ArrayList<>();

		List<PriorityGroup> groups =
				new ArrayList<>();

		List<PriorityView> views =
				new ArrayList<>();

		definitions.add(definition);
		groups.add(group);
		views.add(view);

		PriorityState state =
				new PriorityState(
						definitions,
						groups,
						views
				);

		definitions.clear();
		groups.clear();
		views.clear();

		assertEquals(
				List.of(definition),
				state.getDefinitions()
		);
		assertEquals(
				List.of(group),
				state.getGroups()
		);
		assertEquals(
				List.of(view),
				state.getViews()
		);
	}

	@Test
	public void createsDefinitionLookupByStableId()
	{
		PriorityDefinition first =
				createDefinition("definition-1");

		PriorityDefinition second =
				createDefinition("definition-2");

		PriorityState state =
				new PriorityState(
						List.of(first, second),
						List.of(),
						List.of()
				);

		Map<String, PriorityDefinition> definitionsById =
				state.definitionsById();

		assertEquals(2, definitionsById.size());

		assertSame(
				first,
				definitionsById.get("definition-1")
		);
		assertSame(
				second,
				definitionsById.get("definition-2")
		);
	}

	@Test
	public void createsGroupLookupByStableId()
	{
		PriorityGroup first =
				createGroup(
						"group-1",
						"definition-1"
				);

		PriorityGroup second =
				createGroup(
						"group-2",
						"definition-2"
				);

		PriorityState state =
				new PriorityState(
						List.of(),
						List.of(first, second),
						List.of()
				);

		Map<String, PriorityGroup> groupsById =
				state.groupsById();

		assertEquals(2, groupsById.size());

		assertSame(
				first,
				groupsById.get("group-1")
		);
		assertSame(
				second,
				groupsById.get("group-2")
		);
	}

	@Test
	public void rejectsDuplicateDefinitionIds()
	{
		PriorityDefinition first =
				createDefinition("definition-1");

		PriorityDefinition duplicate =
				createDefinition("definition-1");

		assertIllegalArgument(() ->
				new PriorityState(
						List.of(first, duplicate),
						List.of(),
						List.of()
				)
		);
	}

	@Test
	public void rejectsDuplicateGroupIds()
	{
		PriorityGroup first =
				createGroup(
						"group-1",
						"definition-1"
				);

		PriorityGroup duplicate =
				createGroup(
						"group-1",
						"definition-2"
				);

		assertIllegalArgument(() ->
				new PriorityState(
						List.of(),
						List.of(first, duplicate),
						List.of()
				)
		);
	}

	@Test
	public void rejectsDuplicateViewIds()
	{
		PriorityView first =
				createView("view-1");

		PriorityView duplicate =
				createView("view-1");

		assertIllegalArgument(() ->
				new PriorityState(
						List.of(),
						List.of(),
						List.of(first, duplicate)
				)
		);
	}

	@Test
	public void allowsUnresolvedGroupReferences()
	{
		PriorityGroup group =
				createGroup(
						"group-1",
						"missing-definition"
				);

		PriorityState state =
				new PriorityState(
						List.of(),
						List.of(group),
						List.of()
				);

		assertFalse(state.getGroups().isEmpty());

		assertEquals(
				"missing-definition",
				state.getGroups()
						.get(0)
						.getDefinitionIds()
						.get(0)
		);
	}

	@Test
	public void allowsUnresolvedPlacementReferences()
	{
		CellPlacement placement =
				new CellPlacement(
						"cell-1",
						"missing-definition",
						4
				);

		PriorityView view =
				new PriorityView(
						"view-1",
						"Unresolved view",
						List.of(placement)
				);

		PriorityState state =
				new PriorityState(
						List.of(),
						List.of(),
						List.of(view)
				);

		assertFalse(state.getViews().isEmpty());

		assertEquals(
				"missing-definition",
				state.getViews()
						.get(0)
						.getPlacements()
						.get(0)
						.getDefinitionId()
		);
	}

	private static PriorityDefinition createDefinition(
			String id)
	{
		PriorityTier tier =
				new PriorityTier(
						id + "-tier",
						List.of(TEST_ITEM_ID)
				);

		return new PriorityDefinition(
				id,
				id + " name",
				List.of(tier)
		);
	}

	private static PriorityGroup createGroup(
			String id,
			String definitionId)
	{
		return new PriorityGroup(
				id,
				id + " name",
				List.of(definitionId)
		);
	}

	private static PriorityView createView(String id)
	{
		return new PriorityView(
				id,
				id + " name",
				List.of()
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
