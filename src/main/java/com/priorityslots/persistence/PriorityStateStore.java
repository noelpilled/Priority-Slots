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
	private final PriorityStateStorage storage;
	private final PriorityStateCodec codec;

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

	public PriorityState load()
	{
		String serializedState = storage.read();

		if (serializedState == null
				|| serializedState.trim().isEmpty())
		{
			return PriorityState.empty();
		}

		try
		{
			return codec.decode(serializedState);
		}
		catch (PriorityStateFormatException exception)
		{
			log.warn(
					"Unable to load Priority Slots state; "
							+ "saved data was left unchanged",
					exception
			);

			return PriorityState.empty();
		}
	}

	public void save(PriorityState state)
	{
		Objects.requireNonNull(state, "state");

		storage.write(codec.encode(state));
	}

	public void clear()
	{
		storage.clear();
	}
}
