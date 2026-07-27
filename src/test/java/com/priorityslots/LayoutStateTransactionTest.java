package com.priorityslots;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class LayoutStateTransactionTest
{
	@Test
	public void restoresLayoutAndStateWhenStateApplicationFails()
	{
		AtomicReference<String> layout = new AtomicReference<>("old-layout");
		AtomicReference<String> state = new AtomicReference<>("old-state");
		List<String> steps = new ArrayList<>();
		RuntimeException failure = new IllegalStateException("state failed");

		try
		{
			LayoutStateTransaction.execute(
				() ->
				{
					steps.add("apply-layout");
					layout.set("new-layout");
				},
				() ->
				{
					steps.add("apply-state");
					state.set("new-state");
					throw failure;
				},
				() ->
				{
					steps.add("rollback-layout");
					layout.set("old-layout");
				},
				() ->
				{
					steps.add("rollback-state");
					state.set("old-state");
				}
			);
			fail("Expected transaction failure");
		}
		catch (RuntimeException exception)
		{
			assertSame(failure, exception);
		}

		assertEquals("old-layout", layout.get());
		assertEquals("old-state", state.get());
		assertEquals(
			List.of(
				"apply-layout",
				"apply-state",
				"rollback-layout",
				"rollback-state"
			),
			steps
		);
	}

	@Test
	public void recordsBothRollbackFailuresAsSuppressed()
	{
		RuntimeException original = new IllegalStateException("apply failed");
		RuntimeException layoutRollback =
			new IllegalStateException("layout rollback failed");
		RuntimeException stateRollback =
			new IllegalStateException("state rollback failed");

		try
		{
			LayoutStateTransaction.execute(
				() -> { },
				() -> { throw original; },
				() -> { throw layoutRollback; },
				() -> { throw stateRollback; }
			);
			fail("Expected transaction failure");
		}
		catch (RuntimeException exception)
		{
			assertSame(original, exception);
			assertEquals(2, exception.getSuppressed().length);
			assertSame(layoutRollback, exception.getSuppressed()[0]);
			assertSame(stateRollback, exception.getSuppressed()[1]);
		}
	}

	@Test
	public void successfulTransactionDoesNotRunRollback()
	{
		List<String> steps = new ArrayList<>();

		LayoutStateTransaction.execute(
			() -> steps.add("apply-layout"),
			() -> steps.add("apply-state"),
			() -> steps.add("rollback-layout"),
			() -> steps.add("rollback-state")
		);

		assertEquals(
			List.of("apply-layout", "apply-state"),
			steps
		);
	}
}
