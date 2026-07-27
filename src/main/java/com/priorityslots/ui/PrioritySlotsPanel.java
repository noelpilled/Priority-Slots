package com.priorityslots.ui;

import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityGroup;
import com.priorityslots.domain.PriorityLibraryEntry;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import com.priorityslots.itemsearch.PriorityItemSearchResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

@Singleton
public final class PrioritySlotsPanel extends PluginPanel
{
	private static final int INDENT_WIDTH = 14;
	private static final int ITEM_ICON_SIZE = 36;
	private static final int MAX_PREVIEW_ITEMS = 3;

	private static final Color SUCCESS_COLOR =
		new Color(115, 200, 125);

	private static final Color ERROR_COLOR =
		new Color(230, 105, 105);


	private static final Listener NOOP_LISTENER =
		new Listener()
		{

			@Override
			public void createDefinition(String name)
			{
			}

			@Override
			public void renameDefinition(
				String definitionId,
				String name)
			{
			}

			@Override
			public void searchItems(
				String definitionId,
				String query)
			{
			}

			@Override
			public void addCandidateTier(
				String definitionId,
				int exactItemId)
			{
			}

			@Override
			public void removeCandidateTier(
				String definitionId,
				String tierId)
			{
			}

			@Override
			public void deleteDefinition(String definitionId)
			{
			}

			@Override
			public void moveGroup(
				String groupId,
				String targetParentGroupId,
				int targetIndex)
			{
			}

			@Override
			public void moveDefinition(
				String definitionId,
				String targetParentGroupId,
				int targetIndex)
			{
			}

			@Override
			public void moveCandidateTier(
				String definitionId,
				String tierId,
				int targetIndex)
			{
			}

			@Override
			public void installDefinition(
				String definitionId)
			{
			}
		};

	private final ItemManager itemManager;
	private final PriorityItemNameCache itemNameCache;
	private final JLabel statusLabel = new JLabel(" ");
	private final Set<String> collapsedGroupIds =
		new HashSet<>();

	private volatile Listener listener = NOOP_LISTENER;
	private PriorityState state = PriorityState.empty();
	private String selectedDefinitionId;

	@Inject
	public PrioritySlotsPanel(
		ItemManager itemManager,
		ClientThread clientThread)
	{
		this.itemManager = Objects.requireNonNull(
			itemManager,
			"itemManager"
		);

		ClientThread requiredClientThread =
			Objects.requireNonNull(
				clientThread,
				"clientThread"
			);

		this.itemNameCache = new PriorityItemNameCache(
			requiredClientThread::invokeLater,
			exactItemId -> this.itemManager
				.getItemComposition(exactItemId)
				.getName(),
			SwingUtilities::invokeLater
		);

		statusLabel.setForeground(Color.LIGHT_GRAY);
		statusLabel.setBorder(new EmptyBorder(6, 2, 0, 2));

		rebuild();
	}

	public void setListener(Listener listener)
	{
		this.listener = listener == null
			? NOOP_LISTENER
			: listener;
	}

	public void setState(PriorityState state)
	{
		PriorityState requiredState = Objects.requireNonNull(
			state,
			"state"
		);

		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(
				() -> setState(requiredState)
			);
			return;
		}

		this.state = requiredState;

		if (selectedDefinitionId != null
			&& !state.definitionsById().containsKey(
				selectedDefinitionId
			))
		{
			selectedDefinitionId = null;
		}

