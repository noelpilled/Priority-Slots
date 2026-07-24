package com.priorityslots.banktags;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeferredCleanupTrackerTest
{
	private static final int FIRST_ITEM = 1005;
	private static final int SECOND_ITEM = 1003;

	@Test
	public void completedSignatureIsNotScheduledAgain()
	{
		DeferredCleanupTracker tracker =
				new DeferredCleanupTracker();

		DeferredCleanupTracker.Signature signature =
				signature(
						"binding-1",
						"herbs",
						Set.of(FIRST_ITEM),
						Set.of(0)
				);

		tracker.replaceCurrent(List.of(signature));

		DeferredCleanupTracker.Token token =
				tracker.begin(signature).orElseThrow();

		assertTrue(tracker.isCurrent(token));

		tracker.complete(token);

		assertFalse(
				tracker.begin(signature).isPresent()
		);
	}

	@Test
	public void changedBindingInvalidatesPendingToken()
	{
		DeferredCleanupTracker tracker =
				new DeferredCleanupTracker();

		DeferredCleanupTracker.Signature original =
				signature(
						"binding-1",
						"herbs",
						Set.of(FIRST_ITEM),
						Set.of(0)
				);

		tracker.replaceCurrent(List.of(original));

		DeferredCleanupTracker.Token oldToken =
				tracker.begin(original).orElseThrow();

		DeferredCleanupTracker.Signature moved =
				signature(
						"binding-1",
						"herbs",
						Set.of(FIRST_ITEM),
						Set.of(8)
				);

		tracker.replaceCurrent(List.of(moved));

		assertFalse(tracker.isCurrent(oldToken));
		assertTrue(tracker.begin(moved).isPresent());
	}

	@Test
	public void changedTagInvalidatesPendingToken()
	{
		DeferredCleanupTracker tracker =
				new DeferredCleanupTracker();

		DeferredCleanupTracker.Signature original =
				signature(
						"binding-1",
						"herbs",
						Set.of(FIRST_ITEM),
						Set.of(0)
				);

		tracker.replaceCurrent(List.of(original));

		DeferredCleanupTracker.Token oldToken =
				tracker.begin(original).orElseThrow();

		DeferredCleanupTracker.Signature renamed =
				signature(
						"binding-1",
						"supplies",
						Set.of(FIRST_ITEM),
						Set.of(0)
				);

		tracker.replaceCurrent(List.of(renamed));

		assertFalse(tracker.isCurrent(oldToken));
		assertTrue(tracker.begin(renamed).isPresent());
	}

	@Test
	public void removedBindingInvalidatesPendingToken()
	{
		DeferredCleanupTracker tracker =
				new DeferredCleanupTracker();

		DeferredCleanupTracker.Signature signature =
				signature(
						"binding-1",
						"herbs",
						Set.of(FIRST_ITEM),
						Set.of(0)
				);

		tracker.replaceCurrent(List.of(signature));

		DeferredCleanupTracker.Token token =
				tracker.begin(signature).orElseThrow();

		tracker.replaceCurrent(List.of());

		assertFalse(tracker.isCurrent(token));
	}

	@Test
	public void resetInvalidatesPendingToken()
	{
		DeferredCleanupTracker tracker =
				new DeferredCleanupTracker();

		DeferredCleanupTracker.Signature signature =
				signature(
						"binding-1",
						"herbs",
						Set.of(FIRST_ITEM),
						Set.of(0)
				);

		tracker.replaceCurrent(List.of(signature));

		DeferredCleanupTracker.Token token =
				tracker.begin(signature).orElseThrow();

		tracker.reset();

		assertFalse(tracker.isCurrent(token));
	}

	@Test
	public void failedCleanupCanBeRetried()
	{
		DeferredCleanupTracker tracker =
				new DeferredCleanupTracker();

		DeferredCleanupTracker.Signature signature =
				signature(
						"binding-1",
						"herbs",
						Set.of(
								FIRST_ITEM,
								SECOND_ITEM
						),
						Set.of(0)
				);

		tracker.replaceCurrent(List.of(signature));

		Optional<DeferredCleanupTracker.Token> first =
				tracker.begin(signature);

		assertTrue(first.isPresent());

		tracker.fail(first.get());

		assertTrue(
				tracker.begin(signature).isPresent()
		);
	}

	private static DeferredCleanupTracker.Signature
	signature(
			String bindingId,
			String tagName,
			Set<Integer> itemIds,
			Set<Integer> indices)
	{
		return new DeferredCleanupTracker.Signature(
				bindingId,
				tagName,
				itemIds,
				indices
		);
	}
}
