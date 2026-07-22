package com.priorityslots;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
		name = "Priority Slots",
		description = "Dynamic bank-layout slots that display the highest-priority item you currently own.",
		tags = {
				"bank",
				"layout",
				"gear",
				"priority"
		}
)
public class PrioritySlotsPlugin extends Plugin
{
	@Override
	protected void startUp()
	{
		log.debug("Priority Slots started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("Priority Slots stopped");
	}

	@Provides
	PrioritySlotsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PrioritySlotsConfig.class);
	}
}