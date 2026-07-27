package com.priorityslots;

import com.google.inject.Provides;
import com.priorityslots.authoring.PrioritySlotAuthoringService;
import com.priorityslots.bank.BankSnapshotFactory;
import com.priorityslots.banktags.BankTagLayoutReader;
import com.priorityslots.banktags.BankTagLayoutSnapshot;
import com.priorityslots.banktags.BankTagProjector;
import com.priorityslots.domain.BankSnapshot;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.itemsearch.PriorityItemSearchService;
import com.priorityslots.lifecycle.LifecycleGeneration;
import com.priorityslots.persistence.PriorityStateStore;
import com.priorityslots.ui.PrioritySlotsIcon;
import com.priorityslots.ui.PrioritySlotsPanel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
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
	private static final String REMOVE_PRIORITY_SLOT =
			"Remove-priority-slot";

	private static final String REMOVE_TAG_PREFIX =
			"Remove-tag";

	private static final String REMOVE_LAYOUT =
			"Remove-layout";

	private final LifecycleGeneration lifecycle =
			new LifecycleGeneration();

	private final BankSnapshotFactory bankSnapshotFactory =
			new BankSnapshotFactory();

	@Inject
	private Client client;

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

	@Inject
	private PrioritySlotAuthoringService authoringService;

	@Inject
	private PriorityItemSearchService itemSearchService;

	@Inject
	private PrioritySlotsPanel prioritySlotsPanel;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private LayoutManager layoutManager;

	@Inject
	private BankSearch bankSearch;

	private PriorityState priorityState =
			PriorityState.empty();

	private BankSnapshot bankSnapshot =
			BankSnapshot.empty();

	private boolean bankSnapshotKnown;
	private NavigationButton navigationButton;

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
		lifecycle.activate();
		bankTagProjector.activate();
		configureSidebar();
		reloadPriorityState();

		/*
		 * Re-enabling the plugin while the bank is already open
		 * may not produce a new ItemContainerChanged event.
		 */
		invokeOnClientThreadWhileActive(
				this::synchronizeFromCurrentBankIfAvailable
		);

		log.debug("Priority Slots started");
	}

	@Override
	protected void shutDown()
	{
		lifecycle.deactivate();

		if (bankTagProjector != null)
		{
			bankTagProjector.deactivate();
		}

		removeSidebar();

		priorityState = PriorityState.empty();
		bankSnapshot = BankSnapshot.empty();
		bankSnapshotKnown = false;

		log.debug("Priority Slots stopped");
	}

	@Subscribe
	public void onProfileChanged(
			ProfileChanged profileChanged)
	{
		long generation = lifecycle.advanceGeneration();

		invokeOnClientThreadWhileActive(
				generation,
				this::reloadForProfileChange
		);
	}

	private void reloadForProfileChange()
	{
		bankTagProjector.resetRuntimeState();

		priorityState = PriorityState.empty();
		bankSnapshot = BankSnapshot.empty();
		bankSnapshotKnown = false;

		/*
		 * Do not inspect the current bank container here.
		 * During a profile transition it may still belong
		 * to the previous profile. Wait for the next real
		 * bank container event instead.
		 */
		reloadPriorityState();
	}

	@Subscribe
	public void onConfigChanged(
			ConfigChanged event)
	{
		if (BankTagsPlugin.CONFIG_GROUP.equals(event.getGroup()))
		{
			String key = event.getKey();
			String newValue = event.getNewValue();

			invokeOnClientThreadWhileActive(() ->
					handleBankTagsMembershipChange(
							key,
							newValue
					)
			);
			return;
		}

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

		invokeOnClientThreadWhileActive(
				this::applyMvpSlot
		);
	}

	private void handleBankTagsMembershipChange(
			String configKey,
			String newValue)
	{
		boolean cleanupInvalidated = bankTagProjector
				.invalidateOrdinaryMembershipCleanup(
						priorityState,
						configKey,
						newValue
				);

		if (!cleanupInvalidated || !bankSnapshotKnown)
		{
			return;
		}

		priorityState = bankTagProjector.synchronize(
				priorityState,
				bankSnapshot
		);
	}

	private void invokeOnClientThreadWhileActive(
			Runnable action)
	{
		invokeOnClientThreadWhileActive(
				lifecycle.currentGeneration(),
				action
		);
	}

	private void invokeOnClientThreadWhileActive(
			long generation,
			Runnable action)
	{
		clientThread.invokeLater(() ->
		{
			if (lifecycle.isCurrent(generation, true))
			{
				action.run();
			}
		});
	}

	@Subscribe
	public void onItemContainerChanged(
			ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK)
		{
			return;
		}

		captureBankSnapshot(
				event.getItemContainer()
		);
	}


	@Subscribe(priority = -1f)
	public void onMenuEntryAdded(
			MenuEntryAdded event)
	{
		if (event.getActionParam1()
				!= InterfaceID.Bankmain.ITEMS
				|| !("Examine".equals(event.getOption())
				|| "Withdraw-All-but-1".equals(
						event.getOption())))
		{
			return;
		}

		String activeTag = bankTagsService.getActiveTag();
		Layout activeLayout =
				bankTagsService.getActiveLayout();

		if (activeTag == null || activeLayout == null)
		{
			return;
		}

		int layoutIndex = event.getActionParam0();

		Optional<String> installedCellId =
			authoringService.installedCellIdAt(
				priorityState,
				activeTag,
				layoutIndex
			);

		if (!installedCellId.isPresent())
		{
			return;
		}

		String cellId = installedCellId.get();

		MenuEntry[] entries =
				client.getMenu().getMenuEntries();

		for (int index = entries.length - 1;
			 index >= 0;
			 index--)
		{
			MenuEntry entry = entries[index];
			String option = entry.getOption();

			if (entry.getParam0() != layoutIndex
					|| entry.getParam1()
					!= InterfaceID.Bankmain.ITEMS
					|| !(option.startsWith(
						REMOVE_TAG_PREFIX)
					|| REMOVE_LAYOUT.equals(option)))
			{
				continue;
			}

			entry.setOption(REMOVE_PRIORITY_SLOT);
			entry.setType(MenuAction.RUNELITE);
			entry.onClick(clicked ->
					invokeOnClientThreadWhileActive(() ->
							removePrioritySlotFromActiveLayout(
									activeTag,
									cellId
							)
					)
			);

			return;
		}
	}

	@Subscribe(priority = -1f)
	public void onScriptPostFired(
			ScriptPostFired event)
	{
		if (event.getScriptId()
				!= ScriptID.BANKMAIN_BUILD
				|| !bankSnapshotKnown)
		{
			return;
		}

		priorityState =
				bankTagProjector
						.reconcileActiveLayout(
								priorityState
						);
	}

	private void configureSidebar()
	{
		prioritySlotsPanel.setListener(
				new PrioritySlotsPanel.Listener()
				{

					@Override
					public void createDefinition(String name)
					{
						invokeOnClientThreadWhileActive(() ->
							createDefinitionFromPanel(name)
						);
					}

					@Override
					public void renameDefinition(
						String definitionId,
						String name)
					{
						invokeOnClientThreadWhileActive(() ->
							renameDefinitionFromPanel(
								definitionId,
								name
							)
						);
					}

					@Override
					public void searchItems(
						String definitionId,
						String query)
					{
						invokeOnClientThreadWhileActive(() ->
							searchItemsFromPanel(
								definitionId,
								query
							)
						);
					}

					@Override
					public void addCandidateTier(
						String definitionId,
						int exactItemId)
					{
						invokeOnClientThreadWhileActive(() ->
							addCandidateFromPanel(
								definitionId,
								exactItemId
							)
						);
					}

					@Override
					public void removeCandidateTier(
						String definitionId,
						String tierId)
					{
						invokeOnClientThreadWhileActive(() ->
							removeCandidateFromPanel(
								definitionId,
								tierId
							)
						);
					}

					@Override
					public void deleteDefinition(String definitionId)
					{
						invokeOnClientThreadWhileActive(() ->
							deleteDefinitionFromPanel(definitionId)
						);
					}

					@Override
					public void moveGroup(
							String groupId,
							String targetParentGroupId,
							int targetIndex)
					{
						invokeOnClientThreadWhileActive(() ->
								moveGroupFromPanel(
										groupId,
										targetParentGroupId,
										targetIndex
								)
						);
					}

					@Override
					public void moveDefinition(
							String definitionId,
							String targetParentGroupId,
							int targetIndex)
					{
						invokeOnClientThreadWhileActive(() ->
								moveDefinitionFromPanel(
										definitionId,
										targetParentGroupId,
										targetIndex
								)
						);
					}

					@Override
					public void moveCandidateTier(
							String definitionId,
							String tierId,
							int targetIndex)
					{
						invokeOnClientThreadWhileActive(() ->
								moveCandidateFromPanel(
										definitionId,
										tierId,
										targetIndex
								)
						);
					}

					@Override
					public void installDefinition(
							String definitionId)
					{
						invokeOnClientThreadWhileActive(() ->
								installDefinitionFromPanel(
										definitionId
								)
						);
					}
				}
		);

		navigationButton = NavigationButton.builder()
				.tooltip("Priority Slots")
				.icon(PrioritySlotsIcon.create())
				.priority(5)
				.panel(prioritySlotsPanel)
				.build();

		clientToolbar.addNavigation(navigationButton);
	}

	private void removeSidebar()
	{
		prioritySlotsPanel.setListener(null);
		prioritySlotsPanel.setState(PriorityState.empty());

		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
	}


	private void createDefinitionFromPanel(String name)
	{
		try
		{
			PrioritySlotAuthoringService.CreateDefinitionResult result =
				authoringService.createDefinition(
					priorityState,
					name,
					List.of(),
					null,
					priorityState.getRootEntries().size()
				);

			applyAuthoringState(result.getState());
			prioritySlotsPanel.openDefinition(
				result.getDefinition().getId()
			);
			prioritySlotsPanel.showMessage(
				"Created " + result.getDefinition().getName() + ".",
				false
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
				"Unable to create definition",
				exception
			);
		}
	}

	private void renameDefinitionFromPanel(
		String definitionId,
		String name)
	{
		try
		{
			applyAuthoringState(
				authoringService.renameDefinition(
					priorityState,
					definitionId,
					name
				)
			);

			prioritySlotsPanel.showMessage(
				"Definition renamed.",
				false
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
				"Unable to rename definition",
				exception
			);
		}
	}

	private void searchItemsFromPanel(
		String definitionId,
		String query)
	{
		try
		{
			if (!priorityState.definitionsById().containsKey(
				definitionId))
			{
				throw new IllegalArgumentException(
					"Priority definition no longer exists."
				);
			}

			prioritySlotsPanel.showItemSearchResults(
				definitionId,
				query,
				itemSearchService.search(query)
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
				"Unable to search items",
				exception
			);
		}
	}

	private void addCandidateFromPanel(
		String definitionId,
		int exactItemId)
	{
		try
		{
			applyAuthoringState(
				authoringService.addCandidateTier(
					priorityState,
					definitionId,
					exactItemId
				)
			);

			prioritySlotsPanel.showMessage(
				"Priority item added.",
				false
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
				"Unable to add priority item",
				exception
			);
		}
	}

	private void removeCandidateFromPanel(
		String definitionId,
		String tierId)
	{
		try
		{
			applyAuthoringState(
				authoringService.removeCandidateTier(
					priorityState,
					definitionId,
					tierId
				)
			);

			prioritySlotsPanel.showMessage(
				"Priority item removed.",
				false
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
				"Unable to remove priority item",
				exception
			);
		}
	}

	private void deleteDefinitionFromPanel(String definitionId)
	{
		try
		{
			applyAuthoringState(
				authoringService.deleteDefinition(
					priorityState,
					definitionId
				)
			);

			prioritySlotsPanel.showMessage(
				"Definition deleted.",
				false
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
				"Unable to delete definition",
				exception
			);
		}
	}

	private void moveGroupFromPanel(
			String groupId,
			String targetParentGroupId,
			int targetIndex)
	{
		try
		{
			applyAuthoringState(
					authoringService.moveGroup(
							priorityState,
							groupId,
							targetParentGroupId,
							targetIndex
					)
			);

			prioritySlotsPanel.showMessage(
					"Group order updated.",
					false
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
					"Unable to move group",
					exception
			);
		}
	}

	private void moveDefinitionFromPanel(
			String definitionId,
			String targetParentGroupId,
			int targetIndex)
	{
		try
		{
			applyAuthoringState(
					authoringService.moveDefinition(
							priorityState,
							definitionId,
							targetParentGroupId,
							targetIndex
					)
			);

			prioritySlotsPanel.showMessage(
					"Definition order updated.",
					false
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
					"Unable to move definition",
					exception
			);
		}
	}

	private void moveCandidateFromPanel(
			String definitionId,
			String tierId,
			int targetIndex)
	{
		try
		{
			applyAuthoringState(
					authoringService.moveCandidateTier(
							priorityState,
							definitionId,
							tierId,
							targetIndex
					)
			);

			prioritySlotsPanel.showMessage(
					"Candidate priority updated.",
					false
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
					"Unable to reorder candidate",
					exception
			);
		}
	}

	private void installDefinitionFromPanel(
			String definitionId)
	{
		try
		{
			String activeTag = bankTagsService.getActiveTag();
			Layout activeLayout =
					bankTagsService.getActiveLayout();

			if (activeTag == null || activeLayout == null)
			{
				throw new IllegalStateException(
						"Open a Bank Tag tab with a layout "
								+ "before adding a priority definition."
				);
			}

			PriorityDefinition definition =
					priorityState.definitionsById().get(
							definitionId
					);

			if (definition == null)
			{
				throw new IllegalArgumentException(
						"Priority definition no longer exists."
				);
			}

			PrioritySlotAuthoringService.InstallationResult
					result = authoringService
							.installDefinitionInActiveLayout(
									priorityState,
									activeTag,
									layoutItems(activeLayout),
									definitionId
							);

			applyLayoutAndAuthoringState(
					activeLayout,
					result.getLayoutItems(),
					result.getState()
			);

			if (!bankSnapshotKnown)
			{
				bankSearch.layoutBank();
			}

			prioritySlotsPanel.showMessage(
					"Added "
							+ definition.getName()
							+ " to "
							+ activeTag
							+ ".",
					false
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
					"Unable to add definition",
					exception
			);
		}
	}


	private void removePrioritySlotFromActiveLayout(
			String expectedBankTagName,
			String cellId)
	{
		try
		{
			String activeTag = bankTagsService.getActiveTag();
			Layout activeLayout =
					bankTagsService.getActiveLayout();

			if (activeTag == null
					|| activeLayout == null
					|| !Text.standardize(activeTag).equals(
							Text.standardize(
									expectedBankTagName)))
			{
				throw new IllegalStateException(
						"The Bank Tag changed before the "
								+ "priority slot was removed."
				);
			}

			priorityState = bankTagProjector
					.reconcileActiveLayout(priorityState);

			PrioritySlotAuthoringService.RemovalResult result =
					authoringService
							.removeInstalledSlotFromActiveLayout(
									priorityState,
									activeTag,
									layoutItems(activeLayout),
									cellId
							);

			PriorityDefinition definition =
					priorityState.definitionsById().get(
							result.getDefinitionId()
					);

			applyLayoutAndAuthoringState(
					activeLayout,
					result.getLayoutItems(),
					result.getState()
			);

			bankTagsService.openBankTag(
					activeTag,
					BankTagsService.OPTION_ALLOW_MODIFICATIONS
			);
			bankSearch.layoutBank();

			prioritySlotsPanel.showMessage(
					"Removed "
							+ (definition == null
									? "priority slot"
									: definition.getName())
							+ " from "
							+ activeTag
							+ (result.isLayoutItemCleared()
									? "."
									: "; current layout item preserved."),
					false
			);
		}
		catch (RuntimeException exception)
		{
			showAuthoringFailure(
					"Unable to remove priority slot",
					exception
			);
		}
	}

	private void applyLayoutAndAuthoringState(
			Layout layout,
			List<Integer> updatedLayoutItems,
			PriorityState updatedState)
	{
		List<Integer> previousLayoutItems = layoutItems(layout);
		PriorityState previousState = priorityState;

		LayoutStateTransaction.execute(
				() ->
				{
					applyLayoutItems(layout, updatedLayoutItems);
					layoutManager.saveLayout(layout);
				},
				() -> applyAuthoringState(updatedState),
				() ->
				{
					applyLayoutItems(layout, previousLayoutItems);
					layoutManager.saveLayout(layout);
				},
				() -> restoreAuthoringState(previousState)
		);
	}

	private void restoreAuthoringState(
			PriorityState previousState)
	{
		priorityStateStore.save(previousState);
		priorityState = previousState;

		if (bankSnapshotKnown)
		{
			priorityState = bankTagProjector.synchronize(
					priorityState,
					bankSnapshot
			);
		}

		prioritySlotsPanel.setState(priorityState);
	}

	private void applyAuthoringState(
			PriorityState updatedState)
	{
		priorityStateStore.save(updatedState);
		priorityState = updatedState;

		if (bankSnapshotKnown)
		{
			priorityState = bankTagProjector.synchronize(
					priorityState,
					bankSnapshot
			);
		}

		prioritySlotsPanel.setState(priorityState);
	}

	private void showAuthoringFailure(
			String action,
			RuntimeException exception)
	{
		String message = exception.getMessage();

		if (message == null || message.trim().isEmpty())
		{
			message = action + ".";
		}

		prioritySlotsPanel.showMessage(message, true);
		log.debug(action, exception);
	}

	private static List<Integer> layoutItems(Layout layout)
	{
		int[] values = layout.getLayout();
		List<Integer> result =
				new ArrayList<>(values.length);

		for (int value : values)
		{
			result.add(value);
		}

		return List.copyOf(result);
	}

	private static void applyLayoutItems(
			Layout layout,
			List<Integer> itemIds)
	{
		layout.resize(itemIds.size());

		for (int index = 0;
			 index < itemIds.size();
			 index++)
		{
			layout.setItemAtPos(
					itemIds.get(index),
					index
			);
		}
	}

	private void reloadPriorityState()
	{
		priorityState = priorityStateStore.load();
		prioritySlotsPanel.setState(priorityState);

		log.debug(
				"Loaded Priority Slots state with "
						+ "{} definitions, {} groups, "
						+ "and {} bank tag bindings",
				priorityState.getDefinitions().size(),
				priorityState.getGroups().size(),
				priorityState.getBindings().size()
		);
	}

	private void synchronizeFromCurrentBankIfAvailable()
	{
		ItemContainer currentBank =
				client.getItemContainer(
						InventoryID.BANK
				);

		if (currentBank == null)
		{
			log.debug(
					"Bank snapshot is not available; "
							+ "Priority Slots projection is deferred"
			);

			return;
		}

		captureBankSnapshot(currentBank);
	}

	private void captureBankSnapshot(
			ItemContainer itemContainer)
	{
		bankSnapshot = bankSnapshotFactory.create(
				itemContainer
		);

		bankSnapshotKnown = true;

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

			PrioritySlotAuthoringService.CreateDefinitionResult
					createdDefinition =
					authoringService.createDefinition(
							priorityState,
							"MVP "
									+ bankTagName
									+ " slot "
									+ layoutIndex,
							exactItemIds,
							null,
							priorityState.getRootEntries().size()
					);

			PriorityState newState =
					authoringService.attachDefinitionToLayoutCell(
							createdDefinition.getState(),
							bankTagName,
							layoutIndex,
							fallbackExactItemId,
							createdDefinition.getDefinition().getId()
					);

			applyAuthoringState(newState);

			if (!bankSnapshotKnown)
			{
				log.debug(
						"Saved MVP priority slot; "
								+ "projection is deferred until "
								+ "a bank snapshot is available"
				);
			}

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
