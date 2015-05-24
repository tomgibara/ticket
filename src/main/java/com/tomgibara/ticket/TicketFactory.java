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

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.tomgibara.bits.BitReader;
import com.tomgibara.bits.BitStreamException;
import com.tomgibara.bits.BitVector;
import com.tomgibara.bits.BitVectorWriter;
import com.tomgibara.coding.CodedReader;
import com.tomgibara.coding.CodedWriter;
import com.tomgibara.coding.EliasOmegaCoding;
import com.tomgibara.coding.ExtendedCoding;

/**
 * Provides {@link TicketMachine} instances to create tickets and a
 * {@link #decodeTicket(String)} method to decode them. Assuming monotonic
 * timing and reliable ticket sequence numbers, all of the tickets created by a
 * factory are guaranteed to be unique.
 * <p>
 * This class may be regarded as the functional entry point of this package;
 * applications will generally establish {@link TicketSpec},
 * {@link TicketConfig} and {@link TicketFormat} instances for the purpose of
 * creating a ticket factory that is then used to create and decode
 * {@link Ticket} objects.
 * <p>
 * Though ticket factories will frequently be dedicated to creating tickets for
 * a single origin, they may be used to create tickets from multiple origins.
 * For this reason, ticket creation is delegated to {@link TicketMachine}
 * instances which operate on behalf of the factory, with each machine dedicated
 * to creating tickets for a single origin.
 * <p>
 * Instances of this class are safe for concurrent access by multiple threads.
 *
 * @author Tom Gibara
 *
 * @param <R>
 *            the type of origin information recorded in tickets
 * @param <D>
 *            the type of data information recorded in tickets
 * @see TicketMachine#ticket()
 */

//NOTE Keccak is used for a simpler life without HMAC: http://keccak.noekeon.org/ - we simply hash and append
//NOTE it is a minor weakness of this design that the ticket must be parsed before the checksum can be validated
//TODO consider allowing null specs to indicate that version cannot be used?
//TODO consider adding 'self description' capability with a ticket inspection capability
public class TicketFactory<R, D> {

	// note, not configurable because we don't want to expose "bits" level abstractions
	// there is probably very little benefit in exposing this anyway
	static final ExtendedCoding CODING = EliasOmegaCoding.extended;

	// this is effectively the version of the ticket format
	// this will need to be increased if backward incompatible changes are made
	static final int VERSION = 0;

	// determines the maximum size in bits of any hash that this package can support
	static final int DIGEST_SIZE = 224;

	//TODO consider optimizing further
	// eg. same secrets get same digest, or no hash length means skip digest creation
	private static KeccakDigest[] createDigests(TicketSpec[] specs, byte[]... secrets) {
		KeccakDigest[] digests = new KeccakDigest[specs.length];
		KeccakDigest vanilla = new KeccakDigest(DIGEST_SIZE);
		KeccakDigest digest = vanilla;
		for (int i = 0; i < secrets.length; i++) {
			byte[] secret = secrets[i];
			if (secret == null || secret.length == 0) {
				digest = vanilla;
			} else {
				digest = new KeccakDigest(vanilla);
				digest.update(secret, 0, secret.length);
			}
			digests[i] = digest;
		}
		for (int i = secrets.length; i < digests.length; i++) {
			digests[i] = digest;
		}
		return digests;
	}

	final TicketConfig<R, D> config;
	final TicketSequences<R> sequences;
	final TicketSpec[] specs;
	final KeccakDigest[] digests;
	final int primarySpecIndex;
	final SecureRandom random;
	volatile TicketFormat format = TicketFormat.DEFAULT;

	private final Map<TicketBasis<R>, TicketMachine<R,D>> machines = new HashMap<TicketBasis<R>, TicketMachine<R,D>>();

	TicketFactory(TicketConfig<R,D> config, TicketSequences<R> sequences, byte[]... secrets) {
		this.config = config;
		this.sequences = sequences == null ? new Sequences() : sequences;
		List<TicketSpec> list = config.getSpecifications();
		specs = (TicketSpec[]) list.toArray(new TicketSpec[list.size()]);
		digests = createDigests(specs, secrets);
		primarySpecIndex = specs.length - 1;
		// we only need a random if we are creating secured ticket fields
		random = config.dataAdapter.isSecretive() ? new SecureRandom() : null;
	}

	// accessors

	/**
	 * The configuration with which the factory was created.
	 *
	 * @return the configuration, never null
	 */

