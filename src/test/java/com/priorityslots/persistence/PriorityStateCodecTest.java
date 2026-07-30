package com.priorityslots.persistence;

import com.google.gson.Gson;
import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.CellPlacement;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityGroup;
import com.priorityslots.domain.PriorityLibraryEntry;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PriorityStateCodecTest
{
	private static final int HIGH_PRIORITY_ITEM = 1005;
	private static final int LOW_PRIORITY_ITEM = 1003;

	private final PriorityStateCodec codec =
		new PriorityStateCodec(new Gson());

	@Test
	public void roundTripsNestedOrderedLibrary()
	{
		PriorityDefinition definition = definition(
			"definition-1",
			"Herbs"
		);
		PriorityGroup child = new PriorityGroup(
			"group-child",
			"Grimy herbs",
			List.of(
				PriorityLibraryEntry.definition(
					definition.getId()
				)
			)
		);
		PriorityGroup parent = new PriorityGroup(
			"group-parent",
			"Herblore",
			List.of(PriorityLibraryEntry.group(child.getId()))
		);
		BankTagBinding binding = binding(definition.getId());

		PriorityState original = new PriorityState(
			List.of(definition),
			List.of(parent, child),
			List.of(binding),
			List.of(PriorityLibraryEntry.group(parent.getId()))
		);

		String json = codec.encode(original);
		PriorityState decoded = codec.decode(json);

		assertTrue(json.contains("\"schemaVersion\":2"));
		assertTrue(json.contains("\"rootEntries\""));
		assertTrue(json.contains("\"children\""));
		assertEquals(original, decoded);
	}

	@Test
	public void roundTripsEmptyState()
	{
		PriorityState original = PriorityState.empty();

		assertEquals(
			original,
			codec.decode(codec.encode(original))
		);
	}

	@Test
	public void rejectsUnpublishedSchemaVersionOne()
	{
		assertFormatException(() -> codec.decode(
			"{"
				+ "\"schemaVersion\":1,"
				+ "\"definitions\":[],"
				+ "\"groups\":[],"
				+ "\"bindings\":[],"
				+ "\"rootEntries\":[]"
				+ "}"
		));
	}

	@Test
	public void rejectsUnsupportedSchemaVersion()
	{
		assertFormatException(() -> codec.decode(
			"{"
				+ "\"schemaVersion\":3,"
				+ "\"definitions\":[],"
				+ "\"groups\":[],"
				+ "\"bindings\":[],"
				+ "\"rootEntries\":[]"
				+ "}"
		));
	}

	@Test
	public void rejectsInvalidNestedLibrary()
	{
		assertFormatException(() -> codec.decode(
			"{"
				+ "\"schemaVersion\":2,"
				+ "\"definitions\":[],"
				+ "\"groups\":[{"
				+ "\"id\":\"group-1\","
				+ "\"name\":\"Cycle\","
				+ "\"children\":[{"
				+ "\"type\":\"GROUP\","
				+ "\"targetId\":\"group-1\""
				+ "}]"
				+ "}],"
				+ "\"bindings\":[],"
				+ "\"rootEntries\":[{"
				+ "\"type\":\"GROUP\","
				+ "\"targetId\":\"group-1\""
				+ "}]"
				+ "}"
		));
	}

	@Test
	public void rejectsMissingVersionTwoCollections()
	{
		assertFormatException(() -> codec.decode(
			"{\"schemaVersion\":2}"
		));
	}

	private static PriorityDefinition definition(
		String id,
		String name)
	{
		return new PriorityDefinition(
			id,
			name,
			List.of(
				new PriorityTier(
					"tier-high",
					List.of(HIGH_PRIORITY_ITEM)
				),
				new PriorityTier(
					"tier-low",
					List.of(LOW_PRIORITY_ITEM)
				)
			)
		);
	}

	private static BankTagBinding binding(
		String definitionId)
	{
		BankTagSlotBinding slot = new BankTagSlotBinding(
			new CellPlacement(
				"cell-1",
				definitionId,
				4
			),
			HIGH_PRIORITY_ITEM,
			LOW_PRIORITY_ITEM
		);

		return new BankTagBinding(
			"binding-1",
			"Herbs",
			List.of(slot)
		);
	}

	private static void assertFormatException(
		Runnable action)
	{
		try
		{
			action.run();
			fail("Expected PriorityStateFormatException");
		}
		catch (PriorityStateFormatException expected)
		{
			// Expected.
		}
	}
}
