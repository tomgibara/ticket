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

final class TicketOrigin<R> {

	final int specNumber;
	final BitVector openOriginBits;
	final R origin;
	final Object[] values;

	TicketOrigin(int specNumber, BitVector openOriginBits, R origin, Object... values) {
		this.specNumber = specNumber;
		this.openOriginBits = openOriginBits;
		this.origin = origin;
		this.values = values;
	}

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
}
