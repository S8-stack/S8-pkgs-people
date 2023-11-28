package org.eclipse.angus.mail.imap.protocol;

import java.io.ByteArrayInputStream;
import org.eclipse.angus.mail.iap.ByteArray;
import org.eclipse.angus.mail.iap.ParsingException;

public class BODY implements Item {
	static final char[] name = new char[]{'B', 'O', 'D', 'Y'};
	private final int msgno;
	private final ByteArray data;
	private final String section;
	private final int origin;
	private final boolean isHeader;

	public BODY(FetchResponse r) throws ParsingException {
		this.msgno = r.getNumber();
		r.skipSpaces();
		if (r.readByte() != 91) {
			throw new ParsingException("BODY parse error: missing ``['' at section start");
		} else {
			this.section = r.readString(']');
			if (r.readByte() != 93) {
				throw new ParsingException("BODY parse error: missing ``]'' at section end");
			} else {
				this.isHeader = this.section.regionMatches(true, 0, "HEADER", 0, 6);
				if (r.readByte() == 60) {
					this.origin = r.readNumber();
					r.skip(1);
				} else {
					this.origin = -1;
				}

				this.data = r.readByteArray();
			}
		}
	}

	public ByteArray getByteArray() {
		return this.data;
	}

	public ByteArrayInputStream getByteArrayInputStream() {
		return this.data != null ? this.data.toByteArrayInputStream() : null;
	}

	public boolean isHeader() {
		return this.isHeader;
	}

	public String getSection() {
		return this.section;
	}

	public int getOrigin() {
		return this.origin;
	}
}