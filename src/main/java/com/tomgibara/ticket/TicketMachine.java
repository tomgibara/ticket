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

import java.util.Random;

import com.tomgibara.bits.BitVector;
import com.tomgibara.bits.BitVectorWriter;
import com.tomgibara.coding.CodedWriter;

/**
 * Creates new tickets on behalf of a {@link TicketFactory}. Each ticket machine
 * is dedicated to the creation of tickets for a single origin.
 *
 * @author Tom Gibara
 *
 * @param <R>
 *            the type of origin information recorded in tickets
 * @param <D>
 *            the type of data information recorded in tickets
 */

public class TicketMachine<R, D> {

	// statics

	private static long bytesToLong(byte[] bytes, int i) {
		return
				( (long)  bytes[i + 0]         << 56) |
				( (long) (bytes[i + 1] & 0xff) << 48) |
				( (long) (bytes[i + 2] & 0xff) << 40) |
				( (long) (bytes[i + 3] & 0xff) << 32) |
				( (long) (bytes[i + 4] & 0xff) << 24) |
				(        (bytes[i + 5] & 0xff) << 16) |
				(        (bytes[i + 6] & 0xff) <<  8) |
				(        (bytes[i + 7] & 0xff) <<  0);
	}

	private static long generateNonce(byte[] digest) {
		long seed = bytesToLong(digest, digest.length - 8);
		Random random = new Random(seed);
		int count = 16 + random.nextInt(16);
		int bits = random.nextInt();
		long bit = 1L << count;
		return bit | bits & bit - 1L;
	}

	// fields

	private final TicketFactory<R, D> factory;
	private final TicketBasis<R> basis;
	private final TicketSequence sequence;
	private final TicketSpec spec;

	private final boolean hasSecret;

	// constructors

	TicketMachine(TicketFactory<R, D> factory, TicketBasis<R> basis) {
		this.factory = factory;
		this.basis = basis;
		sequence = factory.sequences.getSequence(basis);
		if (sequence == null) throw new IllegalStateException("No sequence for basis: " + basis);
		spec = factory.specs[basis.specNumber];
		TicketConfig<R, D> config = factory.config;
		hasSecret = config.originAdapter.isSecretive() || config.dataAdapter.isSecretive();
	}

	// accessors

	/**
	 * The factory for which this machine creates tickets.
	 *
	 * @return the owning factory
	 */

	public TicketFactory<R, D> getFactory() {
		return factory;
	}

	/**
	 * The basis on which the tickets are created by this machine. This includes
	 * not only the origin with which the generated tickets will be marked, but
	 * also the specification number that is being used by this factory.
	 *
	 * @return the ticket basis
	 */

	public TicketBasis<R> getBasis() {
		return basis;
	}

	// methods

	/**
	 * Creates a new ticket with default data values. This is equivalent to
	 * supplying null data to {@link #ticketData(Object)} or no values to
	 * {@link #ticketDataValues(Object...)}.
	 *
	 * @return a new ticket
	 * @throws TicketException
	 *             if the sequence numbers have become exhausted
	 */

	public Ticket<R, D> ticket() throws TicketException {
		return ticketImpl( factory.config.dataAdapter.unadapt(null) );
	}

	/**
	 * Creates a ticket with the supplied data.
	 *
	 * @param data
	 *            the data associated with the ticket, or null
	 * @return a new ticket
	 * @throws TicketException
	 *             if the sequence numbers have become exhausted
	 */

	public Ticket<R, D> ticketData(D data) throws TicketException {
		return ticketImpl( factory.config.dataAdapter.unadapt(data) );
	}

	/**
	 * Creates a ticket with the supplied data.
	 *
	 * @param data
	 *            the data associated with the ticket, or null
	 * @return a new ticket
	 * @throws TicketException
	 *             if the sequence numbers have become exhausted
	 * @throws IllegalArgumentException
	 *             if too many values are supplied or their types do not match
	 *             those indicated by the ticket's data interface
	 */

	public Ticket<R, D> ticketDataValues(Object... dataValues) throws TicketException {
		if (dataValues == null) throw new IllegalArgumentException("null dataValues");
		return ticketImpl( factory.config.dataAdapter.defaultValues(dataValues) );
	}

	private Ticket<R, D> ticketImpl(Object... dataValues) throws TicketException {
		TicketAdapter<D> dataAdapter = factory.config.dataAdapter;
		D data = dataAdapter.adapt(dataValues);
		BitVectorWriter writer = new BitVectorWriter();
		CodedWriter w = new CodedWriter(writer, TicketFactory.CODING);
		int number = basis.specNumber;
		long timestamp = spec.timestamp();
		final long seq;
		try {
			seq = sequence.nextSequenceNumber(timestamp);
		} catch (RuntimeException e) {
			throw new TicketException("Failed to obtain sequence number for origin: " + basis, e);
		}
		if (seq < 0) throw new TicketException("Ticket sequence returned a negative number: " + seq);
		int length = 0;
		length += w.writePositiveInt(TicketFactory.VERSION);
		length += w.writePositiveInt(number);
		length += w.writePositiveLong(timestamp);
		length += w.writePositiveLong(seq);
		length += basis.openOriginBits.writeTo(writer);
		length += dataAdapter.write(w, false, dataValues);
		if (hasSecret) {
			// digest this prefix
			// Note: flushing not currently necessary when writing to BitVectors
			// but in case this changes in the future
			writer.flush();
			byte[] digest = factory.digest(number, writer.toImmutableBitVector().toByteArray());
			// xor extract bytes, digest with secret bits and write out
			// start by writing the secret fields into a bit vector
			BitVectorWriter sWriter = new BitVectorWriter();
			CodedWriter sW = new CodedWriter(sWriter, TicketFactory.CODING);
			factory.config.originAdapter.write(sW, true, basis.values);
			dataAdapter.write(sW, true, dataValues);
			// add a nonce between 16 and 32 bits to avoid deducing information from the secret length
			// we compute this from the 64 MSB bits which we reserve from the digest.
			sW.writePositiveLong( generateNonce(digest) );
			BitVector sBits = sWriter.toMutableBitVector();
			// measure the bit vector and write out the length
			int sLength = sBits.size();
			factory.checkSecretLength(sLength);
			length += w.writePositiveInt(sLength);
			// xor the digest with the bits and write to the ticket
			sBits.xorVector(BitVector.fromByteArray(digest, sLength));
			length += sBits.writeTo(writer);
		} else {
			// no encrypted bits
			length += w.writePositiveInt(0);
		}
		length += spec.writeHash(factory.digests[number], writer);
		int padding = 4 - (length + 4) % 5;
		length += writer.writeBooleans(false, padding);
		BitVector bits = writer.toImmutableBitVector();
		String string = factory.format.encode(bits, factory.config.ticketCharLimit);
		return new Ticket<R, D>(spec, bits, timestamp, seq, basis.origin, data, string);
	}

	// package scoped methods

	boolean isDisposable() {
		return sequence.isUnsequenced(spec.timestamp());
	}

}
