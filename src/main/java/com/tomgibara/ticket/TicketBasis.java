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

import java.math.BigInteger;

import com.tomgibara.bits.BitVector;

/**
 * <p>
 * Each ticket machine in a factory creates tickets with a unique basis. A
 * basis combines the a ticket origin with a specification number.
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

public final class TicketBasis<R> {

	final int specNumber;
	final BitVector openOriginBits;
	final BitVector secretOriginBits;
	final R origin;
	final Object[] values;
	private String id = null;

	TicketBasis(int specNumber, BitVector openOriginBits, BitVector secretOriginBits, R origin, Object... values) {
		this.specNumber = specNumber;
		this.openOriginBits = openOriginBits;
		this.secretOriginBits = secretOriginBits;
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
		return specNumber + openOriginBits.hashCode() + secretOriginBits.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof TicketBasis)) return false;
		TicketBasis<?> that = (TicketBasis<?>) obj;
		if (this.specNumber != that.specNumber) return false;
		if (!this.openOriginBits.equals(that.openOriginBits)) return false;
		if (!this.secretOriginBits.equals(that.secretOriginBits)) return false;
		return true;
	}

	@Override
	public String toString() {
		if (id != null) return id;
		if (secretOriginBits.size() == 0) {
			id = openOriginBits.toString(16) + '0' + (specNumber + 1);
		} else {
			KeccakDigest digest = new KeccakDigest(TicketFactory.DIGEST_SIZE);
			byte[] openBytes = openOriginBits.toByteArray();
			byte[] secretBytes = secretOriginBits.toByteArray();
			digest.update(openBytes, 0, openBytes.length);
			digest.update(secretBytes, 0, secretBytes.length);
			digest.update((byte) (specNumber >> 24));
			digest.update((byte) (specNumber >> 16));
			digest.update((byte) (specNumber >>  8));
			digest.update((byte) (specNumber      ));
			byte[] magnitude = new byte[digest.getDigestSize()];
			digest.doFinal(magnitude, 0);
			// trims leading zeros, but shouldn't be an issue
			id = new BigInteger(1, magnitude).toString(16);
		}
		return id;
	}
}
