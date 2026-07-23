package com.priorityslots.persistence;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
public final class ConfigPriorityStateStorage
		implements PriorityStateStorage
{
	static final String CONFIG_GROUP =
			"priorityslots";

	static final String STATE_KEY =
			"state";

	private final ConfigManager configManager;

	@Inject
	public ConfigPriorityStateStorage(
			ConfigManager configManager)
	{
		this.configManager =
				Objects.requireNonNull(
						configManager,
						"configManager"
				);
	}

	@Override
	public String read()
	{
		return configManager.getConfiguration(
				CONFIG_GROUP,
				STATE_KEY
		);
	}

	@Override
	public void write(String serializedState)
	{
		Objects.requireNonNull(
				serializedState,
				"serializedState"
		);

		configManager.setConfiguration(
				CONFIG_GROUP,
				STATE_KEY,
				serializedState
		);
	}

	@Override
	public void clear()
	{
		configManager.unsetConfiguration(
				CONFIG_GROUP,
				STATE_KEY
		);
	}
}
