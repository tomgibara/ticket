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
		return ticketData(factory.config.dataAdapter.adapt(dataValues));
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
		length += factory.config.dataAdapter.write(w, data);
		length += spec.writeHash(factory.digests[number], writer);
		int padding = 4 - (length + 4) % 5;
		length += writer.writeBooleans(false, padding);
		BitVector bits = writer.toBitVector();
		String string = factory.format.encode(bits);
		return new Ticket<R, D>(spec, bits, timestamp, seq, origin.origin, data, string);
	}

}
