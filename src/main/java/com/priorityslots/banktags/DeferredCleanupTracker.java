package com.priorityslots.banktags;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class DeferredCleanupTracker
{
	private long generation;

	private final Map<String, Signature>
			currentByBindingId = new HashMap<>();

	private final Map<String, Signature>
			pendingByBindingId = new HashMap<>();

	private final Map<String, Signature>
			completedByBindingId = new HashMap<>();

	void replaceCurrent(
			Collection<Signature> signatures)
	{
		Objects.requireNonNull(
				signatures,
				"signatures"
		);

		Map<String, Signature> replacement =
				new HashMap<>();

		for (Signature signature : signatures)
		{
			Objects.requireNonNull(
					signature,
					"signatures must not contain null"
			);

			Signature previous = replacement.put(
					signature.getBindingId(),
					signature
			);

			if (previous != null)
			{
				throw new IllegalArgumentException(
						"Duplicate cleanup binding ID: "
								+ signature.getBindingId()
				);
			}
		}

		currentByBindingId.clear();
		currentByBindingId.putAll(replacement);

		pendingByBindingId.entrySet().removeIf(
				entry -> !entry.getValue().equals(
						currentByBindingId.get(
								entry.getKey()
						)
				)
		);

		completedByBindingId.entrySet().removeIf(
				entry -> !entry.getValue().equals(
						currentByBindingId.get(
								entry.getKey()
						)
				)
		);
	}

	Optional<Token> begin(Signature signature)
	{
		Objects.requireNonNull(signature, "signature");

		String bindingId = signature.getBindingId();

		if (!signature.equals(
				currentByBindingId.get(bindingId)))
		{
			return Optional.empty();
		}

		if (signature.equals(
				pendingByBindingId.get(bindingId))
				|| signature.equals(
				completedByBindingId.get(bindingId)))
		{
			return Optional.empty();
		}

		pendingByBindingId.put(
				bindingId,
				signature
		);

		return Optional.of(
				new Token(generation, signature)
		);
	}

	boolean isCurrent(Token token)
	{
		Objects.requireNonNull(token, "token");

		Signature signature = token.getSignature();
		String bindingId = signature.getBindingId();

		return token.getGeneration() == generation
				&& signature.equals(
				currentByBindingId.get(bindingId))
				&& signature.equals(
				pendingByBindingId.get(bindingId));
	}

	void complete(Token token)
	{
		if (!isCurrent(token))
		{
			return;
		}

		Signature signature = token.getSignature();
		String bindingId = signature.getBindingId();

		pendingByBindingId.remove(bindingId);

		completedByBindingId.put(
				bindingId,
				signature
		);
	}

	void fail(Token token)
	{
		Objects.requireNonNull(token, "token");

		if (token.getGeneration() != generation)
		{
			return;
		}

		Signature signature = token.getSignature();
		String bindingId = signature.getBindingId();

		if (signature.equals(
				pendingByBindingId.get(bindingId)))
		{
			pendingByBindingId.remove(bindingId);
		}
	}

	void reset()
	{
		generation++;

		currentByBindingId.clear();
		pendingByBindingId.clear();
		completedByBindingId.clear();
	}

	static final class Signature
	{
		private final String bindingId;
		private final String standardizedTagName;
		private final Set<Integer> managedItemIds;
		private final Set<Integer> reservedIndices;

		Signature(
				String bindingId,
				String standardizedTagName,
				Set<Integer> managedItemIds,
				Set<Integer> reservedIndices)
		{
			this.bindingId = requireNonBlank(
					bindingId,
					"bindingId"
			);

			this.standardizedTagName = requireNonBlank(
					standardizedTagName,
					"standardizedTagName"
			);

			this.managedItemIds = copyPositiveValues(
					managedItemIds,
					"managedItemIds"
			);

			this.reservedIndices =
					copyNonNegativeValues(
							reservedIndices,
							"reservedIndices"
					);
		}

		String getBindingId()
		{
			return bindingId;
		}

		String getStandardizedTagName()
		{
			return standardizedTagName;
		}

		Set<Integer> getManagedItemIds()
		{
			return managedItemIds;
		}

		Set<Integer> getReservedIndices()
		{
			return reservedIndices;
		}

		@Override
		public boolean equals(Object object)
		{
			if (this == object)
			{
				return true;
			}

			if (!(object instanceof Signature))
			{
				return false;
			}

			Signature other = (Signature) object;

			return bindingId.equals(other.bindingId)
					&& standardizedTagName.equals(
					other.standardizedTagName)
					&& managedItemIds.equals(
					other.managedItemIds)
					&& reservedIndices.equals(
					other.reservedIndices);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(
					bindingId,
					standardizedTagName,
					managedItemIds,
					reservedIndices
			);
		}
	}

	static final class Token
	{
		private final long generation;
		private final Signature signature;

		private Token(
				long generation,
				Signature signature)
		{
			this.generation = generation;
			this.signature = Objects.requireNonNull(
					signature,
					"signature"
			);
		}

		long getGeneration()
		{
			return generation;
		}

		Signature getSignature()
		{
			return signature;
		}
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

	private static Set<Integer> copyPositiveValues(
			Set<Integer> values,
			String fieldName)
	{
		Objects.requireNonNull(values, fieldName);

		for (Integer value : values)
		{
			if (value == null || value <= 0)
			{
				throw new IllegalArgumentException(
						fieldName
								+ " must contain only "
								+ "positive values"
				);
			}
		}

		return Set.copyOf(values);
	}

	private static Set<Integer> copyNonNegativeValues(
			Set<Integer> values,
			String fieldName)
	{
		Objects.requireNonNull(values, fieldName);

		for (Integer value : values)
		{
			if (value == null || value < 0)
			{
				throw new IllegalArgumentException(
						fieldName
								+ " must contain only "
								+ "non-negative values"
				);
			}
		}

		return Set.copyOf(values);
	}
}
