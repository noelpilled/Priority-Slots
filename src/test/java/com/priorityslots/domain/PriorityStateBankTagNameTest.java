package com.priorityslots.domain;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PriorityStateBankTagNameTest
{
	@Test
	public void rejectsDuplicateStandardizedBankTagNames()
	{
		BankTagBinding first =
				new BankTagBinding(
						"binding-1",
						"Herbs",
						List.of()
				);

		BankTagBinding duplicate =
				new BankTagBinding(
						"binding-2",
						"  herbs  ",
						List.of()
				);

		try
		{
			new PriorityState(
					List.of(),
					List.of(),
					List.of(
							first,
							duplicate
					)
			);

			fail(
					"Expected IllegalArgumentException"
			);
		}
		catch (IllegalArgumentException expected)
		{
			assertEquals(
					"Duplicate Bank Tag binding: herbs",
					expected.getMessage()
			);
		}
	}

	@Test
	public void allowsDifferentStandardizedBankTagNames()
	{
		PriorityState state =
				new PriorityState(
						List.of(),
						List.of(),
						List.of(
								new BankTagBinding(
										"binding-1",
										"Herbs",
										List.of()
								),
								new BankTagBinding(
										"binding-2",
										"Potions",
										List.of()
								)
						)
				);

		assertEquals(2, state.getBindings().size());
	}
}
