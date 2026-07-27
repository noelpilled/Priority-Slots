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
	private static final int DEFAULT_MAX_RESULTS = 50;
	private static final String UNKNOWN_ITEM_NAME = "null";

	private final ItemSource itemSource;

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
	 * Searches exact item definitions without canonicalizing IDs. The caller
	 * must invoke this method through ClientThread because the production item
	 * source reads Client item definitions.
	 */
	public List<PriorityItemSearchResult> search(String query)
	{
		return search(query, DEFAULT_MAX_RESULTS);
	}

	List<PriorityItemSearchResult> search(
		String query,
		int maxResults)
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

		if (maxResults <= 0)
		{
			throw new IllegalArgumentException(
				"maxResults must be positive"
			);
		}

		List<PriorityItemSearchResult> matches =
			new ArrayList<>();

		int itemCount = itemSource.itemCount();

		for (int exactItemId = 1;
			exactItemId < itemCount;
			exactItemId++)
		{
			ItemDescriptor descriptor =
				itemSource.item(exactItemId);

			if (descriptor == null
				|| descriptor.noted
				|| descriptor.placeholder)
			{
				continue;
			}

			String name = descriptor.name;

			if (name == null)
			{
				continue;
			}

			String trimmedName = name.trim();
			String normalizedName =
				trimmedName.toLowerCase(Locale.ROOT);

			if (trimmedName.isEmpty()
				|| UNKNOWN_ITEM_NAME.equals(normalizedName)
				|| !normalizedName.contains(normalizedQuery))
			{
				continue;
			}

			matches.add(new PriorityItemSearchResult(
				exactItemId,
				trimmedName
			));
		}

		matches.sort(
			Comparator
				.comparing((PriorityItemSearchResult result) ->
					!result.getName()
						.toLowerCase(Locale.ROOT)
						.startsWith(normalizedQuery))
				.thenComparing(
					result -> result.getName()
						.toLowerCase(Locale.ROOT)
				)
				.thenComparingInt(
					PriorityItemSearchResult::getExactItemId
				)
		);

		if (matches.size() > maxResults)
		{
			return List.copyOf(matches.subList(0, maxResults));
		}

		return List.copyOf(matches);
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
}
