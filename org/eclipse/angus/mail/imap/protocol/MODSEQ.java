package org.eclipse.angus.mail.imap.protocol;

import org.eclipse.angus.mail.iap.ParsingException;

public class MODSEQ implements Item {
	static final char[] name = new char[]{'M', 'O', 'D', 'S', 'E', 'Q'};
	public int seqnum;
	public long modseq;

	public MODSEQ(FetchResponse r) throws ParsingException {
		this.seqnum = r.getNumber();
		r.skipSpaces();
		if (r.readByte() != 40) {
			throw new ParsingException("MODSEQ parse error");
		} else {
			this.modseq = r.readLong();
			if (!r.isNextNonSpace(')')) {
				throw new ParsingException("MODSEQ parse error");
			}
		}
	}
}