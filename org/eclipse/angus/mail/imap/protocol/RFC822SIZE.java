package org.eclipse.angus.mail.imap.protocol;

import org.eclipse.angus.mail.iap.ParsingException;

public class RFC822SIZE implements Item {
	static final char[] name = new char[]{'R', 'F', 'C', '8', '2', '2', '.', 'S', 'I', 'Z', 'E'};
	public int msgno;
	public long size;

	public RFC822SIZE(FetchResponse r) throws ParsingException {
		this.msgno = r.getNumber();
		r.skipSpaces();
		this.size = r.readLong();
	}
}