package com.priorityslots;

import java.util.Objects;

final class LayoutStateTransaction
{
	private LayoutStateTransaction()
	{
	}

	static void execute(
		Runnable applyLayout,
		Runnable applyState,
		Runnable rollbackLayout,
		Runnable rollbackState)
	{
		Objects.requireNonNull(applyLayout, "applyLayout");
		Objects.requireNonNull(applyState, "applyState");
		Objects.requireNonNull(rollbackLayout, "rollbackLayout");
		Objects.requireNonNull(rollbackState, "rollbackState");

		try
		{
			applyLayout.run();
			applyState.run();
		}
		catch (RuntimeException originalException)
		{
			compensate(rollbackLayout, originalException);
			compensate(rollbackState, originalException);
			throw originalException;
		}
	}

	private static void compensate(
		Runnable compensation,
		RuntimeException originalException)
	{
		try
		{
			compensation.run();
		}
		catch (RuntimeException rollbackException)
		{
			originalException.addSuppressed(rollbackException);
		}
	}
}