	public TicketConfig<R, D> getConfig() {
		return config;
	}

	/**
	 * Specifies text formatting for the encoding of tickets into strings. The
	 * format used by a factory may be changed at any time and will subsequently
	 * be applied to all tickets created by the ticket machines of the factory.
	 *
	 * @param format
	 *            the format to use, not null
	 * @see Ticket#toString()
	 */

	public void setFormat(TicketFormat format) {
		if (format == null) throw new IllegalArgumentException("null format");
		this.format = format;
	}

	/**
	 * The format currently applied by the factory to the string encoding of its
	 * tickets. If the {@link #setFormat(TicketFormat)} method has not
	 * previously been called, this will be {@link TicketFormat#DEFAULT}
	 *
	 * @return the format used this ticket factory
	 */

	public TicketFormat getFormat() {
		return format;
	}

	// methods

	/**
	 * A ticket machine for the default origin. For the void origin type, this
	 * is the only ticket machine. For factories which operate with an interface
	 * defined origin type, this method will assume default values for all interface
	 * methods.
	 *
	 * @return a ticket machine for the default origin.
	 */

	public TicketMachine<R, D> machine() {
		return machineImpl( config.originAdapter.unadapt(null) );
	}

	/**
	 * A ticket machine for the specified origin. The supplied origin must be
	 * null or implement the origin type interface. In the case of a null
	 * origin, this method will assume default values for all interface methods.
	 *
	 * @param origin
	 *            the origin to be declared for the created tickets
	 * @return a ticket machine for the specified origin.
	 * @see TicketConfig#withOriginType(Class)
	 */

	public TicketMachine<R, D> machineForOrigin(R origin) {
		return machineImpl( config.originAdapter.unadapt(origin) );
	}

	/**
	 * A ticket machine for the origin as specified by the supplied values. This
	 * method provides a convenient way to produce a ticket machine without
	 * first creating an implementation of the origin type interface. Each value
	 * supplied is assigned to the correspondingly indexed accessor on the
	 * origin type interface (as per the {@link TicketField} annotation); any
	 * null or absent value is assigned a default.
	 *
	 * @param origin
	 *            the origin to be declared for the created tickets
	 * @return a ticket machine for the specified origin.
	 * @see TicketField
	 */

	public TicketMachine<R, D> machineForOriginValues(Object... originValues) {
		if (originValues == null) throw new IllegalArgumentException("null originValues");
		return machineImpl( config.originAdapter.defaultValues( originValues ) );
	}

	/**
	 * Decodes a ticket that was previously encoded using the
	 * {@link Ticket#toString()} method. To be valid ticket for this factory,
	 * the encoded ticket must have been created with a compatible factory.
	 *
	 * @param str
	 *            the string to be decoded
	 * @return a ticket
	 * @throws IllegalArgumentException
	 *             if the supplied string is null or empty
	 * @throws TicketException
	 *             if the string did not specify a valid ticket
	 */

