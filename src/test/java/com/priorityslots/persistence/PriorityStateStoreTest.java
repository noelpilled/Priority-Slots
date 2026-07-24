package com.priorityslots.persistence;

import com.google.gson.Gson;
import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.CellPlacement;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityGroup;
import com.priorityslots.domain.PriorityLibraryEntry;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PriorityStateStoreTest
{
	private static final int TEST_ITEM_ID = 1005;

	private final PriorityStateCodec codec =
		new PriorityStateCodec(new Gson());

	@Test
	public void loadsEmptyStateWhenNothingIsSaved()
	{
		InMemoryStorage storage = new InMemoryStorage();
		PriorityStateStore store =
			new PriorityStateStore(storage, codec);

		assertEquals(PriorityState.empty(), store.load());
	}

	@Test
	public void savesAndLoadsCompleteState()
	{
		InMemoryStorage storage = new InMemoryStorage();
		PriorityStateStore store =
			new PriorityStateStore(storage, codec);
		PriorityState original = createState();

		store.save(original);

		assertNotNull(storage.serializedState);
		assertEquals(original, store.load());
	}

	@Test
	public void malformedStateReturnsEmptyWithoutDeletion()
	{
		InMemoryStorage storage = new InMemoryStorage();
		storage.serializedState = "{";
		PriorityStateStore store =
			new PriorityStateStore(storage, codec);

		assertEquals(PriorityState.empty(), store.load());
		assertEquals("{", storage.serializedState);
	}

	@Test
	public void clearRemovesSavedState()
	{
		InMemoryStorage storage = new InMemoryStorage();
		PriorityStateStore store =
			new PriorityStateStore(storage, codec);

		store.save(createState());
		assertNotNull(storage.serializedState);

		store.clear();
		assertNull(storage.serializedState);
	}

	private static PriorityState createState()
	{
		PriorityDefinition definition =
			new PriorityDefinition(
				"definition-1",
				"Test definition",
				List.of(
					new PriorityTier(
						"tier-1",
						List.of(TEST_ITEM_ID)
					)
				)
			);
		PriorityGroup group = new PriorityGroup(
			"group-1",
			"Test group",
			List.of(
				PriorityLibraryEntry.definition(
					definition.getId()
				)
			)
		);
		CellPlacement placement = new CellPlacement(
			"cell-1",
			definition.getId(),
			4
		);
		BankTagSlotBinding slot =
			BankTagSlotBinding.create(
				placement,
				TEST_ITEM_ID
			);
		BankTagBinding binding = new BankTagBinding(
			"binding-1",
			"Test tag",
			List.of(slot)
		);

		return new PriorityState(
			List.of(definition),
			List.of(group),
			List.of(binding),
			List.of(PriorityLibraryEntry.group(group.getId()))
		);
	}

	private static final class InMemoryStorage
		implements PriorityStateStorage
	{
		private String serializedState;

		@Override
		public String read()
		{
			return serializedState;
		}

		@Override
		public void write(String serializedState)
		{
			this.serializedState = serializedState;
		}

		@Override
		public void clear()
		{
			serializedState = null;
		}
	}
}
