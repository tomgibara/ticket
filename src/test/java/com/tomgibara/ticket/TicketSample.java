package com.tomgibara.ticket;

import java.io.UnsupportedEncodingException;
import java.util.TimeZone;

import com.tomgibara.ticket.Ticket.Granularity;

import junit.framework.TestCase;

public class TicketSample extends TestCase {

	public void testBasic() throws UnsupportedEncodingException {

		/**
		 * Tickets are created via factories. The simplest way to create a
		 * factory and a ticket is this:
		 */

			TicketFactory<Void, Void> factory = TicketConfig.getDefault().newFactory();
			Ticket<Void, Void> ticket = factory.machine().ticket();

		/**
		 * To convert this ticket into ASCII for reporting to a user (or
		 * possibly another system) use the toString() method:
		 */

		out( ticket.toString() );

		/**
		 * This will produce output that looks something like
		 * <code>"55wwn-gd00z"</code>. The format of a ticket can be changed:
		 */

		factory.setFormat(new TicketFormat(true, 3, '.', false));

		/**
		 * In this case, the ticket string would have been
		 * <code>55W.WNG.D00</code>. Changing a factory's formatting, does not
		 * prevent it from decoding tickets it created with older formats:
		 */

		Ticket<Void, Void> decoded = factory.decodeTicket(ticket.toString());
		assertEquals(ticket, decoded); // they are equal

		/**
		 * Any new ticket will have a different string representation. This is
		 * because the tickets are always populated with a timestamp, and a
		 * sequence number to distinguish tickets that share the same timestamp.
		 * This information is available from the ticket.
		 */

		ticket.getTimestamp(); // machine epoch time
		ticket.getSequenceNumber(); // a non-negative number

		/**
		 * By default, the tickets will store a timestamp which is accurate to a
		 * second, relative to the start of 2015 UTC. The reason for this date
		 * offset is that no ticket could exist before that date (because the
		 * library did not exist) and it helps to produce more compact tickets.
		 * To change this the factory configuration can be used to specify an
		 * alternative specification:
		 */

		TicketSpec spec = TicketSpec.newDefaultBuilder()
				.setGranularity(Granularity.MILLISECOND)
				.setOriginYear(2016)
				.setTimeZone(TimeZone.getDefault())
				.setHashLength(32)
				.build();
		TicketConfig.getDefault().withSpecifications(spec).newFactory();

		/**
		 * The specification above will produce tickets with millisecond
		 * precision for dates after 2016 in the default time zone. It also adds
		 * something new, it specifies that a 32 bit hash should be included in
		 * tickets. Hashes are useful (though not always necessary) because they
		 * can reject the majority of invalid tickets. When constructing a
		 * factory from a configuration one may also supply a secret. This is
		 * incorporated into the hash and makes tickets much more difficult to
		 * forge:
		 */
		byte[] secret0 = "Secret Passphraze!".getBytes("ASCII");
		Ticket<Void, Void> hashedTicket =
				TicketConfig.getDefault()
				.withSpecifications(spec).newFactory(secret0)
				.machine().ticket();
		assertEquals(32, hashedTicket.getSpecification().getHashLength());

		/**
		 * Changing the specification used by a factory will prevent the factory
		 * from correctly interpreting tickets created with older
		 * specifications. So what happens if we want to change the
		 * specification used in an active system? We simply add new
		 * specifications to the configuration.
		 */

		TicketSpec newSpec = TicketSpec.newDefaultBuilder()
				// we change the granularity
				.setGranularity(Granularity.MILLISECOND)
				.setOriginYear(2016)
				.setTimeZone(TimeZone.getDefault())
				// and we change the hash length
				.setHashLength(50)
				.build();
		TicketFactory<Void, Void> laterFactory = TicketConfig.getDefault()
				.withSpecifications(spec, newSpec).newFactory(secret0);

		/**
		 * New tickets will always use the latest specification, but older
		 * tickets will still be decoded using the historic specifications.
		 */

		// the former ticket decodes okay
		laterFactory.decodeTicket( hashedTicket.toString() );
		// any new ticket has a change specification
		Ticket<Void, Void> hashedTicket2 = laterFactory.machine().ticket();
		assertEquals(50, hashedTicket2.getSpecification().getHashLength());

		/**
		 * If, as is the case above, fewer secrets are supplied than
		 * specifications, then all later specifications are assumed to share
		 * the same secret. If this is not the case, then multiple secrets can
		 * be supplied at the time at which the factory is constructed.
		 */

		byte[] secret1 = "New Seekret Passphraze!".getBytes("ASCII");
		TicketConfig.getDefault()
				.withSpecifications(spec, newSpec)
				.newFactory(secret0, secret1);

		/**
		 * In this way, the secret used to securely hash the ticket contents may
		 * changed without invalidating tickets hashed with older secrets.
		 */
	}

	private static void out(Object obj) {
		System.out.println(obj);
	}

}
