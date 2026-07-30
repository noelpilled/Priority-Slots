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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PriorityStateStoreTest
{
	private static final int TEST_ITEM_ID = 1005;

	private final PriorityStateCodec codec =
		new PriorityStateCodec(new Gson());

	@Test
	public void loadsWritableEmptyStateWhenNothingIsSaved()
	{
		InMemoryStorage storage = new InMemoryStorage();
		PriorityStateStore store =
			new PriorityStateStore(storage, codec);

		PriorityStateStore.LoadResult result = store.load();

		assertTrue(result.isWritable());
		assertEquals(PriorityState.empty(), result.getState());
		assertEquals(null, result.getErrorMessage());
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

		PriorityStateStore.LoadResult result = store.load();

		assertTrue(result.isWritable());
		assertEquals(original, result.getState());
	}

	@Test
	public void malformedStateIsReadOnlyAndCannotBeOverwritten()
	{
		InMemoryStorage storage = new InMemoryStorage();
		storage.serializedState = "{";
		PriorityStateStore store =
			new PriorityStateStore(storage, codec);

		PriorityStateStore.LoadResult result = store.load();

		assertFalse(result.isWritable());
		assertEquals(PriorityState.empty(), result.getState());
		assertNotNull(result.getErrorMessage());
		assertEquals("{", storage.serializedState);

		try
		{
			store.save(createState());
			fail("Expected writes to be blocked");
		}
		catch (IllegalStateException expected)
		{
			assertEquals(
				result.getErrorMessage(),
				expected.getMessage()
			);
		}

		assertEquals("{", storage.serializedState);
	}

	@Test
	public void successfulReloadUnblocksWrites()
	{
		InMemoryStorage storage = new InMemoryStorage();
		storage.serializedState = "{";
		PriorityStateStore store =
			new PriorityStateStore(storage, codec);

		assertFalse(store.load().isWritable());

		PriorityState recovered = createState();
		storage.serializedState = codec.encode(recovered);

		PriorityStateStore.LoadResult result = store.load();

		assertTrue(result.isWritable());
		assertEquals(recovered, result.getState());

		store.save(PriorityState.empty());

		assertEquals(
			PriorityState.empty(),
			codec.decode(storage.serializedState)
		);
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
	}
}
