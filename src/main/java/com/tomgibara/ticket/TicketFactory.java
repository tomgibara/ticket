package com.tomgibara.ticket;

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

//NOTE Keccak is used for a simpler life without HMAC: http://keccak.noekeon.org/ - we simply hash and append
//NOTE it is a minor weakness of this design that the ticket must be parsed before the checksum can be validated
//TODO support persistence of serial numbers by passing in interface during config
//TODO consider allowing null specs to indicate that version cannot be used?
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
	final TicketSpec[] specs;
	final KeccakDigest[] digests;
	final int primarySpecIndex;
	volatile TicketFormat format = TicketFormat.DEFAULT;

	private final Map<TicketOrigin<R>, TicketMachine<R,D>> machines = new HashMap<TicketOrigin<R>, TicketMachine<R,D>>();

	TicketFactory(TicketConfig<R,D> config, byte[]... secrets) {
		this.config = config;
		List<TicketSpec> list = config.getSpecifications();
		specs = (TicketSpec[]) list.toArray(new TicketSpec[list.size()]);
		digests = createDigests(specs, secrets);
		primarySpecIndex = specs.length - 1;
	}

	// accessors

	public TicketConfig<R, D> getConfig() {
		return config;
	}

	public void setFormat(TicketFormat format) {
		if (format == null) throw new IllegalArgumentException("null format");
		this.format = format;
	}

	public TicketFormat getFormat() {
		return format;
	}

	// methods

	public TicketMachine<R, D> machine() {
		return machineImpl(null);
	}

	public TicketMachine<R, D> machineForOrigin(R origin) {
		return machineImpl(origin);
	}

	public TicketMachine<R, D> machineForOriginValues(Object... originValues) {
		if (originValues == null) throw new IllegalArgumentException("null originValues");
		R origin = config.originAdapter.adapt(originValues);
		return machineImpl(origin);
	}

	public Ticket<R, D> decodeTicket(String str) {
		// validate parameters
		if (str == null) throw new IllegalArgumentException("null str");
		int length = str.length();
		if (length == 0) throw new IllegalArgumentException("empty str");
		// decode string to bits
		BitVector bits = format.decode(str);
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
			origin = config.originAdapter.read(r);
			data = config.dataAdapter.read(r);
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

	// private helper methods

	private TicketMachine<R, D> machineImpl(R origin) {
		TicketOrigin<R> key = newOrigin(primarySpecIndex, origin);
		TicketMachine<R, D> machine;
		synchronized (machines) {
			machine = machines.get(key);
			for (Iterator<TicketMachine<R,D>> i = machines.values().iterator(); i.hasNext(); ) {
				TicketMachine<R, D> existing = i.next();
				if (existing == machine) continue;
				if (!existing.isDisposable()) continue;
				i.remove();
			}
			if (machine == null) {
				machine = new TicketMachine<R, D>(this, key);
				machines.put(key, machine);
			}
		}
		return new TicketMachine<R, D>(this, key);
	}

	private TicketOrigin<R> newOrigin(int specNumber, R origin) {
		BitVectorWriter writer = new BitVectorWriter();
		CodedWriter w = new CodedWriter(writer, TicketFactory.CODING);
		config.originAdapter.write(w, origin);
		return new TicketOrigin<R>(specNumber, writer.toBitVector(), origin);
	}

}
