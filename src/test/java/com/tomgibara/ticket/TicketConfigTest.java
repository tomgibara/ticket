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
				TicketSpec.newDefaultBuilder().setGranularity(Granularity.MILLISECOND).setHashLength(18).setOriginYear(0).setTimeZone(TimeZone.getTimeZone("BST")).build()
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
