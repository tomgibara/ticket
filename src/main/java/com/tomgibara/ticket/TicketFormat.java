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

import com.tomgibara.bits.BitReader;
import com.tomgibara.bits.BitVector;
import com.tomgibara.bits.BitWriter;

/**
 * Controls the formatting of tickets created by a {@link TicketMachine}.
 * <p>
 * A ticket that is encoded using any format remains decodable by a compatibly
 * configured factory, irrespective of the encoding used by the factory to
 * create new tickets.
 * <p>
 * Instances of this class are safe for concurrent access by multiple threads.
 *
 * @author Tom Gibara
 * @see TicketFactory#setFormat(TicketFormat)
 */

//TODO make serializable?
//TODO reconsider having ticket character limit here
public final class TicketFormat {

	// statics

	private static final char[] CHARS_L = {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
		'g', 'h', 'j', 'k', 'm', 'n', 'p', 'q',
		'r', 's', 't', 'u', 'v', 'w', 'x', 'y'
	};
	private static final char[] CHARS_U = {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
		'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q',
		'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y'
	};
	private static final int[] BITS = new int[128];
	static {
		Arrays.fill(BITS, -1);
		for (int i = 0; i < 32; i++) {
			BITS[CHARS_L[i]] = i;
			BITS[CHARS_U[i]] = i;
		}
	}
	private static final char PAD_CHAR_L = 'z';
	private static final char PAD_CHAR_U = 'z';

	/**
	 * The format which will be applied by ticket factories if no format has
	 * been specified.
	 *
	 * @see TicketFactory#getFormat()
	 */

	public static TicketFormat DEFAULT = new TicketFormat(256, false, 5, '-', true);

	// fields

	private final int ticketCharLimit;
	private final boolean upperCase;
	private final int charGroupLength;
	private final char separatorChar;
	private final boolean padGroups;

	private final char[] chars;
	private final char padChar;

	// constructors

	/**
	 * Creates a new ticket format. See the corresponding accessors on this
	 * class for a complete explanation of the parameters
	 *
	 * @param ticketCharLimit
	 *            the maximum length of ticket that will be encoded or decoded
	 * @param upperCase
	 *            true if the tickets should be encoded using upper case
	 *            characters, false if lower case characters should be used
	 * @param charGroupLength
	 *            the number of characters that are grouped for readability, 0
	 *            to disable grouping
	 * @param separatorChar
	 *            the character used to separate groups
	 * @param padGroups
	 *            whether the ticket should be padded to a whole number of
	 *            characters.
	 */

	public TicketFormat(int ticketCharLimit, boolean upperCase, int charGroupLength, char separatorChar, boolean padGroups) {
		if (ticketCharLimit < 1) throw new IllegalArgumentException("Non-positive ticketCharLimit");
		if (charGroupLength < 0) throw new IllegalArgumentException("Negative charGroupLength");
		if (separatorChar < ' ' || separatorChar > '~') throw new IllegalArgumentException("Non-printable or non ASCII separatorChar");
		if (BITS[separatorChar] != -1) throw new IllegalArgumentException("separatorChar used for ticket encoding");
		separatorChar = upperCase ? Character.toUpperCase(separatorChar) : Character.toLowerCase(separatorChar);
		this.ticketCharLimit = ticketCharLimit;
		this.upperCase = upperCase;
		this.charGroupLength = charGroupLength;
		this.separatorChar = separatorChar;
		this.padGroups = padGroups;

		chars = upperCase ? CHARS_U : CHARS_L;
		padChar = upperCase ? PAD_CHAR_U : PAD_CHAR_L;
	}

	// accessors

	/**
	 * Attempts to encode or decode tickets who's string length exceeds this
	 * value will fail with a {@link TicketException}.
	 *
	 * @return the limit in characters
	 */

	public int getTicketCharLimit() {
		return ticketCharLimit;
	}

	/**
	 * Whether tickets should be encoded using upper case characters. A value of
	 * true indicates that upper case characters should be used. A value of
	 * false indicates that lower case characters should be used.
	 *
	 * @return whether encoded tickets are upper case
	 */

	public boolean isUpperCase() {
		return upperCase;
	}

	/**
	 * For readability, the characters comprising an encoded ticket may be
	 * separated into regularly sized groups. This value should be zero if
	 * the characters should not be grouped.
	 *
	 * @return the group length or zero
	 */

	public int getCharGroupLength() {
		return charGroupLength;
	}

	/**
	 * The character used to separate regular sized groups of characters in a
	 * ticket's encoding.
	 *
	 * @return the separation character
	 * @see #getCharGroupLength()
	 */

	public char getSeparatorChar() {
		return separatorChar;
	}

	// object methods

	@Override
	public int hashCode() {
		return
				(ticketCharLimit     ) + 31 * (
				(upperCase ? 0 : 1337) + 31 * (
				(charGroupLength     ) + 31 * (
				(separatorChar       ))));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof TicketFormat)) return false;
		TicketFormat that = (TicketFormat) obj;
		if (this.ticketCharLimit != that.ticketCharLimit) return false;
		if (this.upperCase != that.upperCase) return false;
		if (this.charGroupLength != that.charGroupLength) return false;
		if (this.separatorChar != that.separatorChar) return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format(
				"ticketCharLimit: %d, upperCase %s, charGroupLength: %d, separatorChar: %s",
				ticketCharLimit, upperCase, charGroupLength, separatorChar
				);
	}

	// package methods

	String encode(BitVector bits) {
		int count = bits.size() / 5;
		BitReader reader = bits.openReader();
		StringBuilder sb;
		if (charGroupLength == 0) {
			// simpler case
			checkTicketLength(count);
			sb = new StringBuilder(count);
			for (int i = 0; i < count; i++) {
				sb.append(chars[reader.read(5)]);
			}
		} else {
			// slightly more complex case
			int sepCount = (count - 1) / charGroupLength;
			int padCount = padGroups ? charGroupLength - 1 - (count + charGroupLength - 1) % charGroupLength : 0;
			int length = count + sepCount + padCount;
			sb = new StringBuilder(length);
			for (int i = 0; i < count; i++) {
				if (i > 0 && (i % charGroupLength) == 0) sb.append(separatorChar);
				sb.append(chars[reader.read(5)]);
			}
			for (int i = 0; i < padCount; i++) {
				sb.append(padChar);
			}
		}
		return sb.toString();
	}

	BitVector decode(String str) {
		int length = str.length();
		checkTicketLength(length);
		int count = 0;
		for (int i = 0; i < length; i++) {
			char c = str.charAt(i);
			if (c < ' ' || c > '~') throw new TicketException("Non-printable or non ASCII ticket character");
			if (BITS[c] == -1) count++; // assume it's a separator character
		}
		int size = (length - count) * 5;
		BitVector vector = new BitVector(size);
		BitWriter writer = vector.openWriter();
		for (int i = 0; i < length; i++) {
			char c = str.charAt(i);
			int bits = BITS[c];
			if (bits == -1) continue;
			writer.write(bits, 5);
		}
		return vector;
	}

	// private utility methods

	private void checkTicketLength(int length) {
		if (length > ticketCharLimit) {
			throw new TicketException("Ticket length exceeds configured maximum");
		}
	}

}
