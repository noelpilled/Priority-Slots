package com.priorityslots.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Value;
import lombok.With;

@Value
public class PriorityDefinition
{
	String id;

	@With
	String name;

	@With
	List<PriorityTier> tiers;

	public PriorityDefinition(
			String id,
			String name,
			List<PriorityTier> tiers)
	{
		this.id = requireNonBlank(id, "id");
		this.name = requireNonBlank(name, "name");

		Objects.requireNonNull(tiers, "tiers");

		Set<String> tierIds = new HashSet<>();
		Set<Integer> exactItemIds = new HashSet<>();

		for (PriorityTier tier : tiers)
		{
			Objects.requireNonNull(
					tier,
					"tiers must not contain null"
			);

			if (!tierIds.add(tier.getId()))
			{
				throw new IllegalArgumentException(
						"Duplicate tier ID: "
								+ tier.getId()
				);
			}

			for (Integer exactItemId
					: tier.getExactItemIds())
			{
				if (!exactItemIds.add(exactItemId))
				{
					throw new IllegalArgumentException(
							"Exact item ID appears in "
									+ "more than one tier: "
									+ exactItemId
					);
				}
			}
		}

		this.tiers = List.copyOf(tiers);
	}

	public static PriorityDefinition create(
			String name,
			List<PriorityTier> tiers)
	{
		return new PriorityDefinition(
				UUID.randomUUID().toString(),
				name,
				tiers
		);
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
}
