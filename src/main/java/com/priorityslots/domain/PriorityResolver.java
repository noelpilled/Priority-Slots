package com.priorityslots.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PriorityResolver
{
	public SlotResolution resolve(
			CellPlacement placement,
			Map<String, PriorityDefinition> definitionsById,
			BankSnapshot bankSnapshot)
	{
		Objects.requireNonNull(placement, "placement");
		Objects.requireNonNull(
				definitionsById,
				"definitionsById"
		);
		Objects.requireNonNull(
				bankSnapshot,
				"bankSnapshot"
		);

		PriorityDefinition definition =
				definitionsById.get(
						placement.getDefinitionId()
				);

		if (definition == null)
		{
			return SlotResolution.unresolved(
					placement.getCellId(),
					placement.getDefinitionId(),
					placement.getIndex()
			);
		}

		Integer ghostItemId = null;

		for (PriorityTier tier : definition.getTiers())
		{
			for (Integer exactItemId
					: tier.getExactItemIds())
			{
				if (ghostItemId == null)
				{
					ghostItemId = exactItemId;
				}

				if (bankSnapshot.contains(exactItemId))
				{
					return SlotResolution.owned(
							placement.getCellId(),
							placement.getDefinitionId(),
							placement.getIndex(),
							exactItemId
					);
				}
			}
		}

		if (ghostItemId != null)
		{
			return SlotResolution.ghost(
					placement.getCellId(),
					placement.getDefinitionId(),
					placement.getIndex(),
					ghostItemId
			);
		}

		return SlotResolution.unresolved(
				placement.getCellId(),
				placement.getDefinitionId(),
				placement.getIndex()
		);
	}

	public List<SlotResolution> resolveView(
			PriorityView view,
			Map<String, PriorityDefinition> definitionsById,
			BankSnapshot bankSnapshot)
	{
		Objects.requireNonNull(view, "view");
		Objects.requireNonNull(
				definitionsById,
				"definitionsById"
		);
		Objects.requireNonNull(
				bankSnapshot,
				"bankSnapshot"
		);

		List<SlotResolution> resolutions =
				new ArrayList<>();

		for (CellPlacement placement
				: view.getPlacements())
		{
			resolutions.add(
					resolve(
							placement,
							definitionsById,
							bankSnapshot
					)
			);
		}

		resolutions.sort(
				Comparator.comparingInt(
						SlotResolution::getIndex
				)
		);

		return List.copyOf(resolutions);
	}
}
