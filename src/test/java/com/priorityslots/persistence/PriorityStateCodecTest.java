package com.priorityslots.persistence;

import com.google.gson.Gson;
import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.CellPlacement;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityGroup;
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
	private static final int MIDDLE_PRIORITY_ITEM = 1004;
	private static final int LOW_PRIORITY_ITEM = 1003;

	private final PriorityStateCodec codec =
			new PriorityStateCodec(new Gson());

	@Test
	public void roundTripsCompleteState()
	{
		PriorityTier chargedTier =
				new PriorityTier(
						"tier-charged",
						List.of(
								HIGH_PRIORITY_ITEM,
								MIDDLE_PRIORITY_ITEM
						)
				);

		PriorityTier fallbackTier =
				new PriorityTier(
						"tier-fallback",
						List.of(LOW_PRIORITY_ITEM)
				);

		PriorityDefinition definition =
				new PriorityDefinition(
						"definition-1",
						"Teleport jewellery",
						List.of(
								chargedTier,
								fallbackTier
						)
				);

		PriorityGroup group =
				new PriorityGroup(
						"group-1",
						"Teleport group",
						List.of(definition.getId())
				);

		CellPlacement placement =
				new CellPlacement(
						"cell-1",
						definition.getId(),
						4
				);

		BankTagSlotBinding slot =
				new BankTagSlotBinding(
						placement,
						HIGH_PRIORITY_ITEM,
						MIDDLE_PRIORITY_ITEM
				);

		BankTagBinding binding =
				new BankTagBinding(
						"binding-1",
						"Teleport layout",
						List.of(slot)
				);

		PriorityState original =
				new PriorityState(
						List.of(definition),
						List.of(group),
						List.of(binding)
				);

		String json = codec.encode(original);
		PriorityState decoded =
				codec.decode(json);

		assertTrue(
				json.contains(
						"\"schemaVersion\":1"
				)
		);

		assertTrue(
				json.contains("\"bindings\"")
		);
		assertTrue(
				json.contains(
						"\"fallbackExactItemId\":"
								+ HIGH_PRIORITY_ITEM
				)
		);
		assertTrue(
				json.contains(
						"\"lastProjectedExactItemId\":"
								+ MIDDLE_PRIORITY_ITEM
				)
		);

		assertEquals(original, decoded);
	}

	@Test
	public void roundTripsEmptyState()
	{
		PriorityState original =
				PriorityState.empty();

		PriorityState decoded =
				codec.decode(
						codec.encode(original)
				);

		assertEquals(original, decoded);
	}

	@Test
	public void rejectsUnsupportedSchemaVersion()
	{
		assertFormatException(() ->
				codec.decode(
						"{"
								+ "\"schemaVersion\":2,"
								+ "\"definitions\":[],"
								+ "\"groups\":[],"
								+ "\"bindings\":[]"
								+ "}"
				)
		);
	}

	@Test
	public void rejectsInvalidJson()
	{
		assertFormatException(() ->
				codec.decode("{")
		);
	}

	@Test
	public void rejectsMissingCollections()
	{
		assertFormatException(() ->
				codec.decode(
						"{\"schemaVersion\":1}"
				)
		);
	}

	@Test
	public void rejectsInvalidBindingState()
	{
		String json =
				"{"
						+ "\"schemaVersion\":1,"
						+ "\"definitions\":[],"
						+ "\"groups\":[],"
						+ "\"bindings\":["
						+ "{"
						+ "\"id\":\"binding-1\","
						+ "\"bankTagName\":\"Invalid binding\","
						+ "\"slots\":["
						+ "{"
						+ "\"cellId\":\"cell-1\","
						+ "\"definitionId\":\"definition-1\","
						+ "\"index\":4,"
						+ "\"fallbackExactItemId\":1005,"
						+ "\"lastProjectedExactItemId\":1005"
						+ "},"
						+ "{"
						+ "\"cellId\":\"cell-2\","
						+ "\"definitionId\":\"definition-2\","
						+ "\"index\":4,"
						+ "\"fallbackExactItemId\":1003,"
						+ "\"lastProjectedExactItemId\":1003"
						+ "}"
						+ "]"
						+ "}"
						+ "]"
						+ "}";

		assertFormatException(() ->
				codec.decode(json)
		);
	}

	@Test
	public void rejectsInvalidGroupState()
	{
		String json =
				"{"
						+ "\"schemaVersion\":1,"
						+ "\"definitions\":[],"
						+ "\"groups\":["
						+ "{"
						+ "\"id\":\"group-1\","
						+ "\"name\":\"Invalid group\","
						+ "\"definitionIds\":["
						+ "\"definition-1\","
						+ "\"definition-1\""
						+ "]"
						+ "}"
						+ "],"
						+ "\"bindings\":[]"
						+ "}";

		assertFormatException(() ->
				codec.decode(json)
		);
	}

	private static void assertFormatException(
			Runnable action)
	{
		try
		{
			action.run();
			fail(
					"Expected "
							+ "PriorityStateFormatException"
			);
		}
		catch (PriorityStateFormatException expected)
		{
			// Expected.
		}
	}
}
