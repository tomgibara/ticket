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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import com.tomgibara.ticket.Ticket.Granularity;

public class TicketFactoryTest extends TestCase {

	public void testVanilla() {
		// create a factory for producing tickets
		TicketFactory<Void, Void> factory = TicketConfig.getDefault().newFactory();

		// create a new ticket
		Ticket<Void, Void> ticket = factory.machine().ticket();

		// report the ticket as a string to the user...
		String string = ticket.toString();

		// eg. "54dww-8a52t-6bb5d-12h5c-27bwp-gw0zz"

		// later... receive the string and verify it
		Ticket<Void, Void> ticket2 = factory.decodeTicket(string);

		// all ticket information is preserved
		assertEquals(ticket, ticket2);
	}

	public void testUniqueness() {
		TicketSpec spec = TicketSpec.newDefaultBuilder()
				.setGranularity(Granularity.MILLISECOND)
				.build();
		TicketFactory<Void, Void> factory = TicketConfig.getDefault()
				.withSpecifications(spec)
				.newFactory();
		TicketMachine<Void, Void> machine = factory.machine();
		long finish = 50 + System.currentTimeMillis();
		Set<Ticket<Void,Void>> tickets = new HashSet<Ticket<Void,Void>>();
		Set<String> strings = new HashSet<String>();
		do {
			for (int i = 0; i < 1000; i++) {
				Ticket<Void, Void> ticket = machine.ticket();
				if (!tickets.add(ticket)) fail("Duplicate ticket " + ticket);
				if (!strings.add(ticket.toString())) fail("Duplicate ticket " + ticket);
			}
		} while (System.currentTimeMillis() < finish);
	}

	@SuppressWarnings("serial")
	static class ShortPolicy extends DefaultTicketPolicy {

		@Override
		public int getTicketCharLimit() {
			return 5;
		}

	}

	public void testLengthLimit() {
		TicketFactory<Void, Void> longFactory = TicketConfig.getDefault().newFactory();
		TicketFactory<Void, Void> shortFactory = TicketConfig.getDefault().newFactory();
		shortFactory.setPolicy(new ShortPolicy());

		try {
			Ticket<Void, Void> ticket = longFactory.machine().ticket();
			shortFactory.decodeTicket(ticket.toString());
			fail();
		} catch (TicketException e) {
			// expected
		}

		try {
			shortFactory.machine().ticket();
			fail();
		} catch (TicketException e) {
			// expected
		}
	}

	interface LongOrigin {

		@TicketField(0)
		long getId();

	}

	public void testManyOrigins() throws InterruptedException {
		TicketSpec spec = TicketSpec.newDefaultBuilder().setGranularity(Granularity.MILLISECOND).build();
		TicketFactory<LongOrigin, Void> factory = TicketConfig.getDefault()
				.withOriginType(LongOrigin.class)
				.withSpecifications(spec)
				.newFactory();

		Set<String> set = new HashSet<String>();
		for (int i = 0; i < 100000; i++) {
			TicketMachine<LongOrigin, Void> machine = factory.machineForOriginValues((long) i);
			TicketBasis<LongOrigin> origin = machine.getBasis();
			assertTrue(set.add(origin.toString()));
			if ((i % 10000) == 0) Thread.sleep(5);
			machine.ticket();
		}
	}

	public void testCanonicalMachine() {
		TicketFactory<Void, Void> factory = TicketConfig.getDefault().newFactory();
		TicketMachine<Void, Void> machine1 = factory.machine();
		TicketMachine<Void, Void> machine2 = factory.machine();
		assertSame(machine1, machine2);
	}

	interface SessionData {
		@TicketField(0)
		long getSessionId();
	}

	// small possibility of failure if requests fall either side of an hour
	public void testHash() throws Exception {
		TicketFactory<LongOrigin, SessionData> factory1 = hashingFactory("SECRET".getBytes("ASCII"), 32);
		TicketFactory<LongOrigin, SessionData> factory2 = hashingFactory("SEKRET".getBytes("ASCII"), 32);
		TicketFactory<LongOrigin, SessionData> factory3 = hashingFactory("SECRET".getBytes("ASCII"), 31);
		Ticket<LongOrigin, SessionData> ticket = factory1.machineForOriginValues(1L).ticketDataValues(7L);
		// check valid hash
		Ticket<LongOrigin, SessionData> result1 = factory1.decodeTicket(ticket.toString());
		assertEquals(ticket, result1);
		// check invalid - changed secret
		try {
			factory2.decodeTicket(ticket.toString());
			fail();
		} catch (TicketException e) {
			// expected
		}
		// check invalid - changed ticket
		try {
			String str = ticket.toString();
			String modified = str.substring(0, 2) + "5" + str.substring(3);
			factory1.decodeTicket(modified);
			fail();
		} catch (TicketException e) {
			// expected
		}
		// check invalid changed hash length
		try {
			factory3.decodeTicket(ticket.toString());
			fail();
		} catch (TicketException e) {
			// expected
		}
	}