	public Ticket<R, D> decodeTicket(String str) throws TicketException {
		// validate parameters
		if (str == null) throw new IllegalArgumentException("null str");
		int length = str.length();
		if (length == 0) throw new IllegalArgumentException("empty str");
		// decode string to bits
		BitVector bits = format.decode(str, config.ticketCharLimit);
		int size = bits.size();
		// read ticket data
		TicketSpec spec;
		long timestamp;
		int seq;
		R origin;
		D data;
		try {
			BitReader reader = bits.openReader();
			CodedReader r = new CodedReader(reader, CODING);
			int version = r.readPositiveInt();
			if (version != VERSION) throw new TicketException("Ticket version does not match version supported by factory.");
			int number = r.readPositiveInt();
			if (number > primarySpecIndex) throw new TicketException("Unsupported ticket specification.");
			spec = specs[number];
			timestamp = r.readPositiveLong();
			seq = r.readPositiveInt();
			TicketAdapter<R> originAdapter = config.originAdapter;
			TicketAdapter<D> dataAdapter = config.dataAdapter;
			Object[] originValues = originAdapter.unadapt(null);
			Object[] dataValues = dataAdapter.unadapt(null);
			originAdapter.read(r, false, originValues);
			dataAdapter.read(r, false, dataValues);
			int sPosition = (int) reader.getPosition();
			int sLength = r.readPositiveInt();
			if (sLength > 0) {
				// digest the prefix
				BitVector digestBits = bits.rangeView(size - sPosition, size);
				byte[] digest = digest(number, digestBits.toByteArray());
				// retrieve the secure bits
				BitVector sBits = new BitVector(sLength);
				sBits.readFrom(reader);
				// xor the digest with the secure bits and read
				checkSecretLength(sLength);
				sBits.xorVector(BitVector.fromByteArray(digest, sLength));
				BitReader sReader = sBits.openReader();
				CodedReader sR = new CodedReader(sReader, CODING);
				originAdapter.read(sR, true, originValues);
				dataAdapter.read(sR, true, dataValues);
				sR.readPositiveLong(); // read the nonce
				// sBits should be exhausted
				if ((int) sReader.getPosition() != sLength) {
					throw new TicketException("Extra secure bits");
				}
			}
			origin = originAdapter.adapt(originValues);
			data = dataAdapter.adapt(dataValues);
			// check for valid hash
			int position = (int) reader.getPosition();
			BitVector expectedHash = spec.hash(digests[number], bits.rangeView(size - position, size));
			int hashSize = expectedHash.size();
			if (hashSize > 0) {
				BitVector actualHash = new BitVector(hashSize);
				actualHash.readFrom(reader);
				if (!actualHash.equals(expectedHash)) {
					throw new TicketException("Ticket hash invalid");
				}
			}
			// check for valid padding
			position = (int) reader.getPosition();
			if (position - size > 4) throw new TicketException("Ticket contains superfluous bits.");
			while (position < size) {
				if (reader.readBoolean()) throw new TicketException("Ticket has non-zero padding bit.");
				position ++;
			}
		} catch (BitStreamException e) {
			throw new TicketException("Invalid ticket bits", e);
		}
		return new Ticket<R, D>(spec, bits, timestamp, seq, origin, data, str);
	}

	// package methods

	byte[] digest(int specNumber, byte[] bytes) {
		KeccakDigest digest = new KeccakDigest(digests[specNumber]);
		digest.update(bytes, 0, bytes.length);
		byte[] out = new byte[digest.getDigestSize()];
		digest.doFinal(out, 0);
		return out;
	}

	void checkSecretLength(int sLength) {
		if (sLength > TicketFactory.DIGEST_SIZE - 64) throw new TicketException("secret data too large");
	}

	// private helper methods

	private TicketMachine<R, D> machineImpl(Object... values) {
		TicketBasis<R> basis = newBasis(primarySpecIndex, values);
		TicketMachine<R, D> machine;
		synchronized (machines) {
			machine = machines.get(basis);
			for (Iterator<TicketMachine<R,D>> i = machines.values().iterator(); i.hasNext(); ) {
				TicketMachine<R, D> existing = i.next();
				if (existing == machine) continue;
				if (!existing.isDisposable()) continue;
				i.remove();
			}
			if (machine == null) {
				machine = new TicketMachine<R, D>(this, basis);
				machines.put(basis, machine);
			}
		}
		return new TicketMachine<R, D>(this, basis);
	}

	private TicketBasis<R> newBasis(int specNumber, Object... values) {
		R origin = config.originAdapter.adapt(values);
		BitVector openBits = originBits(false, values);
		BitVector secretBits = originBits(true, values);
		return new TicketBasis<R>(specNumber, openBits, secretBits, origin, values);
	}
	
	private BitVector originBits(boolean secret, Object... values) {
		BitVectorWriter writer = new BitVectorWriter();
		CodedWriter w = new CodedWriter(writer, TicketFactory.CODING);
		config.originAdapter.write(w, false, values);
		return writer.toImmutableBitVector();
	}

	// inner classes

	private class Sequences implements TicketSequences<R> {

		@Override
		public TicketSequence getSequence(TicketBasis<R> origin) {
			return new Sequence();
		}

	}

	private static class Sequence implements TicketSequence {

		// the timestamp for which the number sequence is increasing
		private long timestamp = -1L;
		// set to the next sequence number
		private long number = 0L;

		@Override
		public synchronized long nextSequenceNumber(long timestamp) {
			if (this.timestamp > timestamp) {
				number = 0;
				this.timestamp = timestamp;
			} else if (number == Long.MIN_VALUE) {
				throw new TicketException("Sequence numbers exhausted");
			}
			return number ++;
		}

		@Override
		public boolean isUnsequenced(long timestamp) {
			return number == 0 || timestamp > this.timestamp;
		}

	}
}
