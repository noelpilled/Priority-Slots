package com.priorityslots.banktags;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectionRefreshPolicyTest
{
	@Test
	public void unchangedActiveProjectionDoesNotRefresh()
	{
		assertFalse(ProjectionRefreshPolicy.shouldRefresh(
			true,
			false,
			false
		));
	}

	@Test
	public void activeLayoutOrMembershipChangeRefreshes()
	{
		assertTrue(ProjectionRefreshPolicy.shouldRefresh(
			true,
			true,
			false
		));
		assertTrue(ProjectionRefreshPolicy.shouldRefresh(
			true,
			false,
			true
		));
	}

	@Test
	public void inactiveTagDoesNotRefreshVisibleBank()
	{
		assertFalse(ProjectionRefreshPolicy.shouldRefresh(
			false,
			true,
			true
		));
	}
}
