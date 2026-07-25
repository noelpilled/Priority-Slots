package com.priorityslots.ui;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PriorityItemNameCacheTest
{
	private static final int LANTADYME = 1005;
	private static final int CADANTINE = 1003;

	@Test
	public void defersResolutionToClientScheduler()
	{
		Queue<Runnable> clientTasks = new ArrayDeque<>();
		Queue<Runnable> uiTasks = new ArrayDeque<>();
		AtomicInteger resolverCalls = new AtomicInteger();
		AtomicInteger updates = new AtomicInteger();

		PriorityItemNameCache cache =
			new PriorityItemNameCache(
				clientTasks::add,
				exactItemId ->
				{
					resolverCalls.incrementAndGet();
					return exactItemId == LANTADYME
						? "Grimy lantadyme"
						: "Grimy cadantine";
				},
				uiTasks::add
			);

		cache.request(
			List.of(LANTADYME, CADANTINE),
			updates::incrementAndGet
		);

		assertEquals(0, resolverCalls.get());
		assertEquals(
			"Item " + LANTADYME,
			cache.displayName(LANTADYME)
		);
		assertEquals(1, clientTasks.size());
		assertEquals(0, uiTasks.size());

		clientTasks.remove().run();

		assertEquals(2, resolverCalls.get());
		assertEquals(
			"Grimy lantadyme",
			cache.displayName(LANTADYME)
		);
		assertEquals(
			"Grimy cadantine",
			cache.displayName(CADANTINE)
		);
		assertEquals(1, uiTasks.size());
		assertEquals(0, updates.get());

		uiTasks.remove().run();

		assertEquals(1, updates.get());
	}

	@Test
	public void coalescesItemsAlreadyPending()
	{
		Queue<Runnable> clientTasks = new ArrayDeque<>();

		PriorityItemNameCache cache =
			new PriorityItemNameCache(
				clientTasks::add,
				exactItemId -> "Item name",
				Runnable::run
			);

		cache.request(
			List.of(LANTADYME, CADANTINE),
			() -> { }
		);
		cache.request(
			List.of(LANTADYME),
			() -> { }
		);

		assertEquals(1, clientTasks.size());
	}

	@Test
	public void retriesResolutionAfterTransientFailure()
	{
		Queue<Runnable> clientTasks = new ArrayDeque<>();
		AtomicInteger attempts = new AtomicInteger();

		PriorityItemNameCache cache =
			new PriorityItemNameCache(
				clientTasks::add,
				exactItemId ->
				{
					if (attempts.getAndIncrement() == 0)
					{
						throw new IllegalStateException(
							"Item data not ready"
						);
					}

					return "Grimy lantadyme";
				},
				Runnable::run
			);

		cache.request(
			List.of(LANTADYME),
			() -> { }
		);
		clientTasks.remove().run();

		assertEquals(
			"Item " + LANTADYME,
			cache.displayName(LANTADYME)
		);

		cache.request(
			List.of(LANTADYME),
			() -> { }
		);
		clientTasks.remove().run();

		assertEquals(
			"Grimy lantadyme",
			cache.displayName(LANTADYME)
		);
	}
}
