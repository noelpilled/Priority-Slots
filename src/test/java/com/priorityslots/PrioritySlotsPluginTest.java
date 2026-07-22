package com.priorityslots;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PrioritySlotsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PrioritySlotsPlugin.class);
		RuneLite.main(args);
	}
}