	private TicketFactory<LongOrigin, SessionData> hashingFactory(byte[] secret, int length) {
		TicketSpec spec = TicketSpec.newDefaultBuilder().setGranularity(Granularity.HOUR).setHashLength(length).build();
		return TicketConfig.getDefault()
				.withOriginType(LongOrigin.class)
				.withDataType(SessionData.class)
				.withSpecifications(spec)
				.newFactory(secret);
	}

	// empty enums not permitted
	enum BadEnum {

	}

	interface BadEnumData {

		@TicketField(0)
		BadEnum getBad();

	}

	public void testBadEnum() {
		try {
			TicketConfig.getDefault()
					.withDataType(BadEnumData.class)
					.newFactory();
			fail(); // bad enum data should be rejected
		} catch (IllegalArgumentException e) {
			//expected
		}
	}

	enum BasicEnum {
		A, B, C;
	}

	interface EnumArrayOrigin {

		@TicketField(0)
		BasicEnum[] getBasic();
	}

	public void testEnumArray() {
		TicketFactory<EnumArrayOrigin, Void> factory = TicketConfig.getDefault()
				.withOriginType(EnumArrayOrigin.class)
				.newFactory();
		assertEquals(0, factory.machineForOriginValues().ticket().getOrigin().getBasic().length);
		checkArray(factory, new BasicEnum[0]);
		checkArray(factory, new BasicEnum[] { BasicEnum.A });
		checkArray(factory, new BasicEnum[] { BasicEnum.A, BasicEnum.B });
	}

	private void checkArray(TicketFactory<EnumArrayOrigin, Void> factory, BasicEnum[] arr) {
		BasicEnum[] res = factory.machineForOriginValues(new Object[] {arr}).ticket().getOrigin().getBasic();
		assertTrue(Arrays.equals(arr, res));
	}

	enum Env {
		INTEGRATION,
		TEST,
		PRODUCTION
	}

	interface MyOrigin {

		@TicketField(0)
		String getSite();

		@TicketField(1)
		int getNode();

		@TicketField(2)
		Env getEnv();

	}

	class MyOriginImpl implements MyOrigin {

		private final String site;
		private final int node;
		private final Env env;

		public MyOriginImpl(String site, int node, Env env) {
			this.site = site;
			this.node = node;
			this.env = env;
		}

		@Override
		public String getSite() {
			return site;
		}

		@Override
		public int getNode() {
			return node;
		}

		@Override
		public Env getEnv() {
			return env;
		}
	}

	interface MyData {

		@TicketField(0)
		long getSessionId();

		@TicketField(1)
		boolean isAuthenticated();

		@TicketField(2)
		String accountNumber();
	}

	class MyDataImpl implements MyData {

		private final long sessionId;
		private final boolean authenticated;

		MyDataImpl(long sessionsId, boolean authenticated) {
			this.sessionId = sessionsId;
			this.authenticated = authenticated;
		}

		@Override
		public long getSessionId() {
			return sessionId;
		}

		@Override
		public boolean isAuthenticated() {
			return authenticated;
		}

		@Override
		public String accountNumber() {
			return null;
		}

	}

	public void testCustomFactory() {
		TicketSpec spec = TicketSpec.newDefaultBuilder().setGranularity(Granularity.MINUTE).build();
		TicketFactory<MyOrigin, MyData> factory = TicketConfig.getDefault()
				.withOriginType(MyOrigin.class)
				.withDataType(MyData.class)
				.withSpecifications(spec).newFactory();

		TicketMachine<MyOrigin, MyData> machine = factory.machineForOrigin(new MyOriginImpl("EXA", 0, Env.TEST));
		Ticket<MyOrigin, MyData> ticket0 = machine.ticketData(new MyDataImpl(2394872349L, true));
		Ticket<MyOrigin, MyData> ticket1 = factory.decodeTicket(ticket0.toString());
		assertEquals("EXA", ticket1.getOrigin().getSite());
		assertEquals(0, ticket1.getOrigin().getNode());
		assertEquals(Env.TEST, ticket1.getOrigin().getEnv());
		assertEquals(2394872349L, ticket1.getData().getSessionId());
		assertEquals(true, ticket1.getData().isAuthenticated());
		assertEquals("", ticket1.getData().accountNumber());

		TicketMachine<MyOrigin, MyData> machine2 = factory.machineForOriginValues("EXA", 0, Env.TEST);
		Ticket<MyOrigin, MyData> ticket2 = machine2.ticketDataValues(2394872349L);
		assertEquals(ticket1.getOrigin(), ticket2.getOrigin());
		assertEquals(ticket1.getData(), ticket2.getData());

		TicketMachine<MyOrigin, MyData> machine3 = factory.machineForOriginValues(null, 0, null);
		Ticket<MyOrigin, MyData> ticket3 = machine3.ticketDataValues(2394872349L);
		assertEquals("", ticket3.getOrigin().getSite());
		assertEquals(0, ticket3.getOrigin().getNode());
		assertEquals(Env.INTEGRATION, ticket3.getOrigin().getEnv());
	}

