package com.priorityslots.persistence;

import com.priorityslots.domain.PriorityState;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public final class PriorityStateStore
{
	private static final String STATE_UNAVAILABLE_MESSAGE =
		"Saved Priority Slots data could not be loaded. "
			+ "Priority Slots is read-only to protect that data.";

	private final PriorityStateStorage storage;
	private final PriorityStateCodec codec;
	private volatile boolean writesBlocked;

	@Inject
	public PriorityStateStore(
		ConfigPriorityStateStorage storage,
		PriorityStateCodec codec)
	{
		this(
			(PriorityStateStorage) storage,
			codec
		);
	}

	PriorityStateStore(
		PriorityStateStorage storage,
		PriorityStateCodec codec)
	{
		this.storage = Objects.requireNonNull(
			storage,
			"storage"
		);

		this.codec = Objects.requireNonNull(
			codec,
			"codec"
		);
	}

	public LoadResult load()
	{
		String serializedState = storage.read();

		if (serializedState == null
			|| serializedState.trim().isEmpty())
		{
			writesBlocked = false;
			return LoadResult.writable(PriorityState.empty());
		}

		try
		{
			PriorityState state = codec.decode(serializedState);
			writesBlocked = false;
			return LoadResult.writable(state);
		}
		catch (PriorityStateFormatException exception)
		{
			writesBlocked = true;

			log.warn(
				"Unable to load Priority Slots state; "
					+ "writes are blocked and saved data "
					+ "was left unchanged",
				exception
			);

			return LoadResult.readOnly(
				PriorityState.empty(),
				STATE_UNAVAILABLE_MESSAGE
			);
		}
	}

	public void save(PriorityState state)
	{
		Objects.requireNonNull(state, "state");

		if (writesBlocked)
		{
			throw new IllegalStateException(
				STATE_UNAVAILABLE_MESSAGE
			);
		}

		storage.write(codec.encode(state));
	}

	public static final class LoadResult
	{
		private final PriorityState state;
		private final String errorMessage;

		private LoadResult(
			PriorityState state,
			String errorMessage)
		{
			this.state = Objects.requireNonNull(state, "state");
			this.errorMessage = errorMessage;
		}

		private static LoadResult writable(PriorityState state)
		{
			return new LoadResult(state, null);
		}

		private static LoadResult readOnly(
			PriorityState state,
			String errorMessage)
		{
			return new LoadResult(
				state,
				Objects.requireNonNull(
					errorMessage,
					"errorMessage"
				)
			);
		}

		public PriorityState getState()
		{
			return state;
		}

		public String getErrorMessage()
		{
			return errorMessage;
		}

		public boolean isWritable()
		{
			return errorMessage == null;
		}
	}
}
