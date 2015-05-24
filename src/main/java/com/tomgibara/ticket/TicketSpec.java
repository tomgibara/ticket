/*
 * Copyright 2015 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.tomgibara.ticket;

import java.io.Serializable;
import java.util.Calendar;
import java.util.TimeZone;

import com.tomgibara.bits.BitVector;
import com.tomgibara.bits.BitVectorWriter;

/**
 * Specifies the structure of tickets created by a {@link TicketFactory}.
 *
 * @author Tom Gibara
 */

public final class TicketSpec implements Serializable {

	private static final long serialVersionUID = -4576985625208064336L;

	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	private static final State DEFAULT_STATE = new State(UTC, Ticket.Granularity.SECOND, 2015, 0);
	private static final TicketSpec DEFAULT = new TicketSpec(DEFAULT_STATE);
	private static final BitVector NO_BITS = new BitVector(0);

	private static class State implements Serializable {

		private static final long serialVersionUID = -5164834176460281670L;

		private final TimeZone timeZone;
		private final Ticket.Granularity granularity;
		private final int originYear;
		private final int hashLength;

		private State(TimeZone timeZone, Ticket.Granularity granularity, int originYear, int hashLength) {
			this.timeZone = timeZone;
			this.granularity = granularity;
			this.originYear = originYear;
			this.hashLength = hashLength;
		}

		State setTimeZone(TimeZone timeZone) {
			return new State(timeZone, granularity, originYear, hashLength);
		}

		State setGranularity(Ticket.Granularity granularity) {
			return new State(timeZone, granularity, originYear, hashLength);
		}

		State setOriginYear(int originYear) {
			return new State(timeZone, granularity, originYear, hashLength);
		}

		State setHashLength(int hashLength) {
			return new State(timeZone, granularity, originYear, hashLength);
		}

		@Override
		public int hashCode() {
			return timeZone.hashCode() + 31 * (granularity.hashCode() + 31 * (originYear + 31 * hashLength));
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (!(obj instanceof State)) return false;
			State that = (State) obj;
			if (this.originYear != that.originYear) return false;
			if (this.hashLength != that.hashLength) return false;
			if (this.granularity != that.granularity) return false;
			if (!this.timeZone.equals(that.timeZone)) return false;
			return true;
		}

		@Override
		public String toString() {
			return String.format(
					"timeZone: %s, granularity: %s, originYear: %d, hashLength: %d",
					timeZone, granularity, originYear, hashLength
					);
		}

		// serialization methods

		private Object readResolve() {
			return new TicketSpec(this);
		}

	}

	/**
	 * Used to create new {@link TicketSpec} instances. Builders are usually
	 * obtained via {@link TicketSpec#defaultBuilder()} but may also be obtained
	 * from any existing specification via {@link TicketSpec#builder()}.
	 *
	 * @author Tom Gibara
	 *
	 */

	public static class Builder {

		private State state;

		Builder(State state) {
			this.state = state;
		}

		/**
		 * Sets the timezone in which tickets timestamps will be computed.
		 *
		 * @param timeZone
		 *            a timezone, not null
		 * @return the builder
		 */

		public Builder setTimeZone(TimeZone timeZone) {
			if (timeZone == null) throw new IllegalArgumentException("null timeZone");
			state = state.setTimeZone(timeZone);
			return this;
		}

		/**
		 * Sets the granularity of the timestamps which will be recorded against
		 * tickets. Coarser granularities provide lower resolution timing
		 * information about tickets but require less data to be stored in the
		 * tickets.
		 *
		 * @param granularity
		 *            the required timestamp granularity
		 * @return the builder
		 */

		public Builder setGranularity(Ticket.Granularity granularity) {
			if (granularity == null) throw new IllegalArgumentException("null granularity");
			state = state.setGranularity(granularity);
			return this;
		}

		/**
		 * Specifies a year before which no ticket could feasibly be generated.
		 *
		 * @param originYear
		 *            the year dot
		 * @return the builder
		 */
		public Builder setOriginYear(int originYear) {
			state = state.setOriginYear(originYear);
			return this;
		}

