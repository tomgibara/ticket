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

import com.tomgibara.bits.BitVector;

/**
 * <p>
 * A ticket is a unique timestamped token. Tickets are initially created by
 * {@link TicketMachine} instances. They are designed to be serialized into
 * compact ASCII strings via their {@link #toString()} method from which they
 * may be reconstructed by a {@link TicketFactory}.
 * <p>
 * In addition to a timestamp (of configurable granularity), a ticket can store
 * application specific data which records information about the origin of the
 * ticket (for example, which node generated the ticket in a distributed system)
 * and ticket specific data (for example, the id of an associated session or
 * transaction).
 * <p>
 * Instances of this class are safe for concurrent access by multiple threads.
 *
 * @author Tom Gibara
 *
 * @param <R>
 *            the type of origin information recorded
 * @param <D>
 *            the type of data information recorded
 */

public final class Ticket<R, D> {

	// statics

	/**
	 * Controls the accuracy of ticket timestamps. Less granular timestamps have
	 * the virtue of producing shorter tickets. Additional sequential numbering
	 * ensures that all tickets with a given origin are uniquely identified,
	 * irrespective of the granularity of their timestamps.
	 */

	//TODO consider supporting DAY
	public enum Granularity {

		MILLISECOND(1),
		SECOND(1000          ),
		MINUTE(1000 * 60     ),
		HOUR  (1000 * 60 * 60);

		private int scale;

		private Granularity(int scale) {
			this.scale = scale;
		}

		long toTimestamp(long millis) {
			return millis / scale;
		}

		long toMillis(long timestamp) {
			return timestamp * scale;
		}
	}

	// fields

	private final TicketSpec spec;
	private final BitVector bits;
	private final long millis;
	private final long seq;
	private final R origin;
	private final D data;
	private final String string;

	// constructors

	Ticket(TicketSpec spec, BitVector bits, long timestamp, long seq, R origin, D data, String string) {
		this.spec = spec;
		this.bits = bits;
		this.millis = spec.timestampToMillis(timestamp);
		this.seq = seq;
		this.origin = origin;
		this.data = data;
		this.string = string;
	}

	// accessors

	/**
	 * The specification by which this ticket was constructed.
	 *
	 * @return the specification of this ticket
	 */

	public TicketSpec getSpecification() {
		return spec;
	}

	/**
	 * The time at which this ticket was originally created. The timestamp will
	 * only be accurate to up to the specified granularity.
	 *
	 * @return the timestamp in milliseconds
	 * @see Granularity
	 */

	public long getTimestamp() {
		return millis;
	}

	/**
	 * The sequence number of the ticket. It forms a unique combination when
	 * taken with the ticket origin and its timestamp.
	 *
	 * @return the sequence number
	 */

	public long getSequenceNumber() {
		return seq;
	}

	/**
	 * The origin of the ticket. This returns interface structured information
	 * about the source of the ticket, or null in the case of the Void origin
	 * type.
	 *
	 * @return the origin of the ticket, possibly null
	 * @see TicketConfig#getOriginType()
	 */

	public R getOrigin() {
		return origin;
	}

	/**
	 * Information associated with this specific ticket, or null if the data
	 * type is Void.
	 *
	 * @return data about the ticket, possibly null
	 * @see TicketConfig#getDataType()
	 */

	public D getData() {
		return data;
	}

	// object methods

	@Override
	public int hashCode() {
		// spec hashcode specifically omitted for lack of practical application
		return bits.hashCode();
	}

	/**
	 * Two tickets are equal if the information they encode is identical under a
	 * shared specification. Note that equal tickets may have String forms which
	 * differ in their formatting.
	 */

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof Ticket)) return false;
		Ticket<?,?> that = (Ticket<?,?>) obj;
		if (!this.bits.equals(that.bits)) return false;
		if (!this.spec.equals(that.spec)) return false;
		return true;
	}

	/**
	 * A compact ASCII encoding of the information recorded in this ticket. This
	 * is the mechanism by which tickets are intended to be shared with users or
	 * other system components.
	 *
	 * @return a compact ASCII string
	 * @see TicketFormat
	 */

	@Override
	public String toString() {
		return string;
	}

}
