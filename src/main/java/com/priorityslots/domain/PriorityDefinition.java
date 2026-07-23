package com.priorityslots.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Value;
import lombok.With;

@Value
public class PriorityDefinition {
	String id;

	@With
	String name;

	@With
	List<PriorityTier> tiers;

	public PriorityDefinition(
			String id,
			String name,
			List<PriorityTier> tiers) {
		this.id = requireNonBlank(id, "id");
		this.name = requireNonBlank(name, "name");

		Objects.requireNonNull(tiers, "tiers");

		for (PriorityTier tier : tiers) {
			Objects.requireNonNull(
					tier,
					"tiers must not contain null"
			);
		}

		this.tiers = List.copyOf(tiers);
	}

	public static PriorityDefinition create(
			String name,
			List<PriorityTier> tiers) {
		return new PriorityDefinition(
				UUID.randomUUID().toString(),
				name,
				tiers
		);
	}

	private static String requireNonBlank(String value, String fieldName) {
		Objects.requireNonNull(value, fieldName);

		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}

		return trimmed;
	}
}
