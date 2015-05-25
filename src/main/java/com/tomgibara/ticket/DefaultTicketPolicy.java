package com.tomgibara.ticket;

class DefaultTicketPolicy implements TicketPolicy {

	@Override
	public int getTicketCharLimit() {
		return 256;
	}

}
