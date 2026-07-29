package com.priorityslots.itemsearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

@Singleton
public final class PriorityItemSearchService
{
	private static final int DEFAULT_MAX_RESULTS = 12;
	private static final String UNKNOWN_ITEM_NAME = "null";

	private final ItemSource itemSource;
	private List<IndexedItem> itemIndex;

	@Inject
	public PriorityItemSearchService(
		Client client,
		ItemManager itemManager)
	{
		Objects.requireNonNull(client, "client");
		Objects.requireNonNull(itemManager, "itemManager");

		this.itemSource = new ItemSource()
		{
			@Override
			public int itemCount()
			{
				return client.getItemCount();
			}

			@Override
			public ItemDescriptor item(int exactItemId)
			{
				ItemComposition composition =
					itemManager.getItemComposition(exactItemId);

				return new ItemDescriptor(
					composition.getName(),
					composition.getNote() != -1,
					composition.getPlaceholderTemplateId() != -1
				);
			}
		};
	}

	PriorityItemSearchService(ItemSource itemSource)
	{
		this.itemSource = Objects.requireNonNull(
			itemSource,
			"itemSource"
		);
	}

	/**
	 * Searches exact item definitions without canonicalizing IDs. The first
	 * search builds an exact-item index; later live searches only filter that
	 * immutable index. The caller must invoke this method through ClientThread
	 * because index construction reads Client item definitions.
	 */
	public List<PriorityItemSearchResult> search(String query)
	{
		return search(query, DEFAULT_MAX_RESULTS);
	}

	List<PriorityItemSearchResult> search(
		String query,
		int maxResults)
	{
		String normalizedQuery = normalizeQuery(query);

		if (maxResults <= 0)
		{
			throw new IllegalArgumentException(
				"maxResults must be positive"
			);
		}

		ensureIndex();

		List<IndexedItem> matches = new ArrayList<>();

		for (IndexedItem item : itemIndex)
		{
			if (item.normalizedName.contains(normalizedQuery))
			{
				matches.add(item);
			}
		}

		matches.sort(
			Comparator
				.comparing((IndexedItem item) ->
					!item.normalizedName.startsWith(normalizedQuery))
				.thenComparing(item -> item.normalizedName)
				.thenComparingInt(item -> item.exactItemId)
		);

		int resultCount = Math.min(matches.size(), maxResults);
		List<PriorityItemSearchResult> results =
			new ArrayList<>(resultCount);

		for (int index = 0; index < resultCount; index++)
		{
			IndexedItem item = matches.get(index);
			results.add(new PriorityItemSearchResult(
				item.exactItemId,
				item.name
			));
		}

		return List.copyOf(results);
	}

	private void ensureIndex()
	{
		if (itemIndex != null)
		{
			return;
		}

		List<IndexedItem> indexedItems = new ArrayList<>();
		int itemCount = itemSource.itemCount();

		for (int exactItemId = 1;
			exactItemId < itemCount;
			exactItemId++)
		{
			ItemDescriptor descriptor = itemSource.item(exactItemId);

			if (descriptor == null
				|| descriptor.noted
				|| descriptor.placeholder
				|| descriptor.name == null)
			{
				continue;
			}

			String name = descriptor.name.trim();
			String normalizedName = name.toLowerCase(Locale.ROOT);

			if (name.isEmpty()
				|| UNKNOWN_ITEM_NAME.equals(normalizedName))
			{
				continue;
			}

			indexedItems.add(new IndexedItem(
				exactItemId,
				name,
				normalizedName
			));
		}

		itemIndex = List.copyOf(indexedItems);
	}

	private static String normalizeQuery(String query)
	{
		String normalizedQuery = Objects.requireNonNull(
			query,
			"query"
		).trim().toLowerCase(Locale.ROOT);

		if (normalizedQuery.length() < 2)
		{
			throw new IllegalArgumentException(
				"Enter at least two characters to search items"
			);
		}

		return normalizedQuery;
	}

	interface ItemSource
	{
		int itemCount();

		ItemDescriptor item(int exactItemId);
	}

	static final class ItemDescriptor
	{
		private final String name;
		private final boolean noted;
		private final boolean placeholder;

		ItemDescriptor(
			String name,
			boolean noted,
			boolean placeholder)
		{
			this.name = name;
			this.noted = noted;
			this.placeholder = placeholder;
		}
	}

	private static final class IndexedItem
	{
		private final int exactItemId;
		private final String name;
		private final String normalizedName;

		private IndexedItem(
			int exactItemId,
			String name,
			String normalizedName)
		{
			this.exactItemId = exactItemId;
			this.name = name;
			this.normalizedName = normalizedName;
		}
	}
}
