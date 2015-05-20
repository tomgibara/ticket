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

import java.util.Arrays;

import com.tomgibara.bits.BitVector;
import com.tomgibara.bits.BitVectorWriter;
import com.tomgibara.coding.CodedWriter;

public class TicketMachine<R, D> {

	// fields

	private final TicketFactory<R, D> factory;
	private final TicketOrigin<R> origin;
	private final TicketSpec spec;

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
	}

	// accessors

	public TicketFactory<R, D> getFactory() {
		return factory;
	}

	public int getSpecNumber() {
		return origin.specNumber;
	}

	public R getOrigin() {
		return origin.origin;
	}

	// methods

	public boolean isDisposable() {
		synchronized (lock) {
			return seqNumber == 0 || spec.timestamp() > seqTimestamp;
		}
	}

	public Ticket<R, D> ticket() throws TicketException {
		return ticketData(null);
	}

	public Ticket<R, D> ticketDataValues(Object... dataValues) throws TicketException {
		return ticketData(factory.config.dataAdapter.defaultAndAdapt(dataValues));
	}

	public Ticket<R, D> ticketData(D data) throws TicketException {
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
		length += origin.originBits.writeTo(writer);
		TicketAdapter<D> adapter = factory.config.dataAdapter;
		length += adapter.write(w, false, data);
		if (adapter.isSecretive()) {
			// xor extract bytes, digest with secret bits and write out
			// start by writing the secret fields into a bit vector
			BitVectorWriter sWriter = new BitVectorWriter();
			CodedWriter sW = new CodedWriter(sWriter, TicketFactory.CODING);
			adapter.write(sW, true, data);
			//TODO make mutable getter available, at cost of 'killing' the writer
			BitVector sBits = sWriter.toBitVector().mutableCopy();
			// measure the bit vector and write out the length
			int sLength = sBits.size();
			if (sLength > TicketFactory.DIGEST_SIZE) throw new TicketException("secret data too large");
			length += w.writePositiveInt(sLength);
			// now digest this prefix
			// Note: flushing not currently necessary when writing to BitVectors
			// but in case this changes in the future
			writer.flush();
			byte[] digest = factory.digest(number, writer.toBitVector().toByteArray());
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
		BitVector bits = writer.toBitVector();
		String string = factory.format.encode(bits, factory.config.ticketCharLimit);
		return new Ticket<R, D>(spec, bits, timestamp, seq, origin.origin, data, string);
	}

}
