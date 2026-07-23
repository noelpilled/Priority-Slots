package com.priorityslots.banktags;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.util.Text;

@Singleton
public final class ConfigBankTagLayoutStorage
		implements BankTagLayoutStorage
{
	private final ConfigManager configManager;

	@Inject
	public ConfigBankTagLayoutStorage(
			ConfigManager configManager)
	{
		this.configManager =
				Objects.requireNonNull(
						configManager,
						"configManager"
				);
	}

	@Override
	public String read(String bankTagName)
	{
		Objects.requireNonNull(
				bankTagName,
				"bankTagName"
		);

		String standardizedTag =
				Text.standardize(bankTagName);

		if (standardizedTag.isEmpty())
		{
			throw new IllegalArgumentException(
					"bankTagName must not be blank"
			);
		}

		return configManager.getConfiguration(
				BankTagsPlugin.CONFIG_GROUP,
				BankTagsPlugin.TAG_LAYOUT_PREFIX
						+ standardizedTag
		);
	}
}
