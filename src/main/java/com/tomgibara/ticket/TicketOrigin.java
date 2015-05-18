package com.tomgibara.ticket;

import com.tomgibara.bits.BitVector;

final class TicketOrigin<R> {

	final int specNumber;
	final BitVector originBits;
	final R origin;

	TicketOrigin(int specNumber, BitVector originBits, R origin) {
		this.specNumber = specNumber;
		this.originBits = originBits;
		this.origin = origin;
	}

	@Override
	public int hashCode() {
		return specNumber + originBits.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof TicketOrigin)) return false;
		TicketOrigin<?> that = (TicketOrigin<?>) obj;
		if (this.specNumber != that.specNumber) return false;
		if (!this.originBits.equals(that.originBits)) return false;
		return true;
	}
}
