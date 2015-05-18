package com.tomgibara.ticket;

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
