package com.priorityslots;

import com.priorityslots.bank.BankSnapshotFactory;
import com.priorityslots.domain.BankSnapshot;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
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
	private final BankSnapshotFactory bankSnapshotFactory =
			new BankSnapshotFactory();

	private BankSnapshot bankSnapshot =
			BankSnapshot.empty();

	@Override
	protected void startUp()
	{
		log.debug("Priority Slots started");
	}

	@Override
	protected void shutDown()
	{
		bankSnapshot = BankSnapshot.empty();
		log.debug("Priority Slots stopped");
	}

	@Subscribe
	public void onItemContainerChanged(
			ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK)
		{
			return;
		}

		bankSnapshot = bankSnapshotFactory.create(
				event.getItemContainer()
		);

		log.debug(
				"Captured bank snapshot with {} exact item IDs",
				bankSnapshot.distinctItemCount()
		);
	}
}
