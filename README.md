# ticket
a ticket generation and validation library for Java

Features
--------

 * Well designed API.
 * A compact human-friendly ASCII format for tickets. 
 * Encode application specific data within tickets.
 * Mark specific ticket data fields for encryption.
 * Tickets may be hashed to catch errors and detect forgeries.
 * Decode old tickets while issuing tickets with newer formats.
 * Pluggable ticket sequencing for persistence across restarts.

API Walkthrough
---------------

Tickets are created via factories. The simplest way to create a
factory and a ticket is this:

```java
TicketFactory<Void, Void> factory = TicketConfig.getDefault().newFactory();
Ticket<Void, Void> ticket = factory.machine().ticket();
```

To convert this ticket into ASCII for reporting to a user (or possibly
another system) use the toString() method:

```java
out( ticket.toString() );
```

This will produce output that looks something like
<code>"55wwn-gd00z"</code>. The format of a ticket can be changed:

```java
factory.setFormat(new TicketFormat(true, 3, '.', false));
```

In this case, the ticket string would have been
<code>55W.WNG.D00</code>. Changing a factory's formatting, does not
prevent it from decoding tickets it created with older formats:

```java
Ticket<Void, Void> decoded = factory.decodeTicket(ticket.toString());
assertEquals(ticket, decoded); // they are equal
```

Any new ticket will have a different string representation. This is
because the tickets are always populated with a timestamp, and a
sequence number to distinguish tickets that share the same timestamp.
This information is available from the ticket.

```java
ticket.getTimestamp(); // machine epoch time
ticket.getSequenceNumber(); // a non-negative number
```

By default, the tickets will store a timestamp which is accurate to a
second, relative to the start of 2015 UTC. The reason for this date
offset is that no ticket could exist before that date (because the
library did not exist) and it helps to produce more compact tickets.
To change this the factory configuration can be used to specify an
alternative specification:

```java
TicketSpec spec = TicketSpec.newDefaultBuilder()
		.setGranularity(Granularity.MILLISECOND)
		.setOriginYear(2016)
		.setTimeZone(TimeZone.getDefault())
		.setHashLength(32)
		.build();
TicketConfig.getDefault().withSpecifications(spec).newFactory();
```

The specification above will produce tickets with millisecond
precision for dates after 2016 in the default time zone. It also adds
something new, it specifies that a 32 bit hash should be included in
tickets. Hashes are useful (though not always necessary) because they
can reject the majority of invalid tickets. When constructing a
factory from a configuration one may also supply a secret. This is
incorporated into the hash and makes tickets much more difficult to
forge:

```java
byte[] secret0 = "Secret Passphraze!".getBytes("ASCII");
Ticket<Void, Void> hashedTicket =
		TicketConfig.getDefault()
		.withSpecifications(spec).newFactory(secret0)
		.machine().ticket();
assertEquals(32, hashedTicket.getSpecification().getHashLength());
```

Changing the specification used by a factory will prevent the factory
from correctly interpreting tickets created with older specifications.
So what happens if we want to change the specification used in an
active system? We simply add new specifications to the configuration.

```java
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
```

New tickets will always use the latest specification, but older
tickets will still be decoded using the historic specifications.

```java
// the former ticket decodes okay
laterFactory.decodeTicket( hashedTicket.toString() );
// any new ticket has a change specification
Ticket<Void, Void> hashedTicket2 = laterFactory.machine().ticket();
assertEquals(50, hashedTicket2.getSpecification().getHashLength());
```

If, as is the case above, fewer secrets are supplied than
specifications, then all later specifications are assumed to share the
same secret. If this is not the case, then multiple secrets can be
supplied at the time at which the factory is constructed.

```java
byte[] secret1 = "New Seekret Passphraze!".getBytes("ASCII");
TicketConfig.getDefault()
		.withSpecifications(spec, newSpec)
		.newFactory(secret0, secret1);
```

In this way, the secret used to securely hash the ticket contents may
changed without invalidating tickets hashed with older secrets.

**There are serveral other useful and important elements of the API
that are not included in this walkthrough, these include:**

 * **`TicketSequences`** for allocating sequence numbers
 * **`TicketPolicy`** for specifying internal factory limits
 * Ticket origins that store information about the source of a ticket
 * Ticket data that encodes specific information inside a ticket
 * Secret ticket data which can be encrypted inside a ticket

Usage
-----

The ticket library is available from the Maven central repository:

> Group ID:    `com.tomgibara.ticket`
> Artifact ID: `ticket`
> Version:     `1.0.0`

The Maven dependency being:

    <dependency>
      <groupId>com.tomgibara.ticket</groupId>
      <artifactId>ticket</artifactId>
      <version>1.0.0</version>
    </dependency>
