package com.priorityslots.banktags;

final class ProjectionRefreshPolicy
{
	private ProjectionRefreshPolicy()
	{
	}

	static boolean shouldRefresh(
		boolean activeTag,
		boolean layoutChanged,
		boolean dynamicMembershipChanged)
	{
		return activeTag
			&& (layoutChanged || dynamicMembershipChanged);
	}
}
