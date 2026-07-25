package com.priorityslots.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntFunction;

final class PriorityItemNameCache
{
	private final Consumer<Runnable> clientScheduler;
	private final IntFunction<String> nameResolver;
	private final Consumer<Runnable> uiScheduler;

	private final Map<Integer, String> names =
		new ConcurrentHashMap<>();

	private final Set<Integer> pendingItemIds =
		ConcurrentHashMap.newKeySet();

	PriorityItemNameCache(
		Consumer<Runnable> clientScheduler,
		IntFunction<String> nameResolver,
		Consumer<Runnable> uiScheduler)
	{
		this.clientScheduler = Objects.requireNonNull(
			clientScheduler,
			"clientScheduler"
		);
		this.nameResolver = Objects.requireNonNull(
			nameResolver,
			"nameResolver"
		);
		this.uiScheduler = Objects.requireNonNull(
			uiScheduler,
			"uiScheduler"
		);
	}

	String displayName(int exactItemId)
	{
		return names.getOrDefault(
			exactItemId,
			"Item " + exactItemId
		);
	}

	void request(
		Collection<Integer> exactItemIds,
		Runnable onUpdated)
	{
		Objects.requireNonNull(
			exactItemIds,
			"exactItemIds"
		);
		Objects.requireNonNull(onUpdated, "onUpdated");

		List<Integer> missingItemIds =
			new ArrayList<>();

		for (Integer exactItemId : exactItemIds)
		{
			if (exactItemId == null || exactItemId <= 0)
			{
				continue;
			}

			if (!names.containsKey(exactItemId)
				&& pendingItemIds.add(exactItemId))
			{
				missingItemIds.add(exactItemId);
			}
		}

		if (missingItemIds.isEmpty())
		{
			return;
		}

		List<Integer> requestedItemIds =
			List.copyOf(missingItemIds);

		try
		{
			clientScheduler.accept(() ->
			{
				boolean updated = false;

				try
				{
					for (Integer exactItemId
						: requestedItemIds)
					{
						String resolvedName;

						try
						{
							resolvedName = nameResolver.apply(
								exactItemId
							);
						}
						catch (RuntimeException exception)
						{
							continue;
						}

						if (resolvedName == null
							|| resolvedName.trim().isEmpty())
						{
							continue;
						}

						names.put(
							exactItemId,
							resolvedName
						);
						updated = true;
					}
				}
				finally
				{
					pendingItemIds.removeAll(
						requestedItemIds
					);
				}

				if (updated)
				{
					uiScheduler.accept(onUpdated);
				}
			});
		}
		catch (RuntimeException exception)
		{
			pendingItemIds.removeAll(requestedItemIds);
			throw exception;
		}
	}
}
