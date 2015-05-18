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
 * Instances of this class are thrown when a ticket cannot be decoded.
 *
 * @author Tom Gibara
 * @see TicketFactory#decodeTicket(String)
 */

public class TicketException extends RuntimeException {

	private static final long serialVersionUID = -504476966169351063L;

	TicketException() {
		super();
	}

	TicketException(String message, Throwable cause) {
		super(message, cause);
	}

	TicketException(String message) {
		super(message);
	}

	TicketException(Throwable cause) {
		super(cause);
	}

}
