package org.eclipse.angus.mail.imap.protocol;

import java.io.ByteArrayInputStream;
import org.eclipse.angus.mail.iap.ByteArray;
import org.eclipse.angus.mail.iap.ParsingException;

public class RFC822DATA implements Item {
	static final char[] name = new char[]{'R', 'F', 'C', '8', '2', '2'};
	private final int msgno;
	private final ByteArray data;
	private final boolean isHeader;

	public RFC822DATA(FetchResponse r) throws ParsingException {
		this(r, false);
	}

	public RFC822DATA(FetchResponse r, boolean isHeader) throws ParsingException {
		this.isHeader = isHeader;
		this.msgno = r.getNumber();
		r.skipSpaces();
		this.data = r.readByteArray();
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
}