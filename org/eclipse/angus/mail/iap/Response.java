package org.eclipse.angus.mail.iap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.angus.mail.util.ASCIIUtility;

public class Response {
	protected int index;
	protected int pindex;
	protected int size;
	protected byte[] buffer;
	protected int type;
	protected String tag;
	protected Exception ex;
	protected boolean utf8;
	private static final int increment = 100;
	public static final int TAG_MASK = 3;
	public static final int CONTINUATION = 1;
	public static final int TAGGED = 2;
	public static final int UNTAGGED = 3;
	public static final int TYPE_MASK = 28;
	public static final int OK = 4;
	public static final int NO = 8;
	public static final int BAD = 12;
	public static final int BYE = 16;
	public static final int SYNTHETIC = 32;
	private static String ATOM_CHAR_DELIM = " (){%*\"\\]";
	private static String ASTRING_CHAR_DELIM = " (){%*\"\\";

	public Response(String s) {
		this(s, true);
	}

	public Response(String s, boolean supportsUtf8) {
		this.buffer = null;
		this.type = 0;
		this.tag = null;
		if (supportsUtf8) {
			this.buffer = s.getBytes(StandardCharsets.UTF_8);
		} else {
			this.buffer = s.getBytes(StandardCharsets.US_ASCII);
		}

		this.size = this.buffer.length;
		this.utf8 = supportsUtf8;
		this.parse();
	}

	public Response(Protocol p) throws IOException, ProtocolException {
		this.buffer = null;
		this.type = 0;
		this.tag = null;
		ByteArray ba = p.getResponseBuffer();
		ByteArray response = p.getInputStream().readResponse(ba);
		this.buffer = response.getBytes();
		this.size = response.getCount() - 2;
		this.utf8 = p.supportsUtf8();
		this.parse();
	}

	public Response(Response r) {
		this.buffer = null;
		this.type = 0;
		this.tag = null;
		this.index = r.index;
		this.pindex = r.pindex;
		this.size = r.size;
		this.buffer = r.buffer;
		this.type = r.type;
		this.tag = r.tag;
		this.ex = r.ex;
		this.utf8 = r.utf8;
	}

	public static Response byeResponse(Exception ex) {
		String err = "* BYE Jakarta Mail Exception: " + ex.toString();
		err = err.replace('\r', ' ').replace('\n', ' ');
		Response r = new Response(err);
		r.type |= 32;
		r.ex = ex;
		return r;
	}

	public boolean supportsUtf8() {
		return this.utf8;
	}

	private void parse() {
		this.index = 0;
		if (this.size != 0) {
			if (this.buffer[this.index] == 43) {
				this.type |= 1;
				++this.index;
			} else {
				if (this.buffer[this.index] == 42) {
					this.type |= 3;
					++this.index;
				} else {
					this.type |= 2;
					this.tag = this.readAtom();
					if (this.tag == null) {
						this.tag = "";
					}
				}

				int mark = this.index;
				String s = this.readAtom();
				if (s == null) {
					s = "";
				}

				if (s.equalsIgnoreCase("OK")) {
					this.type |= 4;
				} else if (s.equalsIgnoreCase("NO")) {
					this.type |= 8;
				} else if (s.equalsIgnoreCase("BAD")) {
					this.type |= 12;
				} else if (s.equalsIgnoreCase("BYE")) {
					this.type |= 16;
				} else {
					this.index = mark;
				}

				this.pindex = this.index;
			}
		}
	}

	public void skipSpaces() {
		while (this.index < this.size && this.buffer[this.index] == 32) {
			++this.index;
		}

	}

	public boolean isNextNonSpace(char c) {
		this.skipSpaces();
		if (this.index < this.size && this.buffer[this.index] == (byte) c) {
			++this.index;
			return true;
		} else {
			return false;
		}
	}

	public void skipToken() {
		while (this.index < this.size && this.buffer[this.index] != 32) {
			++this.index;
		}

	}

	public void skip(int count) {
		this.index += count;
	}

	public byte peekByte() {
		return this.index < this.size ? this.buffer[this.index] : 0;
	}

	public byte readByte() {
		return this.index < this.size ? this.buffer[this.index++] : 0;
	}

	public String readAtom() {
		return this.readDelimString(ATOM_CHAR_DELIM);
	}

	private String readDelimString(String delim) {
		this.skipSpaces();
		if (this.index >= this.size) {
			return null;
		} else {
			int b;
			int start;
			for (start = this.index; this.index < this.size && (b = this.buffer[this.index] & 255) >= 32
					&& delim.indexOf((char) b) < 0 && b != 127; ++this.index) {
			}

			return this.toString(this.buffer, start, this.index);
		}
	}

