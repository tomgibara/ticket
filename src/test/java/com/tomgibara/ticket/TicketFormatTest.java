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

import junit.framework.TestCase;

public class TicketFormatTest extends TestCase {

	public void testEquals() {
		TicketFormat f1 = new TicketFormat(true, 5, '-', true);
		TicketFormat f2 = new TicketFormat(true, 5, '-', true);
		assertEquals(f1, f2);
		assertEquals(f1.toString(), f2.toString());
		TicketFormat f3 = new TicketFormat(false, 5, '-', true);
		TicketFormat f4 = new TicketFormat(true, 6, '-', true);
		TicketFormat f5 = new TicketFormat(true, 5, '.', true);
		TicketFormat f6 = new TicketFormat(true, 5, '-', false);
		assertFalse(f1.equals(f3));
		assertFalse(f3.equals(f2));
		assertFalse(f1.equals(f4));
		assertFalse(f4.equals(f2));
		assertFalse(f1.equals(f5));
		assertFalse(f5.equals(f2));
		assertFalse(f1.equals(f6));
		assertFalse(f6.equals(f2));
	}

	public void testSerialization() throws IOException, ClassNotFoundException {
		TicketFormat tf = new TicketFormat(true, 6, '.', false);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream oo = new ObjectOutputStream(out);
		oo.writeObject(tf);
		oo.close();
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ObjectInputStream oi = new ObjectInputStream(in);
		Object result = oi.readObject();
		assertEquals(tf, result);
		assertEquals(tf.toString(), result.toString());
	}

}
