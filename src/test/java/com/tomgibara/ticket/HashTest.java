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

import java.awt.Point;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;

import junit.framework.TestCase;

import com.tomgibara.bits.BitVector;

public class HashTest extends TestCase {

	private static final boolean SHOW_HASHES = Boolean.valueOf(System.getProperty("com.tomgibara.ticket.showHashes"));

	public void testKeccakDigest() {
		KeccakDigest digest1 = new KeccakDigest(224);
		int digestSize = digest1.getDigestSize();
		assertEquals(224 / 8, digestSize);
		expect("f71837502ba8e10837bdd8d365adb85591895602fc552b48b7390abd", digest1); // ""
		digest1.update((byte)'a');
		KeccakDigest digest2 = new KeccakDigest(digest1);
		expect("7cf87d912ee7088d30ec23f8e7100d9319bff090618b439d3fe91308", digest1); // "a"
		digest1.update((byte)'b');
		expect("2b7904dc1950b9ec7acf9b5d7798e5d6d8b73a220c7801378b6d2592", digest1); // "b"
		digest2.update((byte)'b');
		expect("54927ada38dd4928ba3bc8d40059dbe1ba68ed7f8e3a6fb3b41492f3", digest2); // "ab"
	}

	private void expect(String expected, KeccakDigest digest) {
		byte[] result = new byte[digest.getDigestSize()];
		digest.doFinal(result, 0);
		assertEquals(expected, new BigInteger(1, result).toString(16));
	}

	public void testPrimitiveHashes() {
		Class<?>[] classes = {
				boolean.class,
				byte.class,
				short.class,
				char.class,
				int.class,
				float.class,
				long.class,
				double.class
			};

		int bits = 4;
		Map<Point, String> hashes = new LinkedHashMap<Point, String>();
		for (int i = 0; i < 32 - bits; i++) {
			int size = 1 << bits;
			BitVector v = new BitVector(size);
			SortedSet<Integer> set = v.asSet();
			int mask = size - 1;
			StringBuilder sb = new StringBuilder();
			for (Class<?> clss : classes) {
				int hash = clss.getName().hashCode();
				int h = (hash >> i) & mask;
				sb.append(String.format("%10s %4d%n", clss.getName(), h));
				set.add(h);
			}
			if (set.size() == classes.length) {
				Point p = new Point(i, mask);
				String str = String.format("hash >> %d & 0x%02x%n%s", i, mask, sb);
				hashes.put(p, str);
			}
		}
		assertTrue(hashes.containsKey(new Point(8, 15))); // used in adapter
		if (SHOW_HASHES) {
			for (String str : hashes.values()) {
				System.out.println(str);
			}
		}
	}

}
