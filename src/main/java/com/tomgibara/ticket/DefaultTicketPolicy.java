package com.tomgibara.ticket;

//TODO make public
class DefaultTicketPolicy implements TicketPolicy {

	@Override
	public int getTicketCharLimit() {
		return 256;
	}

	@Override
	public int getMachineCacheSize() {
		return 0;
	}

}
