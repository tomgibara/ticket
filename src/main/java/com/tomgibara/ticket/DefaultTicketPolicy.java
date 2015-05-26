package com.tomgibara.ticket;

import java.io.Serializable;


/**
 * <p>
 * The policy which is applied to ticket factories by default.
 * <p>
 * This class can serve as a convenient base class for custom policies.
 *
 * @author Tom Gibara
 *
 */

public class DefaultTicketPolicy implements TicketPolicy, Serializable {

	private static final long serialVersionUID = 3304016125579836480L;

	/**
	 * In the current implementation the returned default value is 256, this may
	 * be revised in future.
	 *
	 * @return the default ticket character limit
	 */

	@Override
	public int getTicketCharLimit() {
		return 256;
	}

	/**
	 * In the current implementation the returned default value is 0. This has
	 * the effect of disabling machine caching and may be revised in future.
	 */

	@Override
	public int getMachineCacheSize() {
		return 0;
	}

}
