package com.priorityslots.banktags;

import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PriorityBankTagTest
{
	@Test
	public void reportsOnlyRealMembershipChanges()
	{
		PriorityBankTag tag = new PriorityBankTag();

		assertFalse(tag.replaceItems(Set.of()));
		assertTrue(tag.replaceItems(Set.of(1005)));
		assertFalse(tag.replaceItems(Set.of(1005)));
		assertTrue(tag.replaceItems(Set.of(1003)));

		assertFalse(tag.contains(1005));
		assertTrue(tag.contains(1003));
	}
}