		itemNameCache.request(
			candidateItemIds(requiredState),
			this::rebuild
		);
		rebuild();
	}


	public void openDefinition(String definitionId)
	{
		String requiredDefinitionId = Objects.requireNonNull(
			definitionId,
			"definitionId"
		);

		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(
				() -> openDefinition(requiredDefinitionId)
			);
			return;
		}

		if (!state.definitionsById().containsKey(requiredDefinitionId))
		{
			return;
		}

		selectedDefinitionId = requiredDefinitionId;
		rebuild();
	}

	public void showItemSearchResults(
		String definitionId,
		String query,
		List<PriorityItemSearchResult> results)
	{
		String requiredDefinitionId = Objects.requireNonNull(
			definitionId,
			"definitionId"
		);
		String requiredQuery = Objects.requireNonNull(query, "query");
		List<PriorityItemSearchResult> copiedResults =
			List.copyOf(Objects.requireNonNull(results, "results"));

		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() ->
				showItemSearchResults(
					requiredDefinitionId,
					requiredQuery,
					copiedResults
				)
			);
			return;
		}

		PriorityDefinition definition = state.definitionsById().get(
			requiredDefinitionId
		);

		if (definition == null)
		{
			return;
		}

		if (copiedResults.isEmpty())
		{
			showMessage(
				"No exact items matched \"" + requiredQuery + "\".",
				true
			);
			return;
		}

		PriorityItemSearchResult selected =
			(PriorityItemSearchResult) JOptionPane.showInputDialog(
				this,
				"Select the exact item to add to "
					+ displayDefinitionName(definition)
					+ ":",
				"Add priority item",
				JOptionPane.PLAIN_MESSAGE,
				null,
				copiedResults.toArray(),
				copiedResults.get(0)
			);

		if (selected != null)
		{
			listener.addCandidateTier(
				requiredDefinitionId,
				selected.getExactItemId()
			);
		}
	}

	public void showMessage(String message, boolean error)
	{
		String requiredMessage = Objects.requireNonNull(
			message,
			"message"
		);

		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(
				() -> showMessage(requiredMessage, error)
			);
			return;
		}

		statusLabel.setText(
			"<html>" + escapeHtml(requiredMessage) + "</html>"
		);
		statusLabel.setForeground(
			error ? ERROR_COLOR : SUCCESS_COLOR
		);
	}

	private void rebuild()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::rebuild);
			return;
		}

		removeAll();
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(
			new BoxLayout(content, BoxLayout.Y_AXIS)
		);
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		if (selectedDefinitionId == null)
		{
			buildLibraryView(content);
		}
		else
		{
			PriorityDefinition definition =
				state.definitionsById().get(
					selectedDefinitionId
				);

			if (definition == null)
			{
				selectedDefinitionId = null;
				buildLibraryView(content);
			}
			else
			{
				buildDefinitionView(content, definition);
			}
		}

		for (Component component : content.getComponents())
		{
			if (component instanceof JComponent)
			{
				((JComponent) component).setAlignmentX(
					Component.LEFT_ALIGNMENT
				);
			}
		}

		add(content, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	private void buildLibraryView(JPanel content)
	{
		content.add(titleLabel("Priority Slots"));
		content.add(Box.createVerticalStrut(4));
		content.add(hintLabel(
			"Select a definition to view and edit its priority items."
		));
		content.add(Box.createVerticalStrut(8));

		JButton createDefinition = new JButton("New definition");
		createDefinition.addActionListener(event ->
			promptCreateDefinition()
		);
		createDefinition.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, 32)
		);
		content.add(createDefinition);
		content.add(Box.createVerticalStrut(8));
		content.add(divider());
		content.add(Box.createVerticalStrut(8));

		if (state.getRootEntries().isEmpty())
		{
			content.add(hintLabel(
				"No priority definitions have been saved."
			));
			content.add(statusLabel);
			return;
		}

		Set<String> visitedGroups = new HashSet<>();
		addLibraryEntries(
			content,
			state.getRootEntries(),
			null,
			0,
			visitedGroups
		);

		content.add(statusLabel);
	}

	private void addLibraryEntries(
		JPanel content,
		List<PriorityLibraryEntry> entries,
		String parentGroupId,
		int depth,
		Set<String> visitedGroups)
	{
		Map<String, PriorityDefinition> definitionsById =
			state.definitionsById();
		Map<String, PriorityGroup> groupsById =
			state.groupsById();

		for (int index = 0; index < entries.size(); index++)
		{
			PriorityLibraryEntry entry = entries.get(index);

			if (entry.isGroup())
			{
				PriorityGroup group = groupsById.get(
					entry.getTargetId()
				);

				if (group == null
					|| !visitedGroups.add(group.getId()))
				{
					continue;
				}

				content.add(
					indent(
						buildGroupHeader(
							group,
							parentGroupId,
							index,
							entries.size()
						),
						depth
					)
				);
				content.add(Box.createVerticalStrut(3));

				if (!collapsedGroupIds.contains(group.getId()))
				{
					addLibraryEntries(
						content,
						group.getChildren(),
						group.getId(),
						depth + 1,
						visitedGroups
					);
				}

				continue;
			}

			PriorityDefinition definition =
				definitionsById.get(entry.getTargetId());

			if (definition == null)
			{
				continue;
			}

			content.add(
				indent(
					buildDefinitionCard(
						definition,
						parentGroupId,
						index,
						entries.size()
					),
					depth
				)
			);
			content.add(Box.createVerticalStrut(5));
		}
	}

	private JPanel buildGroupHeader(
		PriorityGroup group,
		String parentGroupId,
		int index,
		int siblingCount)
	{
		boolean collapsed = collapsedGroupIds.contains(
			group.getId()
		);

		JPanel row = new JPanel(new BorderLayout(5, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(rowBorder());
		row.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, 31)
		);

		JButton toggle = compactButton(collapsed ? "▸" : "▾");
		toggle.setToolTipText(collapsed ? "Expand" : "Collapse");
		toggle.addActionListener(event ->
		{
			if (collapsed)
			{
				collapsedGroupIds.remove(group.getId());
			}
			else
			{
				collapsedGroupIds.add(group.getId());
			}

			rebuild();
		});
		row.add(toggle, BorderLayout.WEST);

		JLabel name = new JLabel(
			"<html><b>" + escapeHtml(group.getName()) + "</b></html>"
		);
		name.setForeground(ColorScheme.BRAND_ORANGE);
		row.add(name, BorderLayout.CENTER);

		JLabel count = new JLabel(
			Integer.toString(group.getChildren().size())
		);
		count.setForeground(Color.LIGHT_GRAY);
		count.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(count, BorderLayout.EAST);

		JPopupMenu menu = buildLibraryEntryMenu(
			PriorityLibraryEntry.group(group.getId()),
			parentGroupId,
			index,
			siblingCount
		);
		setPopupRecursively(row, menu);

		return row;
	}

	private JPanel buildDefinitionCard(
		PriorityDefinition definition,
		String parentGroupId,
		int index,
		int siblingCount)
	{
		JPanel card = new JPanel(new BorderLayout(8, 0));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(boxBorder());
		card.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, 82)
		);
		card.setCursor(Cursor.getPredefinedCursor(
			Cursor.HAND_CURSOR
		));

		JLabel icon = itemIconLabel(primaryItemId(definition));
		card.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

		JLabel name = new JLabel(
			"<html><b>"
				+ escapeHtml(displayDefinitionName(definition))
				+ "</b></html>"
		);
		name.setForeground(Color.WHITE);

		JLabel preview = new JLabel(
			"<html>"
				+ candidatePreviewHtml(definition)
				+ "</html>"
		);
		preview.setForeground(Color.LIGHT_GRAY);
		preview.setToolTipText(
			"<html>" + escapeHtml(fullCandidatePreview(definition)) + "</html>"
		);

		text.add(name);
		text.add(Box.createVerticalStrut(2));
		text.add(preview);
		card.add(text, BorderLayout.CENTER);

		JLabel open = new JLabel("›");
		open.setForeground(ColorScheme.BRAND_ORANGE);
		open.setHorizontalAlignment(SwingConstants.RIGHT);
		card.add(open, BorderLayout.EAST);

		Runnable openDefinition = () ->
		{
			selectedDefinitionId = definition.getId();
			rebuild();
		};
		makeClickableRecursively(card, openDefinition);

		JPopupMenu menu = buildLibraryEntryMenu(
			PriorityLibraryEntry.definition(definition.getId()),
			parentGroupId,
			index,
			siblingCount
		);
		JMenuItem install = new JMenuItem("Add to open Bank Tag");
		install.setEnabled(!definition.getTiers().isEmpty());
		install.addActionListener(event ->
			listener.installDefinition(definition.getId())
		);
		menu.insert(install, 0);
		menu.insert(new JSeparator(), 1);
		menu.add(new JSeparator());

		JMenuItem rename = new JMenuItem("Rename definition");
		rename.addActionListener(event ->
			promptRenameDefinition(definition)
		);
		menu.add(rename);

		JMenuItem delete = new JMenuItem("Delete definition");
		delete.addActionListener(event ->
			confirmDeleteDefinition(definition)
		);
		menu.add(delete);
		setPopupRecursively(card, menu);

		return card;
	}

	private void buildDefinitionView(
		JPanel content,
		PriorityDefinition definition)
	{
		JButton back = new JButton("‹ Back");
		back.setHorizontalAlignment(SwingConstants.LEFT);
		back.addActionListener(event ->
		{
			selectedDefinitionId = null;
			rebuild();
		});
		back.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, 28)
		);
		content.add(back);
		content.add(Box.createVerticalStrut(10));

		content.add(titleLabel(displayDefinitionName(definition)));
		content.add(Box.createVerticalStrut(4));
		content.add(hintLabel(
			"Highest priority is at the top. Drag the handle to reorder."
		));
		content.add(Box.createVerticalStrut(8));
		content.add(divider());
		content.add(Box.createVerticalStrut(8));

		List<PriorityTier> tiers = definition.getTiers();

		CandidateListPanel candidateList =
			new CandidateListPanel();

		for (int index = 0; index < tiers.size(); index++)
		{
			PriorityTier tier = tiers.get(index);
			CandidateRow row = new CandidateRow(
				definition.getId(),
				tier,
				index,
				tiers.size(),
				candidateList
			);
			candidateList.add(row);

			if (index < tiers.size() - 1)
			{
				candidateList.add(
					Box.createVerticalStrut(5)
				);
			}
		}

		if (!tiers.isEmpty())
		{
			content.add(candidateList);
			content.add(Box.createVerticalStrut(8));
		}

		if (tiers.isEmpty())
		{
			content.add(hintLabel(
				"This definition has no priority items."
			));
			content.add(Box.createVerticalStrut(8));
		}

		JButton addItem = new JButton("Add priority item");
		addItem.addActionListener(event ->
			promptItemSearch(definition)
		);
		addItem.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, 32)
		);
		content.add(addItem);
		content.add(Box.createVerticalStrut(6));

		JButton addToTag = new JButton("Add to open Bank Tag");
		addToTag.setEnabled(!tiers.isEmpty());
		addToTag.setToolTipText(
			"Insert this definition into the first empty cell of the currently open Bank Tag layout"
		);
		addToTag.addActionListener(event ->
			listener.installDefinition(definition.getId())
		);
		addToTag.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, 32)
		);
		content.add(addToTag);
		content.add(Box.createVerticalStrut(6));

		JPanel definitionActions = new JPanel(
			new FlowLayout(FlowLayout.LEFT, 4, 0)
		);
		definitionActions.setOpaque(false);

		JButton rename = compactButton("Rename");
		rename.addActionListener(event ->
			promptRenameDefinition(definition)
		);
		definitionActions.add(rename);

		JButton delete = compactButton("Delete");
		delete.addActionListener(event ->
			confirmDeleteDefinition(definition)
		);
		definitionActions.add(delete);
		content.add(definitionActions);
		content.add(statusLabel);
	}

	private JPopupMenu buildLibraryEntryMenu(
		PriorityLibraryEntry entry,
		String parentGroupId,
		int index,
		int siblingCount)
	{
		JPopupMenu menu = new JPopupMenu();

		JMenuItem up = new JMenuItem("Move up");
		up.setEnabled(index > 0);
		up.addActionListener(event ->
			moveLibraryEntry(
				entry,
				parentGroupId,
				index - 1
			)
		);
		menu.add(up);

		JMenuItem down = new JMenuItem("Move down");
		down.setEnabled(index < siblingCount - 1);
		down.addActionListener(event ->
			moveLibraryEntry(
				entry,
				parentGroupId,
				index + 1
			)
		);
		menu.add(down);

		JMenu moveTo = new JMenu("Move to");
		addMoveTargets(moveTo, entry, parentGroupId);
		moveTo.setEnabled(moveTo.getItemCount() > 0);
		menu.add(moveTo);

		return menu;
	}

	private void addMoveTargets(
		JMenu menu,
		PriorityLibraryEntry entry,
		String currentParentGroupId)
	{
		if (currentParentGroupId != null)
		{
			JMenuItem root = new JMenuItem("Top level");
			root.addActionListener(event ->
				moveLibraryEntry(
					entry,
					null,
					state.getRootEntries().size()
				)
			);
			menu.add(root);
		}

		Map<String, String> groupPaths = groupPaths();

		for (Map.Entry<String, String> path
			: groupPaths.entrySet())
		{
			String targetGroupId = path.getKey();

			if (Objects.equals(
				currentParentGroupId,
				targetGroupId
			))
			{
				continue;
			}

			if (entry.isGroup()
				&& (entry.getTargetId().equals(targetGroupId)
					|| isDescendantGroup(
						entry.getTargetId(),
						targetGroupId
					)))
			{
				continue;
			}

			PriorityGroup target = state.groupsById().get(
				targetGroupId
			);

			if (target == null)
			{
				continue;
			}

			JMenuItem destination = new JMenuItem(path.getValue());
			destination.addActionListener(event ->
				moveLibraryEntry(
					entry,
					targetGroupId,
					target.getChildren().size()
				)
			);
			menu.add(destination);
		}
	}

	private Map<String, String> groupPaths()
	{
		Map<String, String> result = new LinkedHashMap<>();
		collectGroupPaths(
			state.getRootEntries(),
			"",
			result,
			new HashSet<>()
		);
		return result;
	}

	private void collectGroupPaths(
		List<PriorityLibraryEntry> entries,
		String prefix,
		Map<String, String> result,
		Set<String> visited)
	{
		Map<String, PriorityGroup> groupsById =
			state.groupsById();

		for (PriorityLibraryEntry entry : entries)
		{
			if (!entry.isGroup())
			{
				continue;
			}

			PriorityGroup group = groupsById.get(
				entry.getTargetId()
			);

			if (group == null || !visited.add(group.getId()))
			{
				continue;
			}

			String path = prefix.isEmpty()
				? group.getName()
				: prefix + " / " + group.getName();
			result.put(group.getId(), path);
			collectGroupPaths(
				group.getChildren(),
				path,
				result,
				visited
			);
		}
	}

	private boolean isDescendantGroup(
		String ancestorGroupId,
		String possibleDescendantId)
	{
		PriorityGroup ancestor = state.groupsById().get(
			ancestorGroupId
		);

		if (ancestor == null)
		{
			return false;
		}

		for (PriorityLibraryEntry child : ancestor.getChildren())
		{
			if (!child.isGroup())
			{
				continue;
			}

			if (child.getTargetId().equals(
				possibleDescendantId
			))
			{
				return true;
			}

			if (isDescendantGroup(
				child.getTargetId(),
				possibleDescendantId
			))
			{
				return true;
			}
		}

		return false;
	}

	private void moveLibraryEntry(
		PriorityLibraryEntry entry,
		String targetParentGroupId,
		int targetIndex)
	{
		if (entry.isGroup())
		{
			listener.moveGroup(
				entry.getTargetId(),
				targetParentGroupId,
				targetIndex
			);
		}
		else
		{
			listener.moveDefinition(
				entry.getTargetId(),
				targetParentGroupId,
				targetIndex
			);
		}
	}


	private void promptCreateDefinition()
	{
		String name = (String) JOptionPane.showInputDialog(
			this,
			"Definition name:",
			"New priority definition",
			JOptionPane.PLAIN_MESSAGE,
			null,
			null,
			""
		);

		if (name == null)
		{
			return;
		}

		String trimmedName = name.trim();

		if (trimmedName.isEmpty())
		{
			showMessage("Definition name must not be blank.", true);
			return;
		}

		listener.createDefinition(trimmedName);
	}

	private void promptRenameDefinition(
		PriorityDefinition definition)
	{
		String name = (String) JOptionPane.showInputDialog(
			this,
			"Definition name:",
			"Rename priority definition",
			JOptionPane.PLAIN_MESSAGE,
			null,
			null,
			definition.getName()
		);

		if (name == null)
		{
			return;
		}

		String trimmedName = name.trim();

		if (trimmedName.isEmpty())
		{
			showMessage("Definition name must not be blank.", true);
			return;
		}

		listener.renameDefinition(
			definition.getId(),
			trimmedName
		);
	}

	private void promptItemSearch(
		PriorityDefinition definition)
	{
		String query = (String) JOptionPane.showInputDialog(
			this,
			"Search exact item names:",
			"Add priority item",
			JOptionPane.PLAIN_MESSAGE,
			null,
			null,
			""
		);

		if (query == null)
		{
			return;
		}

		String trimmedQuery = query.trim();

		if (trimmedQuery.length() < 2)
		{
			showMessage(
				"Enter at least two characters to search items.",
				true
			);
			return;
		}

		listener.searchItems(
			definition.getId(),
			trimmedQuery
		);
	}

	private void confirmDeleteDefinition(
		PriorityDefinition definition)
	{
		int choice = JOptionPane.showConfirmDialog(
			this,
			"Delete \"" + displayDefinitionName(definition) + "\"?",
			"Delete priority definition",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE
		);

		if (choice == JOptionPane.OK_OPTION)
		{
			listener.deleteDefinition(definition.getId());
		}
	}

	private String displayDefinitionName(
		PriorityDefinition definition)
	{
		return PriorityDefinitionPresentation.displayName(
			definition,
			this::itemName
		);
	}

	private String candidatePreviewHtml(
		PriorityDefinition definition)
	{
		List<String> lines =
			PriorityDefinitionPresentation.previewLines(
				definition,
				this::itemName,
				2
			);

		StringBuilder preview = new StringBuilder();

		for (String line : lines)
		{
			if (preview.length() > 0)
			{
				preview.append("<br>");
			}

			preview.append(escapeHtml(line));
		}

		return preview.toString();
	}

	private String fullCandidatePreview(
		PriorityDefinition definition)
	{
		List<String> names =
			PriorityDefinitionPresentation.candidateNames(
				definition,
				this::itemName
			);

		return names.isEmpty()
			? "No priority items"
			: String.join(" → ", names);
	}

	private int primaryItemId(
		PriorityDefinition definition)
	{
		for (PriorityTier tier : definition.getTiers())
		{
			if (!tier.getExactItemIds().isEmpty())
			{
				return tier.getExactItemIds().get(0);
			}
		}

		return -1;
	}

	private String itemName(int exactItemId)
	{
		return itemNameCache.displayName(exactItemId);
	}

	private static List<Integer> candidateItemIds(
		PriorityState state)
	{
		List<Integer> itemIds = new ArrayList<>();

		for (PriorityDefinition definition
			: state.getDefinitions())
		{
			for (PriorityTier tier : definition.getTiers())
			{
				itemIds.addAll(tier.getExactItemIds());
			}
		}

		return List.copyOf(itemIds);
	}

	private JLabel itemIconLabel(int exactItemId)
	{
		JLabel label = new JLabel();
		label.setPreferredSize(
			new Dimension(ITEM_ICON_SIZE, ITEM_ICON_SIZE)
		);
		label.setMinimumSize(
			new Dimension(ITEM_ICON_SIZE, ITEM_ICON_SIZE)
		);

		if (exactItemId <= 0)
		{
			return label;
		}

		try
		{
			AsyncBufferedImage image = itemManager.getImage(exactItemId);
			image.addTo(label);
		}
		catch (RuntimeException ignored)
		{
			// Rendering the text is sufficient until item images are ready.
		}

		return label;
	}

	private JPanel indent(JPanel component, int depth)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.setBorder(new EmptyBorder(
			0,
			depth * INDENT_WIDTH,
			0,
			0
		));
		wrapper.setMaximumSize(
			new Dimension(Integer.MAX_VALUE,
				component.getMaximumSize().height)
		);
		wrapper.add(component, BorderLayout.CENTER);
		return wrapper;
	}

	private static JLabel titleLabel(String text)
	{
		JLabel label = new JLabel(
			"<html><b>" + escapeHtml(text) + "</b></html>"
		);
		label.setForeground(Color.WHITE);
		label.setFont(label.getFont().deriveFont(
			label.getFont().getStyle() | java.awt.Font.BOLD,
			16f
		));
		return label;
	}

	private static JLabel hintLabel(String text)
	{
		JLabel label = new JLabel(
			"<html>" + escapeHtml(text) + "</html>"
		);
		label.setForeground(Color.LIGHT_GRAY);
		return label;
	}

	private static JSeparator divider()
	{
		JSeparator separator = new JSeparator();
		separator.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		separator.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, 1)
		);
		return separator;
	}

	private static JButton compactButton(String text)
	{
		JButton button = new JButton(text);
		button.setMargin(new java.awt.Insets(0, 4, 0, 4));
		button.setFocusable(false);
		return button;
	}

	private static Border rowBorder()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(
				0,
				0,
				1,
				0,
				ColorScheme.MEDIUM_GRAY_COLOR
			),
			new EmptyBorder(3, 4, 3, 4)
		);
	}

	private static Border boxBorder()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(
				ColorScheme.MEDIUM_GRAY_COLOR
			),
			new EmptyBorder(6, 7, 6, 7)
		);
	}

	private static void makeClickableRecursively(
		Component component,
		Runnable action)
	{
		MouseAdapter click = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (SwingUtilities.isLeftMouseButton(event))
				{
					action.run();
				}
			}
		};

		addMouseListenerRecursively(component, click);
	}

	private static void addMouseListenerRecursively(
		Component component,
		MouseAdapter listener)
	{
		component.addMouseListener(listener);

		if (component instanceof java.awt.Container)
		{
			for (Component child
				: ((java.awt.Container) component).getComponents())
			{
				addMouseListenerRecursively(child, listener);
			}
		}
	}

	private static void setPopupRecursively(
		Component component,
		JPopupMenu menu)
	{
		if (component instanceof JComponent)
		{
			((JComponent) component).setComponentPopupMenu(menu);
		}

		if (component instanceof java.awt.Container)
		{
			for (Component child
				: ((java.awt.Container) component).getComponents())
			{
				setPopupRecursively(child, menu);
			}
		}
	}

	private static String escapeHtml(String text)
	{
		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}

	public interface Listener
	{
		void createDefinition(String name);

		void renameDefinition(
			String definitionId,
			String name);

		void searchItems(
			String definitionId,
			String query);

		void addCandidateTier(
			String definitionId,
			int exactItemId);

		void removeCandidateTier(
			String definitionId,
			String tierId);

		void deleteDefinition(String definitionId);

		void moveGroup(
			String groupId,
			String targetParentGroupId,
			int targetIndex);

		void moveDefinition(
			String definitionId,
			String targetParentGroupId,
			int targetIndex);

		void moveCandidateTier(
			String definitionId,
			String tierId,
			int targetIndex);

		void installDefinition(String definitionId);
	}

	private final class CandidateRow extends JPanel
	{
		private final String definitionId;
		private final String tierId;
		private final int index;
		private final int candidateCount;
		private final JLabel dragHandle;
		private final JLabel rank;
		private Border restingBorder;

		private CandidateRow(
			String definitionId,
			PriorityTier tier,
			int index,
			int candidateCount,
			CandidateListPanel candidateList)
		{
			super(new BorderLayout(7, 0));

			this.definitionId = definitionId;
			this.tierId = tier.getId();
			this.index = index;
			this.candidateCount = candidateCount;

			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(boxBorder());
			setMaximumSize(
				new Dimension(Integer.MAX_VALUE, 54)
			);

			dragHandle = new JLabel("☰");
			dragHandle.setForeground(Color.LIGHT_GRAY);
			dragHandle.setCursor(Cursor.getPredefinedCursor(
				Cursor.MOVE_CURSOR
			));
			dragHandle.setToolTipText("Drag to change priority");
			add(dragHandle, BorderLayout.WEST);

			int exactItemId = tier.getExactItemIds().get(0);

			JPanel center = new JPanel(new BorderLayout(7, 0));
			center.setOpaque(false);
			center.add(itemIconLabel(exactItemId), BorderLayout.WEST);

			JPanel labels = new JPanel();
			labels.setOpaque(false);
			labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

			JLabel itemName = new JLabel(
				"<html><b>"
					+ escapeHtml(itemName(exactItemId))
					+ "</b></html>"
			);
			itemName.setForeground(Color.WHITE);
			labels.add(itemName);

			String detail = index == 0
				? "Highest priority"
				: "Priority " + (index + 1);

			if (tier.getExactItemIds().size() > 1)
			{
				detail += " · "
					+ tier.getExactItemIds().size()
					+ " exact variants";
			}

			JLabel priority = new JLabel(detail);
			priority.setForeground(
				index == 0
					? ColorScheme.BRAND_ORANGE
					: Color.LIGHT_GRAY
			);
			labels.add(priority);
			center.add(labels, BorderLayout.CENTER);
			add(center, BorderLayout.CENTER);

			rank = new JLabel(Integer.toString(index + 1));
			rank.setForeground(Color.LIGHT_GRAY);
			rank.setHorizontalAlignment(SwingConstants.RIGHT);

			JPanel actions = new JPanel(
				new FlowLayout(FlowLayout.RIGHT, 4, 0)
			);
			actions.setOpaque(false);
			actions.add(rank);

			JButton remove = compactButton("×");
			remove.setToolTipText("Remove priority item");
			remove.addActionListener(event ->
				listener.removeCandidateTier(
					definitionId,
					tierId
				)
			);
			actions.add(remove);
			add(actions, BorderLayout.EAST);

			CandidateDragMouseAdapter drag =
				new CandidateDragMouseAdapter(
					this,
					candidateList
				);
			dragHandle.addMouseListener(drag);
			dragHandle.addMouseMotionListener(drag);
		}

		private void setDragging(boolean dragging)
		{
			if (dragging)
			{
				restingBorder = getBorder();
				setBackground(ColorScheme.DARK_GRAY_COLOR);
				setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(
						ColorScheme.BRAND_ORANGE,
						2
					),
					new EmptyBorder(5, 7, 5, 7)
				));
				dragHandle.setForeground(
					ColorScheme.BRAND_ORANGE
				);
				rank.setText("↕");
				rank.setForeground(
					ColorScheme.BRAND_ORANGE
				);
			}
			else
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
				if (restingBorder != null)
				{
					setBorder(restingBorder);
				}
				dragHandle.setForeground(Color.LIGHT_GRAY);
				rank.setText(Integer.toString(index + 1));
				rank.setForeground(Color.LIGHT_GRAY);
			}
			repaint();
		}
	}

	private static final class CandidateListPanel extends JPanel
	{
		private Integer insertionBoundary;

		private CandidateListPanel()
		{
			setOpaque(false);
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setMaximumSize(
				new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)
			);
		}

		private void setInsertionBoundary(
			Integer insertionBoundary)
		{
			this.insertionBoundary = insertionBoundary;
			repaint();
		}

		@Override
		protected void paintChildren(Graphics graphics)
		{
			super.paintChildren(graphics);

			if (insertionBoundary == null)
			{
				return;
			}

			List<Component> rows = new ArrayList<>();
			for (Component component : getComponents())
			{
				if (component instanceof CandidateRow)
				{
					rows.add(component);
				}
			}

			if (rows.isEmpty())
			{
				return;
			}

			int boundary = Math.max(
				0,
				Math.min(insertionBoundary, rows.size())
			);
			int y;

			if (boundary == 0)
			{
				y = rows.get(0).getY() - 3;
			}
			else if (boundary == rows.size())
			{
				Component last = rows.get(rows.size() - 1);
				y = last.getY() + last.getHeight() + 2;
			}
			else
			{
				Component before = rows.get(boundary - 1);
				Component after = rows.get(boundary);
				y = (before.getY() + before.getHeight()
					+ after.getY()) / 2;
			}

			if (getHeight() < 6)
			{
				return;
			}

			y = Math.max(2, Math.min(y, getHeight() - 3));
			graphics.setColor(ColorScheme.BRAND_ORANGE);
			graphics.fillRoundRect(6, y - 2,
				Math.max(0, getWidth() - 12), 4, 4, 4);
			graphics.fillOval(2, y - 4, 8, 8);
			graphics.fillOval(
				Math.max(2, getWidth() - 10),
				y - 4,
				8,
				8
			);
		}
	}

	private final class CandidateDragMouseAdapter
		extends MouseAdapter
	{
		private final CandidateRow row;
		private final CandidateListPanel candidateList;
		private Point pressPoint;
		private boolean dragging;
		private Integer requestedInsertionIndex;

		private CandidateDragMouseAdapter(
			CandidateRow row,
			CandidateListPanel candidateList)
		{
			this.row = row;
			this.candidateList = candidateList;
		}

		@Override
		public void mousePressed(MouseEvent event)
		{
			pressPoint = event.getPoint();
			dragging = false;
			requestedInsertionIndex = null;
		}

		@Override
		public void mouseDragged(MouseEvent event)
		{
			if (pressPoint == null)
			{
				return;
			}

			if (!dragging
				&& pressPoint.distance(event.getPoint()) >= 4.0)
			{
				dragging = true;
				row.setDragging(true);
				candidateList.setCursor(
					Cursor.getPredefinedCursor(
						Cursor.MOVE_CURSOR
					)
				);
			}

			if (dragging)
			{
				requestedInsertionIndex = insertionIndexAt(event);
				candidateList.setInsertionBoundary(
					requestedInsertionIndex
				);
			}
		}

		@Override
		public void mouseReleased(MouseEvent event)
		{
			try
			{
				if (!dragging)
				{
					return;
				}

				int requestedIndex = requestedInsertionIndex == null
					? insertionIndexAt(event)
					: requestedInsertionIndex;

				int targetIndex = PriorityTreeDropPlanner
					.normalizeInsertionIndex(
						row.definitionId,
						row.index,
						row.definitionId,
						requestedIndex,
						row.candidateCount
					);

				if (targetIndex != row.index)
				{
					listener.moveCandidateTier(
						row.definitionId,
						row.tierId,
						targetIndex
					);
				}
			}
			catch (RuntimeException exception)
			{
				String message = exception.getMessage();
				showMessage(
					message == null
						? "Unable to reorder priority item."
						: message,
					true
				);
			}
			finally
			{
				row.setDragging(false);
				candidateList.setInsertionBoundary(null);
				candidateList.setCursor(
					Cursor.getDefaultCursor()
				);
				pressPoint = null;
				dragging = false;
				requestedInsertionIndex = null;
			}
		}

		private int insertionIndexAt(MouseEvent event)
		{
			Point listPoint = SwingUtilities.convertPoint(
				(Component) event.getSource(),
				event.getPoint(),
				candidateList
			);

			List<Integer> rowMidpoints = new ArrayList<>();
			for (Component component
				: candidateList.getComponents())
			{
				if (component instanceof CandidateRow)
				{
					rowMidpoints.add(
						component.getY()
							+ component.getHeight() / 2
					);
				}
			}

			return PriorityTreeDropPlanner
				.requestedInsertionIndex(
					listPoint.y,
					rowMidpoints
				);
		}
	}

}
