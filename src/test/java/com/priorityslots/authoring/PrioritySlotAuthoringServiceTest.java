package com.priorityslots.authoring;

import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.CellPlacement;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityGroup;
import com.priorityslots.domain.PriorityLibraryEntry;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class PrioritySlotAuthoringServiceTest
{
	private static final int LANTADYME = 1005;
	private static final int CADANTINE = 1003;
	private static final int UNRELATED = 2000;
	private static final int EMPTY = -1;

	@Test
	public void createsNestedGroupsAndDefinitionInOrder()
	{
		PrioritySlotAuthoringService service = serviceWithIds(
			"group-herblore",
			"group-herbs",
			"tier-lantadyme",
			"tier-cadantine",
			"definition-herbs"
		);

		PrioritySlotAuthoringService.CreateGroupResult herblore =
			service.createGroup(
				PriorityState.empty(),
				"Herblore",
				null,
				0
			);

		PrioritySlotAuthoringService.CreateGroupResult herbs =
			service.createGroup(
				herblore.getState(),
				"Grimy herbs",
				herblore.getGroup().getId(),
				0
			);

		PrioritySlotAuthoringService.CreateDefinitionResult definition =
			service.createDefinition(
				herbs.getState(),
				"Herb priority",
				List.of(LANTADYME, CADANTINE),
				herbs.getGroup().getId(),
				0
			);

		PriorityState state = definition.getState();

		assertEquals(
			List.of(
				PriorityLibraryEntry.group("group-herblore")
			),
			state.getRootEntries()
		);

		assertEquals(
			List.of(
				PriorityLibraryEntry.group("group-herbs")
			),
			state.groupsById()
				.get("group-herblore")
				.getChildren()
		);

		assertEquals(
			List.of(
				PriorityLibraryEntry.definition(
					"definition-herbs"
				)
			),
			state.groupsById()
				.get("group-herbs")
				.getChildren()
		);

		assertEquals(
			List.of(LANTADYME),
			definition.getDefinition()
				.getTiers().get(0).getExactItemIds()
		);
		assertEquals(
			List.of(CADANTINE),
			definition.getDefinition()
				.getTiers().get(1).getExactItemIds()
		);
	}

	@Test
	public void movesDefinitionsBetweenOrderedPositions()
	{
		PriorityDefinition lantadyme = definition(
			"definition-lantadyme",
			"tier-lantadyme",
			LANTADYME
		);
		PriorityDefinition cadantine = definition(
			"definition-cadantine",
			"tier-cadantine",
			CADANTINE
		);

		PriorityGroup group = new PriorityGroup(
			"group-herbs",
			"Herbs",
			List.of(
				PriorityLibraryEntry.definition(
					lantadyme.getId()
				),
				PriorityLibraryEntry.definition(
					cadantine.getId()
				)
			)
		);

		PriorityState state = new PriorityState(
			List.of(lantadyme, cadantine),
			List.of(group),
			List.of(),
			List.of(PriorityLibraryEntry.group(group.getId()))
		);

		PriorityState moved = serviceWithIds().moveDefinition(
			state,
			cadantine.getId(),
			group.getId(),
			0
		);

		assertEquals(
			List.of(
				PriorityLibraryEntry.definition(
					cadantine.getId()
				),
				PriorityLibraryEntry.definition(
					lantadyme.getId()
				)
			),
			moved.groupsById()
				.get(group.getId())
				.getChildren()
		);
	}

	@Test
	public void rejectsMovingGroupIntoDescendant()
	{
		PriorityGroup child = new PriorityGroup(
			"group-child",
			"Child",
			List.of()
		);
		PriorityGroup parent = new PriorityGroup(
			"group-parent",
			"Parent",
			List.of(
				PriorityLibraryEntry.group(child.getId())
			)
		);

		PriorityState state = new PriorityState(
			List.of(),
			List.of(parent, child),
			List.of(),
			List.of(PriorityLibraryEntry.group(parent.getId()))
		);

		assertIllegalArgument(() ->
			serviceWithIds().moveGroup(
				state,
				parent.getId(),
				child.getId(),
				0
			)
		);
	}

	@Test
	public void reordersCandidateTiersWithoutChangingIdentity()
	{
		PriorityTier lantadymeTier = new PriorityTier(
			"tier-lantadyme",
			List.of(LANTADYME)
		);
		PriorityTier cadantineTier = new PriorityTier(
			"tier-cadantine",
			List.of(CADANTINE)
		);
		PriorityDefinition definition =
			new PriorityDefinition(
				"definition-herbs",
				"Herbs",
				List.of(lantadymeTier, cadantineTier)
			);

		PriorityState state = new PriorityState(
			List.of(definition),
			List.of(),
			List.of(),
			List.of(
				PriorityLibraryEntry.definition(
					definition.getId()
				)
			)
		);

		PriorityState moved =
			serviceWithIds().moveCandidateTier(
				state,
				definition.getId(),
				cadantineTier.getId(),
				0
			);

		PriorityDefinition updated =
			moved.definitionsById().get(definition.getId());

		assertEquals(
			List.of(
				cadantineTier.getId(),
				lantadymeTier.getId()
			),
			List.of(
				updated.getTiers().get(0).getId(),
				updated.getTiers().get(1).getId()
			)
		);
		assertSame(cadantineTier, updated.getTiers().get(0));
		assertSame(lantadymeTier, updated.getTiers().get(1));
	}

	@Test
	public void installsIntoFirstEmptyCellAndSeedsTopCandidate()
	{
		PriorityDefinition definition = herbsDefinition();
		PriorityState state = stateWithDefinition(definition);

		PrioritySlotAuthoringService.InstallationResult result =
			serviceWithIds().installDefinitionInActiveLayout(
				state,
				"Herbs",
				List.of(UNRELATED, EMPTY, EMPTY),
				definition.getId()
			);

		assertEquals(1, result.getLayoutIndex());
		assertEquals(LANTADYME, result.getSeededExactItemId());
		assertEquals(
			List.of(UNRELATED, LANTADYME, EMPTY),
			result.getLayoutItems()
		);

		BankTagBinding binding =
			result.getState().getBindings().get(0);

		assertEquals("Herbs", binding.getBankTagName());
		assertEquals(1, binding.getSlots().get(0)
			.getPlacement().getIndex());
		assertEquals(definition.getId(), binding.getSlots()
			.get(0).getPlacement().getDefinitionId());
	}

	@Test
	public void appendsWhenLayoutHasNoEmptyCell()
	{
		PriorityDefinition definition = herbsDefinition();

		PrioritySlotAuthoringService.InstallationResult result =
			serviceWithIds().installDefinitionInActiveLayout(
				stateWithDefinition(definition),
				"Herbs",
				List.of(UNRELATED, 2001),
				definition.getId()
			);

		assertEquals(2, result.getLayoutIndex());
		assertEquals(
			List.of(UNRELATED, 2001, LANTADYME),
			result.getLayoutItems()
		);
	}

	@Test
	public void reusesActiveTagBindingAndSkipsReservedEmptyCell()
	{
		PriorityDefinition first = definition(
			"definition-first",
			"tier-first",
			LANTADYME
		);
		PriorityDefinition second = definition(
			"definition-second",
			"tier-second",
			CADANTINE
		);

		BankTagSlotBinding existingSlot =
			BankTagSlotBinding.create(
				new CellPlacement(
					"cell-existing",
					first.getId(),
					1
				),
				LANTADYME
			);
		BankTagBinding existingBinding =
			new BankTagBinding(
				"binding-existing",
				"Herbs",
				List.of(existingSlot)
			);

		PriorityState state = new PriorityState(
			List.of(first, second),
			List.of(),
			List.of(existingBinding),
			List.of(
				PriorityLibraryEntry.definition(first.getId()),
				PriorityLibraryEntry.definition(second.getId())
			)
		);

		PrioritySlotAuthoringService.InstallationResult result =
			serviceWithIds().installDefinitionInActiveLayout(
				state,
				" herbs ",
				List.of(UNRELATED, EMPTY, EMPTY),
				second.getId()
			);

		assertEquals("binding-existing", result.getBindingId());
		assertEquals(2, result.getLayoutIndex());
		assertEquals(2, result.getState()
			.getBindings().get(0).getSlots().size());
	}

	@Test
	public void rejectsDuplicateInstallationInSameTag()
	{
		PriorityDefinition definition = herbsDefinition();
		BankTagSlotBinding slot = BankTagSlotBinding.create(
			new CellPlacement(
				"cell-existing",
				definition.getId(),
				0
			),
			LANTADYME
		);
		BankTagBinding binding = new BankTagBinding(
			"binding-existing",
			"Herbs",
			List.of(slot)
		);
		PriorityState state = new PriorityState(
			List.of(definition),
			List.of(),
			List.of(binding),
			List.of(
				PriorityLibraryEntry.definition(
					definition.getId()
				)
			)
		);

		assertIllegalArgument(() ->
			serviceWithIds().installDefinitionInActiveLayout(
				state,
				"Herbs",
				List.of(LANTADYME),
				definition.getId()
			)
		);
	}

	@Test
	public void rejectsDuplicateCandidateIds()
	{
		assertIllegalArgument(() ->
			serviceWithIds(
				"tier-1",
				"tier-2",
				"definition-1"
			).createDefinition(
				PriorityState.empty(),
				"Invalid",
				List.of(LANTADYME, LANTADYME),
				null,
				0
			)
		);
	}

	private static PrioritySlotAuthoringService serviceWithIds(
		String... ids)
	{
		Queue<String> values = new ArrayDeque<>(List.of(ids));

		return new PrioritySlotAuthoringService(() ->
		{
			if (values.isEmpty())
			{
				return "generated-" + System.nanoTime();
			}

			return values.remove();
		});
	}

	private static PriorityDefinition herbsDefinition()
	{
		return new PriorityDefinition(
			"definition-herbs",
			"Herbs",
			List.of(
				new PriorityTier(
					"tier-lantadyme",
					List.of(LANTADYME)
				),
				new PriorityTier(
					"tier-cadantine",
					List.of(CADANTINE)
				)
			)
		);
	}

	private static PriorityDefinition definition(
		String definitionId,
		String tierId,
		int itemId)
	{
		return new PriorityDefinition(
			definitionId,
			definitionId,
			List.of(new PriorityTier(tierId, List.of(itemId)))
		);
	}

	private static PriorityState stateWithDefinition(
		PriorityDefinition definition)
	{
		return new PriorityState(
			List.of(definition),
			List.of(),
			List.of(),
			List.of(
				PriorityLibraryEntry.definition(
					definition.getId()
				)
			)
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
			assertNotEquals("", expected.getMessage());
		}
	}
}
