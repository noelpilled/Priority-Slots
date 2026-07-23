package com.priorityslots.banktags;

import java.util.Optional;
import java.util.OptionalInt;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BankTagLayoutReaderTest
{
	@Test
	public void returnsEmptyWhenLayoutIsMissing()
	{
		InMemoryStorage storage =
				new InMemoryStorage(null);

		BankTagLayoutReader reader =
				new BankTagLayoutReader(storage);

		Optional<BankTagLayoutSnapshot> result =
				reader.load("Melee");

		assertFalse(result.isPresent());
	}

	@Test
	public void blankValueIsPresentEmptyLayout()
	{
		InMemoryStorage storage =
				new InMemoryStorage("");

		BankTagLayoutReader reader =
				new BankTagLayoutReader(storage);

		Optional<BankTagLayoutSnapshot> result =
				reader.load("Melee");

		assertTrue(result.isPresent());
		assertEquals(0, result.get().size());
	}

	@Test
	public void parsesLayoutInSavedOrder()
	{
		InMemoryStorage storage =
				new InMemoryStorage(
						"1005,-1,1003"
				);

		BankTagLayoutReader reader =
				new BankTagLayoutReader(storage);

		BankTagLayoutSnapshot layout =
				reader.load("Melee").get();

		assertEquals(3, layout.size());

		assertEquals(
				OptionalInt.of(1005),
				layout.itemAt(0)
		);

		assertEquals(
				OptionalInt.of(-1),
				layout.itemAt(1)
		);

		assertEquals(
				OptionalInt.of(1003),
				layout.itemAt(2)
		);
	}

	@Test
	public void trimsCsvEntriesLikeRuneLite()
	{
		InMemoryStorage storage =
				new InMemoryStorage(
						" 1005, -1, 1003 "
				);

		BankTagLayoutReader reader =
				new BankTagLayoutReader(storage);

		BankTagLayoutSnapshot layout =
				reader.load("Melee").get();

		assertEquals(
				OptionalInt.of(1005),
				layout.itemAt(0)
		);

		assertEquals(
				OptionalInt.of(-1),
				layout.itemAt(1)
		);

		assertEquals(
				OptionalInt.of(1003),
				layout.itemAt(2)
		);
	}

	@Test
	public void distinguishesMissingIndexFromBlankCell()
	{
		InMemoryStorage storage =
				new InMemoryStorage("-1");

		BankTagLayoutReader reader =
				new BankTagLayoutReader(storage);

		BankTagLayoutSnapshot layout =
				reader.load("Melee").get();

		assertEquals(
				OptionalInt.of(-1),
				layout.itemAt(0)
		);

		assertEquals(
				OptionalInt.empty(),
				layout.itemAt(1)
		);
	}

	@Test
	public void rejectsInvalidItemId()
	{
		InMemoryStorage storage =
				new InMemoryStorage(
						"1005,not-an-item,1003"
				);

		BankTagLayoutReader reader =
				new BankTagLayoutReader(storage);

		try
		{
			reader.load("Melee");

			fail(
					"Expected "
							+ "BankTagLayoutFormatException"
			);
		}
		catch (BankTagLayoutFormatException expected)
		{
			// Expected.
		}
	}

	private static final class InMemoryStorage
			implements BankTagLayoutStorage
	{
		private final String serializedLayout;

		private InMemoryStorage(
				String serializedLayout)
		{
			this.serializedLayout =
					serializedLayout;
		}

		@Override
		public String read(String bankTagName)
		{
			return serializedLayout;
		}
	}
}
