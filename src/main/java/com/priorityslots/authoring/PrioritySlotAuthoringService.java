package com.priorityslots.authoring;

import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.BankTagSlotBinding;
import com.priorityslots.domain.CellPlacement;
import com.priorityslots.domain.PriorityDefinition;
import com.priorityslots.domain.PriorityGroup;
import com.priorityslots.domain.PriorityLibraryEntry;
import com.priorityslots.domain.PriorityState;
import com.priorityslots.domain.PriorityTier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Text;

@Singleton
public final class PrioritySlotAuthoringService
{
	private static final int EMPTY_LAYOUT_ITEM = -1;

	private final Supplier<String> idSupplier;

	private final PriorityLibraryEditor libraryEditor =
		new PriorityLibraryEditor();

	@Inject
	public PrioritySlotAuthoringService()
	{
		this(() -> UUID.randomUUID().toString());
	}

	PrioritySlotAuthoringService(
		Supplier<String> idSupplier)
	{
		this.idSupplier = Objects.requireNonNull(
			idSupplier,
			"idSupplier"
		);
	}

	public CreateGroupResult createGroup(
		PriorityState state,
		String name,
		String parentGroupId,
		int targetIndex)
	{
		Objects.requireNonNull(state, "state");

		PriorityGroup group = new PriorityGroup(
			nextId(),
			name,
			List.<PriorityLibraryEntry>of()
		);

		List<PriorityGroup> groups =
			new ArrayList<>(state.getGroups());

		groups.add(group);

		PriorityState withGroup = new PriorityState(
			state.getDefinitions(),
			groups,
			state.getBindings(),
			appendUnplacedGroupTemporarily(
				state.getRootEntries(),
				group.getId()
			)
		);

		PriorityState moved = libraryEditor.move(
			withGroup,
			PriorityLibraryEntry.group(group.getId()),
			parentGroupId,
			targetIndex
		);

		return new CreateGroupResult(moved, group);
	}

	public CreateDefinitionResult createDefinition(
		PriorityState state,
		String name,
		List<Integer> orderedExactItemIds,
		String parentGroupId,
		int targetIndex)
	{
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(
			orderedExactItemIds,
			"orderedExactItemIds"
		);

		if (orderedExactItemIds.isEmpty())
		{
			throw new IllegalArgumentException(
				"orderedExactItemIds must not be empty"
			);
		}

		Set<Integer> seenItemIds = new HashSet<>();
		List<PriorityTier> tiers = new ArrayList<>();

		for (Integer exactItemId : orderedExactItemIds)
		{
			if (exactItemId == null || exactItemId <= 0)
			{
				throw new IllegalArgumentException(
					"orderedExactItemIds must contain "
						+ "only positive item IDs"
				);
			}

			if (!seenItemIds.add(exactItemId))
			{
				throw new IllegalArgumentException(
					"Duplicate exact item ID: "
						+ exactItemId
				);
			}

			tiers.add(
				new PriorityTier(
					nextId(),
					List.of(exactItemId)
				)
			);
		}

		PriorityDefinition definition =
			new PriorityDefinition(
				nextId(),
				name,
				tiers
			);

		List<PriorityDefinition> definitions =
			new ArrayList<>(state.getDefinitions());

		definitions.add(definition);

		PriorityState withDefinition = new PriorityState(
			definitions,
			state.getGroups(),
			state.getBindings(),
			appendUnplacedDefinitionTemporarily(
				state.getRootEntries(),
				definition.getId()
			)
		);

		PriorityState moved = libraryEditor.move(
			withDefinition,
			PriorityLibraryEntry.definition(
				definition.getId()
			),
			parentGroupId,
			targetIndex
		);

		return new CreateDefinitionResult(
			moved,
			definition
		);
	}

	public PriorityState moveGroup(
		PriorityState state,
		String groupId,
		String targetParentGroupId,
		int targetIndex)
	{
		return libraryEditor.move(
			state,
			PriorityLibraryEntry.group(groupId),
			targetParentGroupId,
			targetIndex
		);
	}

	public PriorityState moveDefinition(
		PriorityState state,
		String definitionId,
		String targetParentGroupId,
		int targetIndex)
	{
		return libraryEditor.move(
			state,
			PriorityLibraryEntry.definition(
				definitionId
			),
			targetParentGroupId,
			targetIndex
		);
	}

