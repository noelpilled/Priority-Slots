package com.priorityslots;

import com.google.inject.Provides;
import com.priorityslots.bank.BankSnapshotFactory;
import com.priorityslots.banktags.BankTagLayoutReader;
import com.priorityslots.banktags.BankTagLayoutSnapshot;
import com.priorityslots.banktags.BankTagProjector;
import com.priorityslots.domain.BankSnapshot;
import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.CellPlacement;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import com.priorityslots.persistence.PriorityStateStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.util.Text;

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

	@Inject
	private PrioritySlotsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BankTagLayoutReader bankTagLayoutReader;

	@Inject
	private BankTagProjector bankTagProjector;

	private PriorityState priorityState =
			PriorityState.empty();

	private BankSnapshot bankSnapshot =
			BankSnapshot.empty();

	@Provides
	PrioritySlotsConfig provideConfig(
			ConfigManager configManager)
	{
		return configManager.getConfig(
				PrioritySlotsConfig.class
		);
	}

	@Override
	protected void startUp()
	{
		reloadPriorityState();

		log.debug("Priority Slots started");
	}

	@Override
	protected void shutDown()
	{
		if (bankTagProjector != null)
		{
			bankTagProjector.unregisterAll();
		}

		priorityState = PriorityState.empty();
		bankSnapshot = BankSnapshot.empty();

		log.debug("Priority Slots stopped");
	}

	@Subscribe
	public void onProfileChanged(
			ProfileChanged profileChanged)
	{
		bankTagProjector.unregisterAll();

		reloadPriorityState();
	}

	@Subscribe
	public void onConfigChanged(
			ConfigChanged event)
	{
		if (!PrioritySlotsConfig.GROUP.equals(
				event.getGroup()))
		{
			return;
		}

		if (!PrioritySlotsConfig.APPLY_KEY.equals(
				event.getKey()))
		{
			return;
		}

		if (!Boolean.parseBoolean(
				event.getNewValue()))
		{
			return;
		}

		configManager.setConfiguration(
				PrioritySlotsConfig.GROUP,
				PrioritySlotsConfig.APPLY_KEY,
				false
		);

		clientThread.invokeLater(
				this::applyMvpSlot
		);
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

		priorityState =
				bankTagProjector.synchronize(
						priorityState,
						bankSnapshot
				);

		log.debug(
				"Captured bank snapshot with {} exact item IDs",
				bankSnapshot.distinctItemCount()
		);
	}

	@Subscribe(priority = -1f)
	public void onScriptPostFired(
			ScriptPostFired event)
	{
		if (event.getScriptId()
				!= ScriptID.BANKMAIN_BUILD)
		{
			return;
		}

		priorityState =
				bankTagProjector
						.reconcileActiveLayout(
								priorityState
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

		/*
		 * Startup can occur on the Swing event thread.
		 * Layout projection must run on RuneLite's
		 * client thread.
		 */
		clientThread.invokeLater(() ->
				priorityState =
						bankTagProjector.synchronize(
								priorityState,
								bankSnapshot
						)
		);
	}

	private void applyMvpSlot()
	{
		try
		{
			String bankTagName =
					config.bankTagName().trim();

			if (bankTagName.isEmpty())
			{
				throw new IllegalArgumentException(
						"Bank Tag name must not be blank"
				);
			}

			int layoutIndex =
					config.layoutIndex();

			if (layoutIndex < 0)
			{
				throw new IllegalArgumentException(
						"Layout index must not be negative"
				);
			}

			List<Integer> exactItemIds =
					parseExactItemIds(
							config.orderedExactItemIds()
					);

			Optional<BankTagLayoutSnapshot>
					loadedLayout =
					bankTagLayoutReader.load(
							bankTagName
					);

			if (!loadedLayout.isPresent())
			{
				throw new IllegalArgumentException(
						"Bank Tags layout does not exist: "
								+ bankTagName
				);
			}

			OptionalInt currentItem =
					loadedLayout.get().itemAt(
							layoutIndex
					);

			if (!currentItem.isPresent())
			{
				throw new IllegalArgumentException(
						"Layout index is outside the "
								+ "saved Bank Tags layout"
				);
			}

			int fallbackExactItemId =
					currentItem.getAsInt();

			if (fallbackExactItemId <= 0)
			{
				throw new IllegalArgumentException(
						"Target layout cell must contain "
								+ "a real item before it can "
								+ "become a priority slot"
				);
			}

			List<PriorityTier> tiers =
					new ArrayList<>();

			for (Integer exactItemId
					: exactItemIds)
			{
				tiers.add(
						PriorityTier.create(
								List.of(exactItemId)
						)
				);
			}

			PriorityDefinition definition =
					PriorityDefinition.create(
							"MVP "
									+ bankTagName
									+ " slot "
									+ layoutIndex,
							tiers
					);

			CellPlacement placement =
					CellPlacement.create(
							definition.getId(),
							layoutIndex
					);

			BankTagSlotBinding slot =
					BankTagSlotBinding.create(
							placement,
							fallbackExactItemId
					);

			BankTagBinding binding =
					BankTagBinding.create(
							bankTagName,
							List.of(slot)
					);

			PriorityState newState =
					new PriorityState(
							List.of(definition),
							List.of(),
							List.of(binding)
					);

			priorityStateStore.save(newState);

			priorityState =
					bankTagProjector.synchronize(
							newState,
							bankSnapshot
					);

			log.info(
					"Applied MVP priority slot to "
							+ "Bank Tag '{}' at index {} "
							+ "with {} priority item IDs",
					bankTagName,
					layoutIndex,
					exactItemIds.size()
			);
		}
		catch (RuntimeException exception)
		{
			log.warn(
					"Unable to apply MVP priority slot",
					exception
			);
		}
	}

	private static List<Integer> parseExactItemIds(
			String serializedItemIds)
	{
		List<Integer> result =
				new ArrayList<>();

		Set<Integer> seen =
				new HashSet<>();

		for (String value
				: Text.fromCSV(serializedItemIds))
		{
			int exactItemId;

			try
			{
				exactItemId =
						Integer.parseInt(value);
			}
			catch (NumberFormatException exception)
			{
				throw new IllegalArgumentException(
						"Priority item IDs must be integers",
						exception
				);
			}

			if (exactItemId <= 0)
			{
				throw new IllegalArgumentException(
						"Priority item IDs must be positive"
				);
			}

			if (!seen.add(exactItemId))
			{
				throw new IllegalArgumentException(
						"Duplicate priority item ID: "
								+ exactItemId
				);
			}

			result.add(exactItemId);
		}

		if (result.isEmpty())
		{
			throw new IllegalArgumentException(
					"At least one priority item ID "
							+ "is required"
			);
		}

		return List.copyOf(result);
	}
}
