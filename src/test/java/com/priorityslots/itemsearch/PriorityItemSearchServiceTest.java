package com.priorityslots.itemsearch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PriorityItemSearchServiceTest
{
	@Test
	public void returnsDistinctExactIdsWithoutCanonicalization()
	{
		Map<Integer, PriorityItemSearchService.ItemDescriptor> items =
			new HashMap<>();

		items.put(1, item("Lantadyme"));
		items.put(2, item("Lantadyme"));
		items.put(3, item("Grimy lantadyme"));
		items.put(4, new PriorityItemSearchService.ItemDescriptor(
			"Lantadyme",
			true,
			false
		));
		items.put(5, new PriorityItemSearchService.ItemDescriptor(
			"Lantadyme",
			false,
			true
		));
		items.put(6, item("null"));

		PriorityItemSearchService service = service(items, 7);

		assertEquals(
			List.of(
				new PriorityItemSearchResult(1, "Lantadyme"),
				new PriorityItemSearchResult(2, "Lantadyme"),
				new PriorityItemSearchResult(3, "Grimy lantadyme")
			),
			service.search("lanta")
		);
	}

	@Test
	public void prefersNamesThatStartWithTheQuery()
	{
		Map<Integer, PriorityItemSearchService.ItemDescriptor> items =
			new HashMap<>();

		items.put(1, item("Grimy cadantine"));
		items.put(2, item("Cadantine seed"));
		items.put(3, item("Cadantine"));

		assertEquals(
			List.of(3, 2, 1),
			service(items, 4).search("cadant")
				.stream()
				.map(PriorityItemSearchResult::getExactItemId)
				.collect(Collectors.toList())
		);
	}

	@Test
	public void requiresTwoSearchCharacters()
	{
		try
		{
			service(Map.of(), 1).search("a");
			fail("Expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			// Expected.
		}
	}

	private static PriorityItemSearchService service(
		Map<Integer, PriorityItemSearchService.ItemDescriptor> items,
		int itemCount)
	{
		return new PriorityItemSearchService(
			new PriorityItemSearchService.ItemSource()
			{
				@Override
				public int itemCount()
				{
					return itemCount;
				}

				@Override
				public PriorityItemSearchService.ItemDescriptor item(
					int exactItemId)
				{
					return items.get(exactItemId);
				}
			}
		);
	}

	private static PriorityItemSearchService.ItemDescriptor item(
		String name)
	{
		return new PriorityItemSearchService.ItemDescriptor(
			name,
			false,
			false
		);
	}
}
