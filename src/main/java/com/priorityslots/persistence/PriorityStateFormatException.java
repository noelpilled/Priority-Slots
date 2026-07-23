package com.priorityslots.persistence;

public final class PriorityStateFormatException
		extends RuntimeException
{
	public PriorityStateFormatException(String message)
	{
		super(message);
	}

	public PriorityStateFormatException(
			String message,
			Throwable cause)
	{
		super(message, cause);
	}
}
