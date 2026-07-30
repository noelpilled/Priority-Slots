package com.priorityslots.ui;

import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityTier;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PriorityDefinitionPresentationTest
{
	private static final int LANTADYME = 1005;
	private static final int CADANTINE = 1003;
	private static final int DWARF_WEED = 1004;

	@Test
	public void previewIncludesFirstTwoPriorityItems()
	{
		PriorityDefinition definition = definition(
			"Grimy herbs",
			LANTADYME,
			CADANTINE,
			DWARF_WEED
		);

		assertEquals(
			List.of(
				"1. Grimy lantadyme",
				"2. Grimy cadantine",
				"… and 1 more"
			),
			PriorityDefinitionPresentation.previewLines(
				definition,
				PriorityDefinitionPresentationTest::itemName,
				2
			)
		);
	}

	@Test
	public void rejectsNonPositivePreviewLimit()
	{
		PriorityDefinition definition = definition(
			"Grimy herbs",
			LANTADYME
		);

		try
		{
			PriorityDefinitionPresentation.previewLines(
				definition,
				PriorityDefinitionPresentationTest::itemName,
				0
			);
			fail("Expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// Expected.
		}
	}

	private static PriorityDefinition definition(
		String name,
		int... itemIds)
	{
		java.util.ArrayList<PriorityTier> tiers =
			new java.util.ArrayList<>();

		for (int index = 0; index < itemIds.length; index++)
		{
			tiers.add(
				new PriorityTier(
					"tier-" + index,
					List.of(itemIds[index])
				)
			);
		}

		return new PriorityDefinition(
			"definition-1",
			name,
			tiers
		);
	}

	private static String itemName(int itemId)
	{
		switch (itemId)
		{
			case LANTADYME:
				return "Grimy lantadyme";
			case CADANTINE:
				return "Grimy cadantine";
			case DWARF_WEED:
				return "Grimy dwarf weed";
			default:
				return "Item " + itemId;
		}
	}
}
