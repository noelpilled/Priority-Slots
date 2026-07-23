package com.priorityslots;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PrioritySlotsConfig.GROUP)
public interface PrioritySlotsConfig extends Config
{
	String GROUP = "priorityslots";
	String APPLY_KEY = "applyMvpSlot";

	@ConfigItem(
			keyName = "bankTagName",
			name = "Bank Tag name",
			description =
					"Existing Bank Tags tab containing the target layout"
	)
	default String bankTagName()
	{
		return "";
	}

	@ConfigItem(
			keyName = "layoutIndex",
			name = "Layout index",
			description =
					"Zero-based layout position; the first cell is 0"
	)
	default int layoutIndex()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "orderedExactItemIds",
			name = "Priority item IDs",
			description =
					"Comma-separated exact item IDs, highest priority first"
	)
	default String orderedExactItemIds()
	{
		return "";
	}

	@ConfigItem(
			keyName = APPLY_KEY,
			name = "Apply MVP slot",
			description =
					"Toggle this on to replace the current Priority Slots state"
	)
	default boolean applyMvpSlot()
	{
		return false;
	}
}
