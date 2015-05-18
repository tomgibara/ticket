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

public final class Ticket<R, D> {

	// statics

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
	private final int seq;
	private final R origin;
	private final D data;
	private final String string;

	// constructors

	Ticket(TicketSpec spec, BitVector bits, long timestamp, int seq, R origin, D data, String string) {
		this.spec = spec;
		this.bits = bits;
		this.millis = spec.timestampToMillis(timestamp);
		this.seq = seq;
		this.origin = origin;
		this.data = data;
		this.string = string;
	}

	// accessors

	public TicketSpec getSpecification() {
		return spec;
	}

	public long getTimestamp() {
		return millis;
	}

	public int getSequenceNumber() {
		return seq;
	}

	public R getOrigin() {
		return origin;
	}

	public D getData() {
		return data;
	}

	// object methods

	@Override
	public int hashCode() {
		// spec hashcode specifically omitted for lack of practical application
		return bits.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof Ticket)) return false;
		Ticket<?,?> that = (Ticket<?,?>) obj;
		if (!this.bits.equals(that.bits)) return false;
		if (!this.spec.equals(that.spec)) return false;
		return true;
	}

	@Override
	public String toString() {
		return string;
	}

}
