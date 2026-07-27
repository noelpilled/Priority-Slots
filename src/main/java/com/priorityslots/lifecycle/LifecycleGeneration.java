package com.priorityslots.lifecycle;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe lifecycle token source for discarding stale queued work.
 */
public final class LifecycleGeneration
{
	private final AtomicReference<State> state =
		new AtomicReference<>(new State(false, 0L));

	public long activate()
	{
		return update(true);
	}

	public long deactivate()
	{
		return update(false);
	}

	public long advanceGeneration()
	{
		State updated = state.updateAndGet(current ->
			new State(current.active, current.generation + 1L)
		);

		return updated.generation;
	}

	public boolean isActive()
	{
		return state.get().active;
	}

	public long currentGeneration()
	{
		return state.get().generation;
	}

	public boolean isCurrent(
		long expectedGeneration,
		boolean expectedActive)
	{
		State current = state.get();

		return current.generation == expectedGeneration
			&& current.active == expectedActive;
	}

	private long update(boolean active)
	{
		State updated = state.updateAndGet(current ->
			new State(active, current.generation + 1L)
		);

		return updated.generation;
	}

	private static final class State
	{
		private final boolean active;
		private final long generation;

		private State(boolean active, long generation)
		{
			this.active = active;
			this.generation = generation;
		}
	}
}
