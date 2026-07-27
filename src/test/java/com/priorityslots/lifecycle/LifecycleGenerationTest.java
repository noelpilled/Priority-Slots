package com.priorityslots.lifecycle;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LifecycleGenerationTest
{
	@Test
	public void staleDeactivateCannotMatchReactivatedLifecycle()
	{
		LifecycleGeneration lifecycle = new LifecycleGeneration();

		long firstActivation = lifecycle.activate();
		long deactivation = lifecycle.deactivate();
		long secondActivation = lifecycle.activate();

		assertFalse(lifecycle.isCurrent(firstActivation, true));
		assertFalse(lifecycle.isCurrent(deactivation, false));
		assertTrue(lifecycle.isCurrent(secondActivation, true));
	}

	@Test
	public void generationAdvancePreservesActiveStateAndInvalidatesWork()
	{
		LifecycleGeneration lifecycle = new LifecycleGeneration();
		long activation = lifecycle.activate();
		long advanced = lifecycle.advanceGeneration();

		assertTrue(lifecycle.isActive());
		assertFalse(lifecycle.isCurrent(activation, true));
		assertTrue(lifecycle.isCurrent(advanced, true));
	}

	@Test
	public void deactivationInvalidatesQueuedActiveWork()
	{
		LifecycleGeneration lifecycle = new LifecycleGeneration();
		long activation = lifecycle.activate();
		long deactivation = lifecycle.deactivate();

		assertFalse(lifecycle.isCurrent(activation, true));
		assertTrue(lifecycle.isCurrent(deactivation, false));
	}
}
