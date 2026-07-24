package com.priorityslots.domain;

import java.util.ArrayList;
import java.util.List;
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
		assertTrue(state.getBindings().isEmpty());
		assertTrue(state.getRootEntries().isEmpty());
	}

	@Test
	public void copiesAllSavedObjectLists()
	{
		PriorityDefinition definition =
			createDefinition("definition-1");
		PriorityGroup group = new PriorityGroup(
			"group-1",
			"Group",
			List.of(
				PriorityLibraryEntry.definition(
					definition.getId()
				)
			)
		);
		BankTagBinding binding = createBinding("binding-1");

		List<PriorityDefinition> definitions =
			new ArrayList<>(List.of(definition));
		List<PriorityGroup> groups =
			new ArrayList<>(List.of(group));
		List<BankTagBinding> bindings =
			new ArrayList<>(List.of(binding));
		List<PriorityLibraryEntry> roots =
			new ArrayList<>(List.of(
				PriorityLibraryEntry.group(group.getId())
			));

		PriorityState state = new PriorityState(
			definitions,
			groups,
			bindings,
			roots
		);

		definitions.clear();
		groups.clear();
		bindings.clear();
		roots.clear();

		assertEquals(List.of(definition), state.getDefinitions());
		assertEquals(List.of(group), state.getGroups());
		assertEquals(List.of(binding), state.getBindings());
		assertEquals(
			List.of(PriorityLibraryEntry.group(group.getId())),
			state.getRootEntries()
		);
	}

	@Test
	public void threeArgumentConstructorDerivesRootTree()
	{
		PriorityDefinition grouped =
			createDefinition("definition-grouped");
		PriorityDefinition ungrouped =
			createDefinition("definition-root");
		PriorityGroup group = new PriorityGroup(
			"group-1",
			"Group",
			List.of(
				PriorityLibraryEntry.definition(grouped.getId())
			)
		);

		PriorityState state = new PriorityState(
			List.of(grouped, ungrouped),
			List.of(group),
			List.of()
		);

		assertEquals(
			List.of(
				PriorityLibraryEntry.group(group.getId()),
				PriorityLibraryEntry.definition(
					ungrouped.getId()
				)
			),
			state.getRootEntries()
		);
	}

	@Test
	public void supportsNestedGroups()
	{
		PriorityDefinition definition =
			createDefinition("definition-1");
		PriorityGroup child = new PriorityGroup(
			"group-child",
			"Child",
			List.of(
				PriorityLibraryEntry.definition(
					definition.getId()
				)
			)
		);
		PriorityGroup parent = new PriorityGroup(
			"group-parent",
			"Parent",
			List.of(PriorityLibraryEntry.group(child.getId()))
		);

		PriorityState state = new PriorityState(
			List.of(definition),
			List.of(parent, child),
			List.of(),
			List.of(PriorityLibraryEntry.group(parent.getId()))
		);

		assertSame(parent, state.groupsById().get(parent.getId()));
		assertSame(child, state.groupsById().get(child.getId()));
	}

	@Test
	public void rejectsDefinitionMissingFromLibrary()
	{
		PriorityDefinition definition =
			createDefinition("definition-1");

		assertIllegalArgument(() ->
			new PriorityState(
				List.of(definition),
				List.of(),
				List.of(),
				List.of()
			)
		);
	}

	@Test
	public void rejectsUnknownLibraryReference()
	{
		assertIllegalArgument(() ->
			new PriorityState(
				List.of(),
				List.of(),
				List.of(),
				List.of(
					PriorityLibraryEntry.definition(
						"missing-definition"
					)
				)
			)
		);
	}

	@Test
	public void rejectsDuplicateLibraryPlacement()
	{
		PriorityDefinition definition =
			createDefinition("definition-1");

		assertIllegalArgument(() ->
			new PriorityState(
				List.of(definition),
				List.of(),
				List.of(),
				List.of(
					PriorityLibraryEntry.definition(
						definition.getId()
					),
					PriorityLibraryEntry.definition(
						definition.getId()
					)
				)
			)
		);
	}

	@Test
	public void rejectsNestedGroupCycle()
	{
		PriorityGroup first = new PriorityGroup(
			"group-1",
			"First",
			List.of(PriorityLibraryEntry.group("group-2"))
		);
		PriorityGroup second = new PriorityGroup(
			"group-2",
			"Second",
			List.of(PriorityLibraryEntry.group("group-1"))
		);

		assertIllegalArgument(() ->
			new PriorityState(
				List.of(),
				List.of(first, second),
				List.of(),
				List.of(PriorityLibraryEntry.group(first.getId()))
			)
		);
	}

	@Test
	public void allowsUnresolvedBindingDefinitionReference()
	{
		CellPlacement placement = new CellPlacement(
			"cell-1",
			"missing-definition",
			4
		);
		BankTagSlotBinding slot = BankTagSlotBinding.create(
			placement,
			TEST_ITEM_ID
		);
		BankTagBinding binding = new BankTagBinding(
			"binding-1",
			"Unresolved tag",
			List.of(slot)
		);

		PriorityState state = new PriorityState(
			List.of(),
			List.of(),
			List.of(binding),
			List.of()
		);

		assertFalse(state.getBindings().isEmpty());
	}

	@Test
	public void rejectsDuplicateStableIds()
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

	private static PriorityDefinition createDefinition(
		String id)
	{
		return new PriorityDefinition(
			id,
			id + " name",
			List.of(
				new PriorityTier(
					id + "-tier",
					List.of(TEST_ITEM_ID)
				)
			)
		);
	}

	private static BankTagBinding createBinding(String id)
	{
		return new BankTagBinding(
			id,
			id + " tag",
			List.of()
		);
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