	interface MySecretData {

		@TicketField(0)
		long getOpen();

		@TicketField(value=1, secret=true)
		long getSecret();

	}

	public void testSecretData() {
		TicketConfig<LongOrigin, MySecretData> config = TicketConfig.getDefault()
				.withOriginType(LongOrigin.class)
				.withDataType(MySecretData.class)
				.withSpecifications();

		TicketFactory<LongOrigin, MySecretData> good = config.newFactory(new byte[] {1});
		Ticket<LongOrigin, MySecretData> ticket = good.machineForOriginValues(213L).ticketDataValues(432L, 24380L);

		String str = ticket.toString();
		Ticket<LongOrigin, MySecretData> result = good.decodeTicket(str);
		assertEquals(ticket, result);

		TicketFactory<LongOrigin, MySecretData> bad1 = config.newFactory(new byte[] {2});
		try {
			Ticket<LongOrigin, MySecretData> badResult = bad1.decodeTicket(str);
			assertFalse(ticket.equals(badResult));
		} catch (TicketException e) {
			//expected since corrupted data may not parse
		}
	}

	interface MySecretOrigin {

		@TicketField(0)
		long getOpen();

		@TicketField(value=1, secret=true)
		long getSecret();

	}

	public void testSecretOrigin() {
		TicketConfig<MySecretOrigin, Void> config = TicketConfig.getDefault()
				.withOriginType(MySecretOrigin.class)
				.withSpecifications();

		TicketFactory<MySecretOrigin, Void> good = config.newFactory(new byte[] {1});
		TicketMachine<MySecretOrigin, Void> machine = good.machineForOriginValues(432L, 24380L);
		Ticket<MySecretOrigin, Void> ticket = machine.ticket();

		String str = ticket.toString();
		Ticket<MySecretOrigin, Void> result = good.decodeTicket(str);
		assertEquals(ticket, result);

		String originId = machine.getBasis().toString();
		assertEquals(originId, machine.getBasis().toString());
		String originId2 = good.machineForOriginValues(431L, 24381L).getBasis().toString();
		assertFalse(originId.equals( originId2 ));

		TicketFactory<MySecretOrigin, Void> bad1 = config.newFactory(new byte[] {2});
		try {
			Ticket<MySecretOrigin, Void> badResult = bad1.decodeTicket(str);
			assertFalse(ticket.equals(badResult));
		} catch (TicketException e) {
			//expected since corrupted data may not parse
		}
	}

	public void testSecret() {
		TicketConfig<MySecretOrigin, MySecretData> config = TicketConfig.getDefault()
				.withOriginType(MySecretOrigin.class)
				.withDataType(MySecretData.class)
				.withSpecifications();

		TicketFactory<MySecretOrigin, MySecretData> good = config.newFactory(new byte[] {1});
		TicketMachine<MySecretOrigin, MySecretData> machine = good.machineForOriginValues(432L, 24380L);
		Ticket<MySecretOrigin, MySecretData> ticket = machine.ticketDataValues(80L, 1000L);

		String str = ticket.toString();
		Ticket<MySecretOrigin, MySecretData> result = good.decodeTicket(str);
		assertEquals(ticket, result);
	}

	public static class TestSequences implements TicketSequences<Void> {
		private Map<String, TestSequence> sequences = new HashMap<String, TicketFactoryTest.TestSequence>();
		@Override
		public synchronized TicketSequence getSequence(TicketBasis<Void> origin) {
			String key = origin.toString();
			TestSequence sequence = sequences.get(key);
			if (sequence == null) {
				sequence = new TestSequence();
				sequences.put(key, sequence);
			}
			return sequence;
		}
	}

	public static class TestSequence implements TicketSequence {

		private long timestamp = -1L;
		private long number = 0L;

		@Override
		public long nextSequenceNumber(long timestamp) throws TicketException {
			if (this.timestamp > timestamp) {
				number = 0;
				this.timestamp = timestamp;
			}
			return number ++;
		}

	}

	public void testSequenceContinuity() {
		TicketSpec spec = TicketSpec.newDefaultBuilder().setGranularity(Granularity.HOUR).build();
		TicketConfig<Void, Void> config = TicketConfig.getDefault().withSpecifications(spec);
		TestSequences sequences = new TestSequences();
		Ticket<Void, Void> ticket1 = config.newFactory(sequences).machine().ticket();
		Ticket<Void, Void> ticket2 = config.newFactory(sequences).machine().ticket();
		assertTrue(ticket1.getSequenceNumber() + 1 == ticket2.getSequenceNumber());
	}

}
