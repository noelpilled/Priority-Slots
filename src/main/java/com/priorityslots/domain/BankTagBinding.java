package com.priorityslots.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Value;
import lombok.With;

@Value
public class BankTagBinding
{
	String id;

	@With
	String bankTagName;

	@With
	List<BankTagSlotBinding> slots;

	public BankTagBinding(
			String id,
			String bankTagName,
			List<BankTagSlotBinding> slots)
	{
		this.id = requireNonBlank(id, "id");

		this.bankTagName = requireNonBlank(
				bankTagName,
				"bankTagName"
		);

		Objects.requireNonNull(slots, "slots");

		Set<String> cellIds = new HashSet<>();
		Set<Integer> indices = new HashSet<>();

		for (BankTagSlotBinding slot : slots)
		{
			Objects.requireNonNull(
					slot,
					"slots must not contain null"
			);

			CellPlacement placement =
					slot.getPlacement();

			if (!cellIds.add(placement.getCellId()))
			{
				throw new IllegalArgumentException(
						"Duplicate cell ID: "
								+ placement.getCellId()
				);
			}

			if (!indices.add(placement.getIndex()))
			{
				throw new IllegalArgumentException(
						"Duplicate layout index: "
								+ placement.getIndex()
				);
			}
		}

		this.slots = List.copyOf(slots);
	}

	public static BankTagBinding create(
			String bankTagName,
			List<BankTagSlotBinding> slots)
	{
		return new BankTagBinding(
				UUID.randomUUID().toString(),
				bankTagName,
				slots
		);
	}

	public List<CellPlacement> placements()
	{
		List<CellPlacement> result =
				new ArrayList<>();

		for (BankTagSlotBinding slot : slots)
		{
			result.add(slot.getPlacement());
		}

		return List.copyOf(result);
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