	public String readString(char delim) {
		this.skipSpaces();
		if (this.index >= this.size) {
			return null;
		} else {
			int start;
			for (start = this.index; this.index < this.size && this.buffer[this.index] != delim; ++this.index) {
			}

			return this.toString(this.buffer, start, this.index);
		}
	}

	public String[] readStringList() {
		return this.readStringList(false);
	}

	public String[] readAtomStringList() {
		return this.readStringList(true);
	}

	private String[] readStringList(boolean atom) {
		this.skipSpaces();
		if (this.buffer[this.index] != 40) {
			return null;
		} else {
			++this.index;
			List<String> result = new ArrayList();

			while (!this.isNextNonSpace(')')) {
				String s = atom ? this.readAtomString() : this.readString();
				if (s == null) {
					break;
				}

				result.add(s);
			}

			return (String[]) result.toArray(new String[0]);
		}
	}

	public int readNumber() {
		this.skipSpaces();

		int start;
		for (start = this.index; this.index < this.size
				&& Character.isDigit((char) this.buffer[this.index]); ++this.index) {
		}

		if (this.index > start) {
			try {
				return ASCIIUtility.parseInt(this.buffer, start, this.index);
			} catch (NumberFormatException var3) {
			}
		}

		return -1;
	}

	public long readLong() {
		this.skipSpaces();

		int start;
		for (start = this.index; this.index < this.size
				&& Character.isDigit((char) this.buffer[this.index]); ++this.index) {
		}

		if (this.index > start) {
			try {
				return ASCIIUtility.parseLong(this.buffer, start, this.index);
			} catch (NumberFormatException var3) {
			}
		}

		return -1L;
	}

	public String readString() {
		return (String) this.parseString(false, true);
	}

	public ByteArrayInputStream readBytes() {
		ByteArray ba = this.readByteArray();
		return ba != null ? ba.toByteArrayInputStream() : null;
	}

	public ByteArray readByteArray() {
		if (this.isContinuation()) {
			this.skipSpaces();
			return new ByteArray(this.buffer, this.index, this.size - this.index);
		} else {
			return (ByteArray) this.parseString(false, false);
		}
	}

	public String readAtomString() {
		return (String) this.parseString(true, true);
	}

	private Object parseString(boolean parseAtoms, boolean returnString) {
		this.skipSpaces();
		byte b = this.buffer[this.index];
		int start;
		int count;
		if (b == 34) {
			++this.index;
			start = this.index;

			for (count = this.index; this.index < this.size && (b = this.buffer[this.index]) != 34; ++this.index) {
				if (b == 92) {
					++this.index;
				}

				if (this.index != count) {
					this.buffer[count] = this.buffer[this.index];
				}

				++count;
			}

			if (this.index >= this.size) {
				return null;
			} else {
				++this.index;
				return returnString
						? this.toString(this.buffer, start, count)
						: new ByteArray(this.buffer, start, count - start);
			}
		} else if (b != 123) {
			if (parseAtoms) {
				start = this.index;
				String s = this.readDelimString(ASTRING_CHAR_DELIM);
				return returnString ? s : new ByteArray(this.buffer, start, this.index);
			} else if (b != 78 && b != 110) {
				return null;
			} else {
				this.index += 3;
				return null;
			}
		} else {
			for (start = ++this.index; this.buffer[this.index] != 125; ++this.index) {
			}

			int count = false;

			try {
				count = ASCIIUtility.parseInt(this.buffer, start, this.index);
			} catch (NumberFormatException var7) {
				return null;
			}

			start = this.index + 3;
			this.index = start + count;
			return returnString
					? this.toString(this.buffer, start, start + count)
					: new ByteArray(this.buffer, start, count);
		}
	}

	private String toString(byte[] buffer, int start, int end) {
		return this.utf8
				? new String(buffer, start, end - start, StandardCharsets.UTF_8)
				: ASCIIUtility.toString(buffer, start, end);
	}

	public int getType() {
		return this.type;
	}

	public boolean isContinuation() {
		return (this.type & 3) == 1;
	}

	public boolean isTagged() {
		return (this.type & 3) == 2;
	}

	public boolean isUnTagged() {
		return (this.type & 3) == 3;
	}

	public boolean isOK() {
		return (this.type & 28) == 4;
	}

	public boolean isNO() {
		return (this.type & 28) == 8;
	}

	public boolean isBAD() {
		return (this.type & 28) == 12;
	}

	public boolean isBYE() {
		return (this.type & 28) == 16;
	}

	public boolean isSynthetic() {
		return (this.type & 32) == 32;
	}

	public String getTag() {
		return this.tag;
	}

	public String getRest() {
		this.skipSpaces();
		return this.toString(this.buffer, this.index, this.size);
	}

	public Exception getException() {
		return this.ex;
	}

	public void reset() {
		this.index = this.pindex;
	}

	public String toString() {
		return this.toString(this.buffer, 0, this.size);
	}
}