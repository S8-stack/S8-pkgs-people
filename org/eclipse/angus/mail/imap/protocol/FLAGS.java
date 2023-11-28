package org.eclipse.angus.mail.imap.protocol;

import jakarta.mail.Flags;
import org.eclipse.angus.mail.iap.ParsingException;

public class FLAGS extends Flags implements Item {
	static final char[] name = new char[]{'F', 'L', 'A', 'G', 'S'};
	public int msgno;
	private static final long serialVersionUID = 439049847053756670L;

	public FLAGS(IMAPResponse var1) throws ParsingException {
		// $FF: Couldn't be decompiled
	}
}