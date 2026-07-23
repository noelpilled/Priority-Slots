package com.priorityslots.banktags;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Text;

@Singleton
public final class BankTagLayoutReader
{
	private final BankTagLayoutStorage storage;

	@Inject
	public BankTagLayoutReader(
			ConfigBankTagLayoutStorage storage)
	{
		this((BankTagLayoutStorage) storage);
	}

	BankTagLayoutReader(
			BankTagLayoutStorage storage)
	{
		this.storage = Objects.requireNonNull(
				storage,
				"storage"
		);
	}

	public Optional<BankTagLayoutSnapshot> load(
			String bankTagName)
	{
		Objects.requireNonNull(
				bankTagName,
				"bankTagName"
		);

		if (bankTagName.trim().isEmpty())
		{
			throw new IllegalArgumentException(
					"bankTagName must not be blank"
			);
		}

		String serializedLayout =
				storage.read(bankTagName);

		if (serializedLayout == null)
		{
			return Optional.empty();
		}

		List<Integer> itemIds =
				new ArrayList<>();

		try
		{
			for (String value
					: Text.fromCSV(serializedLayout))
			{
				itemIds.add(
						Integer.parseInt(value)
				);
			}
		}
		catch (NumberFormatException exception)
		{
			throw new BankTagLayoutFormatException(
					"Bank Tags layout for '"
							+ bankTagName
							+ "' contains an invalid item ID",
					exception
			);
		}

		return Optional.of(
				new BankTagLayoutSnapshot(
						bankTagName,
						itemIds
				)
		);
	}
}
