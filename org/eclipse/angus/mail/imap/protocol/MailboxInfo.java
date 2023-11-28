package org.eclipse.angus.mail.imap.protocol;

import jakarta.mail.Flags;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.angus.mail.iap.ParsingException;
import org.eclipse.angus.mail.iap.Response;

public class MailboxInfo {
	public Flags availableFlags = null;
	public Flags permanentFlags = null;
	public int total = -1;
	public int recent = -1;
	public int first = -1;
	public long uidvalidity = -1L;
	public long uidnext = -1L;
	public boolean uidNotSticky = false;
	public long highestmodseq = -1L;
	public int mode;
	public List<IMAPResponse> responses;

	public MailboxInfo(Response[] r) throws ParsingException {
		for (int i = 0; i < r.length; ++i) {
			if (r[i] != null && r[i] instanceof IMAPResponse) {
				IMAPResponse ir = (IMAPResponse) r[i];
				if (ir.keyEquals("EXISTS")) {
					this.total = ir.getNumber();
					r[i] = null;
				} else if (ir.keyEquals("RECENT")) {
					this.recent = ir.getNumber();
					r[i] = null;
				} else if (ir.keyEquals("FLAGS")) {
					this.availableFlags = new FLAGS(ir);
					r[i] = null;
				} else if (ir.keyEquals("VANISHED")) {
					if (this.responses == null) {
						this.responses = new ArrayList();
					}

					this.responses.add(ir);
					r[i] = null;
				} else if (ir.keyEquals("FETCH")) {
					if (this.responses == null) {
						this.responses = new ArrayList();
					}

					this.responses.add(ir);
					r[i] = null;
				} else {
					boolean handled;
					String s;
					if (ir.isUnTagged() && ir.isOK()) {
						ir.skipSpaces();
						if (ir.readByte() != 91) {
							ir.reset();
						} else {
							handled = true;
							s = ir.readAtom();
							if (s.equalsIgnoreCase("UNSEEN")) {
								this.first = ir.readNumber();
							} else if (s.equalsIgnoreCase("UIDVALIDITY")) {
								this.uidvalidity = ir.readLong();
							} else if (s.equalsIgnoreCase("PERMANENTFLAGS")) {
								this.permanentFlags = new FLAGS(ir);
							} else if (s.equalsIgnoreCase("UIDNEXT")) {
								this.uidnext = ir.readLong();
							} else if (s.equalsIgnoreCase("HIGHESTMODSEQ")) {
								this.highestmodseq = ir.readLong();
							} else {
								handled = false;
							}

							if (handled) {
								r[i] = null;
							} else {
								ir.reset();
							}
						}
					} else if (ir.isUnTagged() && ir.isNO()) {
						ir.skipSpaces();
						if (ir.readByte() != 91) {
							ir.reset();
						} else {
							handled = true;
							s = ir.readAtom();
							if (s.equalsIgnoreCase("UIDNOTSTICKY")) {
								this.uidNotSticky = true;
							} else {
								handled = false;
							}

							if (handled) {
								r[i] = null;
							} else {
								ir.reset();
							}
						}
					}
				}
			}
		}

		if (this.permanentFlags == null) {
			if (this.availableFlags != null) {
				this.permanentFlags = new Flags(this.availableFlags);
			} else {
				this.permanentFlags = new Flags();
			}
		}

	}
}