	public PriorityState moveCandidateTier(
		PriorityState state,
		String definitionId,
		String tierId,
		int targetIndex)
	{
		Objects.requireNonNull(state, "state");
		String requiredDefinitionId = requireNonBlank(
			definitionId,
			"definitionId"
		);
		String requiredTierId = requireNonBlank(
			tierId,
			"tierId"
		);

		PriorityDefinition definition =
			state.definitionsById().get(
				requiredDefinitionId
			);

		if (definition == null)
		{
			throw new IllegalArgumentException(
				"Unknown definition: "
					+ requiredDefinitionId
			);
		}

		List<PriorityTier> tiers =
			new ArrayList<>(definition.getTiers());

		if (targetIndex < 0 || targetIndex >= tiers.size())
		{
			throw new IllegalArgumentException(
				"targetIndex is outside candidate list"
			);
		}

		int sourceIndex = -1;

		for (int index = 0; index < tiers.size(); index++)
		{
			if (tiers.get(index).getId().equals(
				requiredTierId))
			{
				sourceIndex = index;
				break;
			}
		}

		if (sourceIndex < 0)
		{
			throw new IllegalArgumentException(
				"Unknown candidate tier: "
					+ requiredTierId
			);
		}

		if (sourceIndex == targetIndex)
		{
			return state;
		}

		PriorityTier movedTier = tiers.remove(sourceIndex);
		tiers.add(targetIndex, movedTier);

		PriorityDefinition updatedDefinition =
			definition.withTiers(tiers);

		List<PriorityDefinition> definitions =
			new ArrayList<>(state.getDefinitions());

		definitions.set(
			definitions.indexOf(definition),
			updatedDefinition
		);

		return new PriorityState(
			definitions,
			state.getGroups(),
			state.getBindings(),
			state.getRootEntries()
		);
	}

	public InstallationResult installDefinitionInActiveLayout(
		PriorityState state,
		String activeBankTagName,
		List<Integer> activeLayoutItems,
		String definitionId)
	{
		Objects.requireNonNull(state, "state");
		String bankTagName = requireNonBlank(
			activeBankTagName,
			"activeBankTagName"
		);
		String requiredDefinitionId = requireNonBlank(
			definitionId,
			"definitionId"
		);

		List<Integer> layoutItems = copyLayoutItems(
			activeLayoutItems
		);

		PriorityDefinition definition =
			state.definitionsById().get(
				requiredDefinitionId
			);

		if (definition == null)
		{
			throw new IllegalArgumentException(
				"Unknown definition: "
					+ requiredDefinitionId
			);
		}

		int seedItemId = highestPriorityItemId(definition);
		BankTagBinding existingBinding =
			bindingForTag(state, bankTagName);

		Set<Integer> reservedIndices = new HashSet<>();

		if (existingBinding != null)
		{
			for (BankTagSlotBinding slot
				: existingBinding.getSlots())
			{
				reservedIndices.add(
					slot.getPlacement().getIndex()
				);

				if (slot.getPlacement()
					.getDefinitionId()
					.equals(requiredDefinitionId))
				{
					throw new IllegalArgumentException(
						"Definition is already installed "
							+ "in the active Bank Tag"
					);
				}
			}
		}

		int targetIndex = firstAvailableIndex(
			layoutItems,
			reservedIndices
		);

		List<Integer> updatedLayoutItems =
			new ArrayList<>(layoutItems);

		while (updatedLayoutItems.size() <= targetIndex)
		{
			updatedLayoutItems.add(EMPTY_LAYOUT_ITEM);
		}

		updatedLayoutItems.set(targetIndex, seedItemId);

		CellPlacement placement = CellPlacement.create(
			requiredDefinitionId,
			targetIndex
		);

		BankTagSlotBinding slot =
			BankTagSlotBinding.create(
				placement,
				seedItemId
			);

		BankTagBinding updatedBinding;
		List<BankTagBinding> bindings =
			new ArrayList<>(state.getBindings());

		if (existingBinding == null)
		{
			updatedBinding = BankTagBinding.create(
				bankTagName,
				List.of(slot)
			);

			bindings.add(updatedBinding);
		}
		else
		{
			List<BankTagSlotBinding> slots =
				new ArrayList<>(
					existingBinding.getSlots()
				);

			slots.add(slot);
			updatedBinding =
				existingBinding.withSlots(slots);

			bindings.set(
				bindings.indexOf(existingBinding),
				updatedBinding
			);
		}

		PriorityState updatedState = new PriorityState(
			state.getDefinitions(),
			state.getGroups(),
			bindings,
			state.getRootEntries()
		);

		return new InstallationResult(
			updatedState,
			updatedLayoutItems,
			updatedBinding.getId(),
			placement.getCellId(),
			targetIndex,
			seedItemId
		);
	}

