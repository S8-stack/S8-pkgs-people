package org.eclipse.angus.mail.imap.protocol;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.angus.mail.iap.ParsingException;
import org.eclipse.angus.mail.iap.Response;

class IMAPAddress extends InternetAddress {
	private boolean group = false;
	private InternetAddress[] grouplist;
	private String groupname;
	private static final long serialVersionUID = -3835822029483122232L;

	IMAPAddress(Response r) throws ParsingException {
		r.skipSpaces();
		if (r.readByte() != 40) {
			throw new ParsingException("ADDRESS parse error");
		} else {
			this.encodedPersonal = r.readString();
			r.readString();
			String mb = r.readString();
			String host = r.readString();
			r.skipSpaces();
			if (!r.isNextNonSpace(')')) {
				throw new ParsingException("ADDRESS parse error");
			} else {
				if (host == null) {
					this.group = true;
					this.groupname = mb;
					if (this.groupname == null) {
						return;
					}

					StringBuilder sb = new StringBuilder();
					sb.append(this.groupname).append(':');
					List<InternetAddress> v = new ArrayList();

					while (r.peekByte() != 41) {
						IMAPAddress a = new IMAPAddress(r);
						if (a.isEndOfGroup()) {
							break;
						}

						if (v.size() != 0) {
							sb.append(',');
						}

						sb.append(a.toString());
						v.add(a);
					}

					sb.append(';');
					this.address = sb.toString();
					this.grouplist = (InternetAddress[]) v.toArray(new IMAPAddress[0]);
				} else if (mb != null && mb.length() != 0) {
					if (host.length() == 0) {
						this.address = mb;
					} else {
						this.address = mb + "@" + host;
					}
				} else {
					this.address = host;
				}

			}
		}
	}

	boolean isEndOfGroup() {
		return this.group && this.groupname == null;
	}

	public boolean isGroup() {
		return this.group;
	}

	public InternetAddress[] getGroup(boolean strict) throws AddressException {
		return this.grouplist == null ? null : (InternetAddress[]) this.grouplist.clone();
	}
}