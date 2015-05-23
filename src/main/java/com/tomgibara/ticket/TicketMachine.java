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
	private final TicketOrigin<R> origin;
	private final TicketSpec spec;

	private final boolean hasSecret;
	// would be nice to use an AtomicInteger
	// but we need to operate over two values synchronously
	private final Object lock = new Object();
	private long seqTimestamp = 0L;
	// set to the next sequence number
	//TODO consider making long?
	private int seqNumber = 0;

	// constructors

	TicketMachine(TicketFactory<R, D> factory, TicketOrigin<R> origin) {
		this.factory = factory;
		this.origin = origin;
		spec = factory.specs[origin.specNumber];
		TicketConfig<R, D> config = factory.config;
		hasSecret = config.originAdapter.isSecretive() || config.dataAdapter.isSecretive();
	}

	// accessors

	public TicketFactory<R, D> getFactory() {
		return factory;
	}
	
	public TicketOrigin<R> getOrigin() {
		return origin;
	}

	// methods

	public Ticket<R, D> ticket() throws TicketException {
		return ticketImpl( factory.config.dataAdapter.unadapt(null) );
	}

	public Ticket<R, D> ticketData(D data) throws TicketException {
		return ticketImpl( factory.config.dataAdapter.unadapt(data) );
	}

	public Ticket<R, D> ticketDataValues(Object... dataValues) throws TicketException {
		if (dataValues == null) throw new IllegalArgumentException("null dataValues");
		return ticketImpl( factory.config.dataAdapter.defaultValues(dataValues) );
	}

	private Ticket<R, D> ticketImpl(Object... dataValues) throws TicketException {
		TicketAdapter<D> dataAdapter = factory.config.dataAdapter;
		D data = dataAdapter.adapt(dataValues);
		BitVectorWriter writer = new BitVectorWriter();
		CodedWriter w = new CodedWriter(writer, TicketFactory.CODING);
		int number = origin.specNumber;
		long timestamp = spec.timestamp();
		int seq;
		synchronized (lock) {
			if (timestamp > seqTimestamp) {
				seqNumber = 0;
				seqTimestamp = timestamp;
			}
			seq = seqNumber ++;
		}
		if (seq == -1) throw new TicketException("Sequence numbers exhausted");
		int length = 0;
		length += w.writePositiveInt(TicketFactory.VERSION);
		length += w.writePositiveInt(number);
		length += w.writePositiveLong(timestamp);
		length += w.writePositiveInt(seq);
		length += origin.openOriginBits.writeTo(writer);
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
			factory.config.originAdapter.write(sW, true, origin.values);
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
		return new Ticket<R, D>(spec, bits, timestamp, seq, origin.origin, data, string);
	}

	// package scoped methods

	boolean isDisposable() {
		synchronized (lock) {
			return seqNumber == 0 || spec.timestamp() > seqTimestamp;
		}
	}

}
