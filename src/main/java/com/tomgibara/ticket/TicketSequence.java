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
 * Generates sequence numbers for tickets. Sequence numbers need only be
 * distinct across individual timestamps, and should generally be kept small.
 * <p>
 * A minimal valid implementation is to maintain a sequence number counter that
 * is incremented on each repetition of a timestamp, and reset to zero on each
 * new timestamp observed.
 *
 * @author Tom Gibara
 */

public interface TicketSequence {

	/**
	 * The next sequence number to be assigned to a ticket. Note that method may
	 * be called concurrently by multiple threads.
	 *
	 * @param timestamp
	 *            a timestamp associated with a ticket
	 * @return a non-negative sequence number
	 * @throws TicketException
	 *             if a sequence number cannot be generated
	 */

	long nextSequenceNumber(long timestamp) throws TicketException;

	/**
	 * Whether the supplied timestamp has yet to be assigned a sequence number
	 *
	 * @param timestamp
	 *            a timestamp associated with a ticket
	 * @return true if the timestamp has never been assigned a sequence number,
	 *         false otherwise
	 */
	boolean isUnsequenced(long timestamp);

}
