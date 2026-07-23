package com.priorityslots;

import com.priorityslots.banktags.BankTagProjector;
import com.priorityslots.bank.BankSnapshotFactory;
import com.priorityslots.domain.BankSnapshot;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.persistence.PriorityStateStore;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.banktags.BankTagsPlugin;

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
@PluginDependency(BankTagsPlugin.class)
public class PrioritySlotsPlugin extends Plugin
{
	private final BankSnapshotFactory bankSnapshotFactory =
			new BankSnapshotFactory();

	@Inject
	private PriorityStateStore priorityStateStore;

	private PriorityState priorityState =
			PriorityState.empty();

	private BankSnapshot bankSnapshot =
			BankSnapshot.empty();

	private BankTagProjector bankTagProjector;

	@Override
	protected void startUp()
	{
		reloadPriorityState();

		log.debug("Priority Slots started");
	}

	@Override
	protected void shutDown()
	{
		bankTagProjector.unregisterAll();
		priorityState = PriorityState.empty();
		bankSnapshot = BankSnapshot.empty();

		log.debug("Priority Slots stopped");
	}

	@Subscribe
	public void onProfileChanged(
			ProfileChanged profileChanged)
	{
		reloadPriorityState();
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

		priorityState = bankTagProjector.synchronize(
				priorityState,
				bankSnapshot
		);

		log.debug(
				"Captured bank snapshot with {} exact item IDs",
				bankSnapshot.distinctItemCount()
		);
	}

	private void reloadPriorityState()
	{
		priorityState = priorityStateStore.load();

		log.debug(
				"Loaded Priority Slots state with "
						+ "{} definitions, {} groups, "
						+ "and {} bank tag bindings",
				priorityState.getDefinitions().size(),
				priorityState.getGroups().size(),
				priorityState.getBindings().size()
		);

		priorityState = bankTagProjector.synchronize(
				priorityState,
				bankSnapshot
		);
	}
}
