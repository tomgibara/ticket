package com.tomgibara.ticket;

/**
 * Parameterizes some ticket factory operations. Note that the values returned
 * by this interface are not cached; the methods will be called every time the
 * value is needed. For this reason, care should be taken to ensure that an
 * efficient implementation is provided. In many cases, returning a constant
 * value will be sufficient.
 * <p>
 * Values are not cached for the specific reason that policies can then adjust
 * the values they return in response to runtime conditions.
 *
 * @author Tom Gibara
 *
 */

public interface TicketPolicy {

	/**
	 * Attempts to encode or decode tickets who's string length exceeds this
	 * value will fail with a {@link TicketException}.
	 *
	 * @return the limit in characters
	 */

	int getTicketCharLimit();

}
