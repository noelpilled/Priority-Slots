package com.priorityslots.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
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
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class PriorityStateCodec
{
	static final int CURRENT_SCHEMA_VERSION = 2;
	private static final int LEGACY_SCHEMA_VERSION = 1;

	private final Gson gson;

	@Inject
	public PriorityStateCodec(Gson gson)
	{
		this.gson = Objects.requireNonNull(gson, "gson");
	}

	public String encode(PriorityState state)
	{
		Objects.requireNonNull(state, "state");

		return gson.toJson(StateDocument.fromDomain(state));
	}

	public PriorityState decode(String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			throw new PriorityStateFormatException(
				"Priority state JSON must not be blank"
			);
		}

		StateDocument document;

		try
		{
			document = gson.fromJson(
				json,
				StateDocument.class
			);
		}
		catch (JsonParseException exception)
		{
			throw new PriorityStateFormatException(
				"Priority state contains invalid JSON",
				exception
			);
		}

		if (document == null)
		{
			throw new PriorityStateFormatException(
				"Priority state document must not be null"
			);
		}

		try
		{
			return document.toDomain();
		}
		catch (PriorityStateFormatException exception)
		{
			throw exception;
		}
		catch (IllegalArgumentException
		       | NullPointerException exception)
		{
			throw new PriorityStateFormatException(
				"Priority state violates domain rules",
				exception
			);
		}
	}

	private static <T> List<T> requireList(
		List<T> value,
		String fieldName)
	{
		if (value == null)
		{
			throw new PriorityStateFormatException(
				fieldName + " must not be null"
			);
		}

		return value;
	}

	private static <T> T requireEntry(
		T value,
		String message)
	{
		if (value == null)
		{
			throw new PriorityStateFormatException(message);
		}

		return value;
	}

	private static final class StateDocument
	{
		@SerializedName("schemaVersion")
		private int schemaVersion;

		@SerializedName("definitions")
		private List<DefinitionDocument> definitions;

		@SerializedName("groups")
		private List<GroupDocument> groups;

		@SerializedName("bindings")
		private List<BindingDocument> bindings;

		@SerializedName("rootEntries")
		private List<LibraryEntryDocument> rootEntries;

		private static StateDocument fromDomain(
			PriorityState state)
		{
			StateDocument document = new StateDocument();
			document.schemaVersion = CURRENT_SCHEMA_VERSION;
			document.definitions = new ArrayList<>();
			document.groups = new ArrayList<>();
			document.bindings = new ArrayList<>();
			document.rootEntries = new ArrayList<>();

			for (PriorityDefinition definition
				: state.getDefinitions())
			{
				document.definitions.add(
					DefinitionDocument.fromDomain(definition)
				);
			}

			for (PriorityGroup group : state.getGroups())
			{
				document.groups.add(
					GroupDocument.fromDomain(group)
				);
			}

			for (BankTagBinding binding
				: state.getBindings())
			{
				document.bindings.add(
					BindingDocument.fromDomain(binding)
				);
			}

			for (PriorityLibraryEntry rootEntry
				: state.getRootEntries())
			{
				document.rootEntries.add(
					LibraryEntryDocument.fromDomain(
						rootEntry
					)
				);
			}

			return document;
		}

		private PriorityState toDomain()
		{
			if (schemaVersion == LEGACY_SCHEMA_VERSION)
			{
				return migrateLegacyState();
			}

			if (schemaVersion != CURRENT_SCHEMA_VERSION)
			{
				throw new PriorityStateFormatException(
					"Unsupported priority state schema version: "
						+ schemaVersion
				);
			}

			return new PriorityState(
				decodeDefinitions(),
				decodeGroups(),
				decodeBindings(),
				decodeLibraryEntries(
					requireList(
						rootEntries,
						"rootEntries"
					),
					"rootEntries"
				)
			);
		}

		private PriorityState migrateLegacyState()
		{
			List<PriorityDefinition> decodedDefinitions =
				decodeDefinitions();

			Set<String> validDefinitionIds =
				new HashSet<>();

			for (PriorityDefinition definition
				: decodedDefinitions)
			{
				validDefinitionIds.add(definition.getId());
			}

			Set<String> placedDefinitionIds =
				new HashSet<>();

			List<PriorityGroup> decodedGroups =
				new ArrayList<>();

			List<PriorityLibraryEntry> decodedRootEntries =
				new ArrayList<>();

			for (GroupDocument groupDocument
				: requireList(groups, "groups"))
			{
				GroupDocument requiredDocument =
					requireEntry(
						groupDocument,
						"groups must not contain null"
					);

				List<PriorityLibraryEntry> children =
					new ArrayList<>();

				for (String definitionId
					: requiredDocument.legacyDefinitionIds())
				{
					if (validDefinitionIds.contains(
						definitionId)
						&& placedDefinitionIds.add(
							definitionId))
					{
						children.add(
							PriorityLibraryEntry.definition(
								definitionId
							)
						);
					}
				}

				PriorityGroup migratedGroup =
					new PriorityGroup(
						requiredDocument.id,
						requiredDocument.name,
						children
					);

				decodedGroups.add(migratedGroup);
				decodedRootEntries.add(
					PriorityLibraryEntry.group(
						migratedGroup.getId()
					)
				);
			}

			for (PriorityDefinition definition
				: decodedDefinitions)
			{
				if (placedDefinitionIds.add(
					definition.getId()))
				{
					decodedRootEntries.add(
						PriorityLibraryEntry.definition(
							definition.getId()
						)
					);
				}
			}

			return new PriorityState(
				decodedDefinitions,
				decodedGroups,
				decodeBindings(),
				decodedRootEntries
			);
		}

		private List<PriorityDefinition>
		decodeDefinitions()
		{
			List<PriorityDefinition> result =
				new ArrayList<>();

			for (DefinitionDocument definition
				: requireList(definitions, "definitions"))
			{
				result.add(
					requireEntry(
						definition,
						"definitions must not contain null"
					).toDomain()
				);
			}

			return result;
		}

		private List<PriorityGroup> decodeGroups()
		{
			List<PriorityGroup> result =
				new ArrayList<>();

			for (GroupDocument group
				: requireList(groups, "groups"))
			{
				result.add(
					requireEntry(
						group,
						"groups must not contain null"
					).toDomain()
				);
			}

			return result;
		}

		private List<BankTagBinding> decodeBindings()
		{
			List<BankTagBinding> result =
				new ArrayList<>();

			for (BindingDocument binding
				: requireList(bindings, "bindings"))
			{
				result.add(
					requireEntry(
						binding,
						"bindings must not contain null"
					).toDomain()
				);
			}

			return result;
		}
	}

	private static final class DefinitionDocument
	{
		@SerializedName("id")
		private String id;

		@SerializedName("name")
		private String name;

		@SerializedName("tiers")
		private List<TierDocument> tiers;

		private static DefinitionDocument fromDomain(
			PriorityDefinition definition)
		{
			DefinitionDocument document =
				new DefinitionDocument();

			document.id = definition.getId();
			document.name = definition.getName();
			document.tiers = new ArrayList<>();

			for (PriorityTier tier
				: definition.getTiers())
			{
				document.tiers.add(
					TierDocument.fromDomain(tier)
				);
			}

			return document;
		}

		private PriorityDefinition toDomain()
		{
			List<PriorityTier> decodedTiers =
				new ArrayList<>();

			for (TierDocument tier
				: requireList(tiers, "definition tiers"))
			{
				decodedTiers.add(
					requireEntry(
						tier,
						"definition tiers must not contain null"
					).toDomain()
				);
			}

			return new PriorityDefinition(
				id,
				name,
				decodedTiers
			);
		}
	}

	private static final class TierDocument
	{
		@SerializedName("id")
		private String id;

		@SerializedName("exactItemIds")
		private List<Integer> exactItemIds;

		private static TierDocument fromDomain(
			PriorityTier tier)
		{
			TierDocument document = new TierDocument();
			document.id = tier.getId();
			document.exactItemIds = new ArrayList<>(
				tier.getExactItemIds()
			);
			return document;
		}

		private PriorityTier toDomain()
		{
			return new PriorityTier(
				id,
				requireList(
					exactItemIds,
					"tier exactItemIds"
				)
			);
		}
	}

	private static final class GroupDocument
	{
		@SerializedName("id")
		private String id;

		@SerializedName("name")
		private String name;

		@SerializedName("definitionIds")
		private List<String> definitionIds;

		@SerializedName("children")
		private List<LibraryEntryDocument> children;

		private static GroupDocument fromDomain(
			PriorityGroup group)
		{
			GroupDocument document = new GroupDocument();
			document.id = group.getId();
			document.name = group.getName();
			document.children = new ArrayList<>();

			for (PriorityLibraryEntry child
				: group.getChildren())
			{
				document.children.add(
					LibraryEntryDocument.fromDomain(child)
				);
			}

			return document;
		}

		private PriorityGroup toDomain()
		{
			return new PriorityGroup(
				id,
				name,
				decodeLibraryEntries(
					requireList(
						children,
						"group children"
					),
					"group children"
				)
			);
		}

		private List<String> legacyDefinitionIds()
		{
			List<String> result = new ArrayList<>();
			Set<String> seen = new HashSet<>();

			for (String definitionId
				: requireList(
					definitionIds,
					"group definitionIds"
				))
			{
				String normalized =
					PriorityLibraryEntry.definition(
						definitionId
					).getTargetId();

				if (!seen.add(normalized))
				{
					throw new IllegalArgumentException(
						"Duplicate definition ID: "
							+ normalized
					);
				}

				result.add(normalized);
			}

			return List.copyOf(result);
		}
	}

	private static final class LibraryEntryDocument
	{
		@SerializedName("type")
		private String type;

		@SerializedName("targetId")
		private String targetId;

		private static LibraryEntryDocument fromDomain(
			PriorityLibraryEntry entry)
		{
			LibraryEntryDocument document =
				new LibraryEntryDocument();

			document.type = entry.getType().name();
			document.targetId = entry.getTargetId();
			return document;
		}

		private PriorityLibraryEntry toDomain()
		{
			return new PriorityLibraryEntry(
				PriorityLibraryEntry.Type.valueOf(type),
				targetId
			);
		}
	}

	private static List<PriorityLibraryEntry>
	decodeLibraryEntries(
		List<LibraryEntryDocument> documents,
		String fieldName)
	{
		List<PriorityLibraryEntry> result =
			new ArrayList<>();

		for (LibraryEntryDocument document : documents)
		{
			result.add(
				requireEntry(
					document,
					fieldName + " must not contain null"
				).toDomain()
			);
		}

		return result;
	}

	private static final class BindingDocument
	{
		@SerializedName("id")
		private String id;

		@SerializedName("bankTagName")
		private String bankTagName;

		@SerializedName("slots")
		private List<SlotDocument> slots;

		private static BindingDocument fromDomain(
			BankTagBinding binding)
		{
			BindingDocument document =
				new BindingDocument();

			document.id = binding.getId();
			document.bankTagName = binding.getBankTagName();
			document.slots = new ArrayList<>();

			for (BankTagSlotBinding slot
				: binding.getSlots())
			{
				document.slots.add(
					SlotDocument.fromDomain(slot)
				);
			}

			return document;
		}

		private BankTagBinding toDomain()
		{
			List<BankTagSlotBinding> decodedSlots =
				new ArrayList<>();

			for (SlotDocument slot
				: requireList(slots, "binding slots"))
			{
				decodedSlots.add(
					requireEntry(
						slot,
						"binding slots must not contain null"
					).toDomain()
				);
			}

			return new BankTagBinding(
				id,
				bankTagName,
				decodedSlots
			);
		}
	}

	private static final class SlotDocument
	{
		@SerializedName("cellId")
		private String cellId;

		@SerializedName("definitionId")
		private String definitionId;

		@SerializedName("index")
		private int index;

		@SerializedName("fallbackExactItemId")
		private int fallbackExactItemId;

		@SerializedName("lastProjectedExactItemId")
		private int lastProjectedExactItemId;

		private static SlotDocument fromDomain(
			BankTagSlotBinding slot)
		{
			SlotDocument document = new SlotDocument();
			CellPlacement placement = slot.getPlacement();

			document.cellId = placement.getCellId();
			document.definitionId =
				placement.getDefinitionId();
			document.index = placement.getIndex();
			document.fallbackExactItemId =
				slot.getFallbackExactItemId();
			document.lastProjectedExactItemId =
				slot.getLastProjectedExactItemId();

			return document;
		}

		private BankTagSlotBinding toDomain()
		{
			return new BankTagSlotBinding(
				new CellPlacement(
					cellId,
					definitionId,
					index
				),
				fallbackExactItemId,
				lastProjectedExactItemId
			);
		}
	}
}
