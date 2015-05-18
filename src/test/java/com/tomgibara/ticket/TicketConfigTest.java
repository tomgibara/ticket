package com.tomgibara.ticket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.TimeZone;

import com.tomgibara.ticket.Ticket.Granularity;

import junit.framework.TestCase;

public class TicketConfigTest extends TestCase {

	interface CustomData {

		@TicketField(0)
		int txId();

	}

	public void testSerialization() throws Exception {
		testSerialization(TicketConfig.getDefault());
		testSerialization(TicketConfig.getDefault().withDataType(CustomData.class).withSpecifications(
				TicketSpec.defaultBuilder().setGranularity(Granularity.MILLISECOND).setHashLength(18).setOriginYear(0).setTimeZone(TimeZone.getTimeZone("BST")).build()
				));
	}

	private void testSerialization(TicketConfig<?, ?> config) throws IOException, ClassNotFoundException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream oo = new ObjectOutputStream(out);
		oo.writeObject(config);
		oo.close();
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ObjectInputStream oi = new ObjectInputStream(in);
		Object result = oi.readObject();
		assertEquals(config, result);
		assertEquals(config.toString(), result.toString());
	}

}