	private String nextId()
	{
		return requireNonBlank(
			idSupplier.get(),
			"generated ID"
		);
	}

	private static List<PriorityLibraryEntry>
	appendUnplacedGroupTemporarily(
		List<PriorityLibraryEntry> rootEntries,
		String groupId)
	{
		List<PriorityLibraryEntry> result =
			new ArrayList<>(rootEntries);

		result.add(PriorityLibraryEntry.group(groupId));
		return List.copyOf(result);
	}

	private static List<PriorityLibraryEntry>
	appendUnplacedDefinitionTemporarily(
		List<PriorityLibraryEntry> rootEntries,
		String definitionId)
	{
		List<PriorityLibraryEntry> result =
			new ArrayList<>(rootEntries);

		result.add(
			PriorityLibraryEntry.definition(definitionId)
		);

		return List.copyOf(result);
	}

	private static BankTagBinding bindingForTag(
		PriorityState state,
		String bankTagName)
	{
		String standardizedName =
			Text.standardize(bankTagName);

		for (BankTagBinding binding
			: state.getBindings())
		{
			if (Text.standardize(
				binding.getBankTagName()
			).equals(standardizedName))
			{
				return binding;
			}
		}

		return null;
	}

	private static int highestPriorityItemId(
		PriorityDefinition definition)
	{
		for (PriorityTier tier : definition.getTiers())
		{
			if (!tier.getExactItemIds().isEmpty())
			{
				return tier.getExactItemIds().get(0);
			}
		}

		throw new IllegalArgumentException(
			"Definition has no candidate items: "
				+ definition.getId()
		);
	}

	private static int firstAvailableIndex(
		List<Integer> layoutItems,
		Set<Integer> reservedIndices)
	{
		for (int index = 0;
		     index < layoutItems.size();
		     index++)
		{
			if (layoutItems.get(index) <= 0
				&& !reservedIndices.contains(index))
			{
				return index;
			}
		}

		int index = layoutItems.size();

		while (reservedIndices.contains(index))
		{
			index++;
		}

		return index;
	}

	private static List<Integer> copyLayoutItems(
		List<Integer> activeLayoutItems)
	{
		Objects.requireNonNull(
			activeLayoutItems,
			"activeLayoutItems"
		);

		for (Integer itemId : activeLayoutItems)
		{
			Objects.requireNonNull(
				itemId,
				"activeLayoutItems must not contain null"
			);
		}

		return List.copyOf(activeLayoutItems);
	}

	private static String requireNonBlank(
		String value,
		String fieldName)
	{
		Objects.requireNonNull(value, fieldName);

		String trimmed = value.trim();

		if (trimmed.isEmpty())
		{
			throw new IllegalArgumentException(
				fieldName + " must not be blank"
			);
		}

		return trimmed;
	}

	public static final class CreateGroupResult
	{
		private final PriorityState state;
		private final PriorityGroup group;

		private CreateGroupResult(
			PriorityState state,
			PriorityGroup group)
		{
			this.state = state;
			this.group = group;
		}

		public PriorityState getState()
		{
			return state;
		}

		public PriorityGroup getGroup()
		{
			return group;
		}
	}

	public static final class CreateDefinitionResult
	{
		private final PriorityState state;
		private final PriorityDefinition definition;

		private CreateDefinitionResult(
			PriorityState state,
			PriorityDefinition definition)
		{
			this.state = state;
			this.definition = definition;
		}

		public PriorityState getState()
		{
			return state;
		}

		public PriorityDefinition getDefinition()
		{
			return definition;
		}
	}

	public static final class InstallationResult
	{
		private final PriorityState state;
		private final List<Integer> layoutItems;
		private final String bindingId;
		private final String cellId;
		private final int layoutIndex;
		private final int seededExactItemId;

		private InstallationResult(
			PriorityState state,
			List<Integer> layoutItems,
			String bindingId,
			String cellId,
			int layoutIndex,
			int seededExactItemId)
		{
			this.state = state;
			this.layoutItems = List.copyOf(layoutItems);
			this.bindingId = bindingId;
			this.cellId = cellId;
			this.layoutIndex = layoutIndex;
			this.seededExactItemId = seededExactItemId;
		}

		public PriorityState getState()
		{
			return state;
		}

		public List<Integer> getLayoutItems()
		{
			return layoutItems;
		}

		public String getBindingId()
		{
			return bindingId;
		}

		public String getCellId()
		{
			return cellId;
		}

		public int getLayoutIndex()
		{
			return layoutIndex;
		}

		public int getSeededExactItemId()
		{
			return seededExactItemId;
		}
	}

}
