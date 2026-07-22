package com.priorityslots.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Value;
import lombok.With;

@Value
public class PriorityTier
{
    String id;

    @With
    List<Integer> exactItemIds;

    public PriorityTier(String id, List<Integer> exactItemIds)
    {
        this.id = requireNonBlank(id, "id");
        Objects.requireNonNull(exactItemIds, "exactItemIds");

        for (Integer itemId : exactItemIds)
        {
            if (itemId == null || itemId <= 0)
            {
                throw new IllegalArgumentException(
                        "exactItemIds must contain only positive item IDs"
                );
            }
        }

        this.exactItemIds = List.copyOf(exactItemIds);
    }

    public static PriorityTier create(List<Integer> exactItemIds)
    {
        return new PriorityTier(
                UUID.randomUUID().toString(),
                exactItemIds
        );
    }

    private static String requireNonBlank(String value, String fieldName)
    {
        Objects.requireNonNull(value, fieldName);

        String trimmed = value.trim();
        if (trimmed.isEmpty())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return trimmed;
    }
}