		/**
		 * Specifies the number of bits that will used to record a hash inside
		 * the ticket. The hash can prevent transmission errors from incorrectly
		 * being recognized as valid. When combined with secret keys, hashes may
		 * also prevent forgery of tickets.
		 *
		 * @param hashLength
		 *            the number of bits in the hash
		 * @return the builder
		 * @throws IllegalArgumentException
		 *             if the hash length is negative, or exceeds an
		 *             implementation specific maximum (currently 160 bits)
		 * @see TicketConfig#newFactory(TicketSequences, byte[]...)
		 */

		public Builder setHashLength(int hashLength) {
			if (hashLength < 0) throw new IllegalArgumentException("hashLength negative");
			if (hashLength > TicketFactory.DIGEST_SIZE) throw new IllegalArgumentException("hashLength too large");
			state = state.setHashLength(hashLength);
			return this;
		}

		/**
		 * Builds a new specification using the values recorded by this builder.
		 *
		 * @return a new ticket specification
		 */
		public TicketSpec build() {
			return new TicketSpec(state);
		}
	}

	/**
	 * A default specification. This specification will be used by a factory if
	 * no other specifications are declared in its configuration.
	 * <p>
	 * The origin year is 2015 (the inception year of this library), the
	 * timezone is UTC and timestamps have {@link Ticket.Granularity#SECOND}. No
	 * hash is specified (ie. the hash length is zero).
	 *
	 * @return the default ticket specification.
	 * @see TicketConfig#newFactory(TicketSequences, byte[]...)
	 */

	public static TicketSpec getDefault() {
		return DEFAULT;
	}

	/**
	 * A builder which is initialized to match the default ticket specification.
	 *
	 * @return a new default builder
	 */

	//TODO consider renaming to include "new"
	public static Builder defaultBuilder() {
		return new Builder(DEFAULT_STATE);
	}

	private final State state;
	private final long originMillis;

	private TicketSpec(State state) {
		this.state = state;
		originMillis = computeOriginMillis();
	}

	// public accessors

	/**
	 * The specified timezone.
	 *
	 * @return a timezone, never null
	 */
	public TimeZone getTimeZone() {
		return state.timeZone;
	}

	/**
	 * The specified timestamp granularity
	 *
	 * @return a granularity, never null
	 */

	public Ticket.Granularity getGranularity() {
		return state.granularity;
	}

	/**
	 * The specified origin year.
	 *
	 * @return the origin year
	 */

	public int getOriginYear() {
		return state.originYear;
	}

	/**
	 * The specified number of bits recorded when hashing the ticket.
	 *
	 * @return the hash length, or zero if the tickets are not hashed
	 */

	public int getHashLength() {
		return state.hashLength;
	}

	// public methods

	/**
	 * A new builder that is initialized to with the specification settings.
	 *
	 * @return a new builder
	 */
	// TODO consider renaming this method too: newBuilder()
	public Builder builder() {
		return new Builder(state);
	}

	// object methods

	@Override
	public int hashCode() {
		return state.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof TicketSpec)) return false;
		TicketSpec that = (TicketSpec) obj;
		return this.state.equals(that.state);
	}

	@Override
	public String toString() {
		return state.toString();
	}

	// serialization methods

	private Object writeReplace() {
		return state;
	}

	// utility methods

	long timestamp() {
		long now = System.currentTimeMillis();
		long millis = now - originMillis;
		return state.granularity.toTimestamp(millis);
	}

	long timestampToMillis(long timestamp) {
		return originMillis + state.granularity.toMillis(timestamp);
	}

	private long computeOriginMillis() {
		Calendar calendar = Calendar.getInstance(state.timeZone);
		calendar.set(state.originYear, 0, 1, 0, 0, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTimeInMillis();
	}

	int writeHash(KeccakDigest digest, BitVectorWriter writer) {
		return state.hashLength == 0 ? 0 : hash(digest, writer.toImmutableBitVector()).writeTo(writer);
	}

	BitVector hash(KeccakDigest digest, BitVector vector) {
		int length = state.hashLength;
		if (length == 0) return NO_BITS;

		digest = new KeccakDigest(digest);
		byte[] bytes = vector.toByteArray();
		digest.update(bytes, 0, bytes.length);
		byte[] hash = new byte[ digest.getDigestSize() ];
		digest.doFinal(hash, 0);
		return BitVector.fromByteArray(hash, length);
	}

}
