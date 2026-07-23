package com.priorityslots.bank;

import com.priorityslots.domain.BankSnapshot;
import net.runelite.api.Item;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BankSnapshotFactoryTest
{
	private static final int HIGH_PRIORITY_ITEM = 1005;
	private static final int LOW_PRIORITY_ITEM = 1003;

	private final BankSnapshotFactory factory =
			new BankSnapshotFactory();

	@Test
	public void createsSnapshotFromItems()
	{
		Item[] items = {
				new Item(HIGH_PRIORITY_ITEM, 1),
				new Item(LOW_PRIORITY_ITEM, 4)
		};

		BankSnapshot snapshot =
				factory.create(items);

		assertEquals(
				1,
				snapshot.quantityOf(
						HIGH_PRIORITY_ITEM
				)
		);
		assertEquals(
				4,
				snapshot.quantityOf(
						LOW_PRIORITY_ITEM
				)
		);
	}

	@Test
	public void keepsExactItemIdsSeparate()
	{
		Item[] items = {
				new Item(HIGH_PRIORITY_ITEM, 1),
				new Item(LOW_PRIORITY_ITEM, 1)
		};

		BankSnapshot snapshot =
				factory.create(items);

		assertTrue(
				snapshot.contains(
						HIGH_PRIORITY_ITEM
				)
		);
		assertTrue(
				snapshot.contains(
						LOW_PRIORITY_ITEM
				)
		);
		assertFalse(snapshot.contains(1004));
	}

	@Test
	public void combinesDuplicateExactItemIds()
	{
		Item[] items = {
				new Item(HIGH_PRIORITY_ITEM, 2),
				new Item(HIGH_PRIORITY_ITEM, 3)
		};

		BankSnapshot snapshot =
				factory.create(items);

		assertEquals(
				5,
				snapshot.quantityOf(
						HIGH_PRIORITY_ITEM
				)
		);
	}

	@Test
	public void skipsEmptyAndInvalidEntries()
	{
		Item[] items = {
				null,
				new Item(-1, 1),
				new Item(HIGH_PRIORITY_ITEM, 0),
				new Item(LOW_PRIORITY_ITEM, 2)
		};

		BankSnapshot snapshot =
				factory.create(items);

		assertFalse(
				snapshot.contains(
						HIGH_PRIORITY_ITEM
				)
		);
		assertTrue(
				snapshot.contains(
						LOW_PRIORITY_ITEM
				)
		);
		assertEquals(
				2,
				snapshot.quantityOf(
						LOW_PRIORITY_ITEM
				)
		);
	}

	@Test
	public void createsEmptySnapshotFromEmptyArray()
	{
		BankSnapshot snapshot =
				factory.create(new Item[0]);

		assertFalse(
				snapshot.contains(
						HIGH_PRIORITY_ITEM
				)
		);
		assertEquals(
				0,
				snapshot.quantityOf(
						HIGH_PRIORITY_ITEM
				)
		);
	}
}
