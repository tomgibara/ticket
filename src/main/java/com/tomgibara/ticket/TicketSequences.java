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

/**
 * <p>
 * Provides sequences for ticket origins. A sequence must be returned for each
 * origin. Sequences may be shared across origins, but in general, it is
 * expected that each origin will have its own sequence.
 * <p>
 * The intended role of this interface is to provide a mechanism by which
 * applications can persist ticket sequence numbers. Without this, it is
 * possible that a factory in a restarted application might reassign the same
 * sequence number to the same timestamp, especially when low-resolution
 * timestamps are used.
 *
 * @author Tom Gibara
 *
 * @param <R>
 *            the type of ticket origin
 * @see TicketConfig#newFactory(TicketSequences, byte[]...)
 */

public interface TicketSequences<R> {

	/**
	 * <p>
	 * Provides a numbering sequence for ticket timestamps from a specified
	 * origin.
	 * <p>
	 * If an implementation is prevented from returning a sequence due to
	 * resource constraints or other circumstances outside of the
	 * implementations's control, null may be returned, or a
	 * <code>RuntimeException</code> may be raised. But in general,
	 * implementations of this interface should make a best effort to return a
	 * valid sequence for any supplied origin.
	 * <p>
	 * Note that the origins supplied to this method provide
	 * {@link TicketBasis#equals(Object)} and {@link TicketBasis#hashCode()}
	 * implementations and a {@link TicketBasis#toString()} representation that
	 * can serve as a unique key for the origin.
	 *
	 * @param origin
	 *            the origin for which a sequence is required
	 * @return a ticket numbering sequence
	 */

	//NOTE exceptions from this method are permitted to bubble-up when obtaining machines
	TicketSequence getSequence(TicketBasis<R> origin);

}
