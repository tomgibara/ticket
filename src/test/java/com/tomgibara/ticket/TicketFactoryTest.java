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
import java.util.HashSet;
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
		TicketSpec spec = TicketSpec.defaultBuilder()
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

	interface LongOrigin {

		@TicketField(0)
		long getId();

	}

	public void testManyOrigins() {
		TicketSpec spec = TicketSpec.defaultBuilder().setGranularity(Granularity.MILLISECOND).build();
		TicketFactory<LongOrigin, Void> factory = TicketConfig.getDefault()
				.withOriginType(LongOrigin.class)
				.withSpecifications(spec)
				.newFactory();

		for (int i = 0; i < 100000; i++) {
			factory.machineForOriginValues((long) i).ticket();
		}

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
		TicketSpec spec = TicketSpec.defaultBuilder().setGranularity(Granularity.HOUR).setHashLength(length).build();
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
		TicketSpec spec = TicketSpec.defaultBuilder().setGranularity(Granularity.MINUTE).build();
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


}
