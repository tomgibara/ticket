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
 * A unique origin is associated with each ticket machine in a factory. An
 * origin combines the open (ie. non secret) elements of a ticket origin with a
 * specification number.
 * <p>
 * The {@link #toString()} method always returns a string key which uniquely
 * identifies one {@link TicketMachine} among all possible machines in a
 * {@link TicketFactory}. This identifier may be used by {@link TicketSequences}
 * to maintain a separate sequence for each machine.
 *
 * @author Tom Gibara
 *
 * @param <R>
 *            the ticket origin type
 */

//TODO find an alternative name
public final class TicketOrigin<R> {

	final int specNumber;
	final BitVector openOriginBits;
	final R origin;
	final Object[] values;
	private String id = null;

	TicketOrigin(int specNumber, BitVector openOriginBits, R origin, Object... values) {
		this.specNumber = specNumber;
		this.openOriginBits = openOriginBits;
		this.origin = origin;
		this.values = values;
	}

	// public accessors

	public int getSpecNumber() {
		return specNumber;
	}

	public R getOrigin() {
		return origin;
	}

	// object methods

	@Override
	public int hashCode() {
		return specNumber + openOriginBits.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof TicketOrigin)) return false;
		TicketOrigin<?> that = (TicketOrigin<?>) obj;
		if (this.specNumber != that.specNumber) return false;
		if (!this.openOriginBits.equals(that.openOriginBits)) return false;
		return true;
	}

	@Override
	public String toString() {
		return id == null ? id = openOriginBits.toString(16) + '0' + (specNumber + 1) : id;
	}
}
