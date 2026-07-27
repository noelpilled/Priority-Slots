package com.priorityslots.banktags;

import com.priorityslots.domain.BankTagBinding;
import com.priorityslots.domain.PriorityState;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.client.util.Text;

/**
 * Matches Bank Tags config storage keys for cleanup invalidation only.
 * Priority resolution continues to use the exact candidate IDs unchanged.
 */
final class BankTagsMembershipChangeDetector
{
	private static final String ITEM_KEY_PREFIX = "item_";

	private final IntUnaryOperator canonicalize;
	private final IntUnaryOperator variationMap;

	BankTagsMembershipChangeDetector(
		IntUnaryOperator canonicalize,
		IntUnaryOperator variationMap)
	{
		this.canonicalize = Objects.requireNonNull(
			canonicalize,
			"canonicalize"
		);
		this.variationMap = Objects.requireNonNull(
			variationMap,
			"variationMap"
		);
	}

	Set<String> affectedBindingIds(
		PriorityState state,
		String configKey,
		String newValue)
	{
		Objects.requireNonNull(state, "state");

		Integer storedItemId = storedItemId(configKey);
		Set<String> addedTags = standardizedTags(newValue);

		if (storedItemId == null || addedTags.isEmpty())
		{
			return Set.of();
		}

		Set<String> affectedBindingIds = new HashSet<>();

		for (BankTagBinding binding : state.getBindings())
		{
			if (!addedTags.contains(Text.standardize(
				binding.getBankTagName()
			)))
			{
				continue;
			}

			for (Integer exactItemId
				: BankTagManagedItems.collect(binding, state))
			{
				if (matchesStoredItemId(exactItemId, storedItemId))
				{
					affectedBindingIds.add(binding.getId());
					break;
				}
			}
		}

		return Set.copyOf(affectedBindingIds);
	}

	private boolean matchesStoredItemId(
		int exactItemId,
		int storedItemId)
	{
		int canonicalItemId = canonicalize.applyAsInt(exactItemId);
		int variationItemId = -variationMap.applyAsInt(canonicalItemId);

		return storedItemId == canonicalItemId
			|| storedItemId == variationItemId;
	}

	private static Integer storedItemId(String configKey)
	{
		if (configKey == null || !configKey.startsWith(ITEM_KEY_PREFIX))
		{
			return null;
		}

		try
		{
			return Integer.parseInt(
				configKey.substring(ITEM_KEY_PREFIX.length())
			);
		}
		catch (NumberFormatException exception)
		{
			return null;
		}
	}

	private static Set<String> standardizedTags(String serializedTags)
	{
		if (serializedTags == null || serializedTags.trim().isEmpty())
		{
			return Set.of();
		}

		Set<String> tags = new HashSet<>();

		for (String tag : Text.fromCSV(serializedTags))
		{
			String standardizedTag = Text.standardize(tag);

			if (!standardizedTag.isEmpty())
			{
				tags.add(standardizedTag);
			}
		}

		return Set.copyOf(tags);
	}
}
