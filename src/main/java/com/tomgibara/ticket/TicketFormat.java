package com.tomgibara.ticket;

import java.util.Arrays;

import com.tomgibara.bits.BitReader;
import com.tomgibara.bits.BitVector;
import com.tomgibara.bits.BitWriter;

//TODO make serializable?
public class TicketFormat {

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

	public static TicketFormat DEFAULT = new TicketFormat();

	private final int ticketCharLimit;
	private final boolean upperCase;
	private final int charGroupLength;
	private final char separatorChar;
	private final boolean padGroups;

	private final char[] chars;
	private final char padChar;

	private TicketFormat() {
		this(256, false, 5, '-', true);
	}

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

	public int getTicketCharLimit() {
		return ticketCharLimit;
	}

	public boolean isUpperCase() {
		return upperCase;
	}

	public int getCharGroupLength() {
		return charGroupLength;
	}

	public char getSeparatorChar() {
		return separatorChar;
	}

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

	private void checkTicketLength(int length) {
		if (length > ticketCharLimit) {
			throw new TicketException("Ticket length exceeds configured maximum");
		}
	}

}
