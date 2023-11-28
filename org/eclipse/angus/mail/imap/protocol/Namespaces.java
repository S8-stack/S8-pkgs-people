package org.eclipse.angus.mail.imap.protocol;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.angus.mail.iap.ProtocolException;
import org.eclipse.angus.mail.iap.Response;

public class Namespaces {
	public Namespace[] personal;
	public Namespace[] otherUsers;
	public Namespace[] shared;

	public Namespaces(Response r) throws ProtocolException {
		this.personal = this.getNamespaces(r);
		this.otherUsers = this.getNamespaces(r);
		this.shared = this.getNamespaces(r);
	}

	private Namespace[] getNamespaces(Response r) throws ProtocolException {
		if (!r.isNextNonSpace('(')) {
			String s = r.readAtom();
			if (s == null) {
				throw new ProtocolException("Expected NIL, got null");
			} else if (!s.equalsIgnoreCase("NIL")) {
				throw new ProtocolException("Expected NIL, got " + s);
			} else {
				return null;
			}
		} else {
			List<Namespace> v = new ArrayList();

			do {
				Namespace ns = new Namespace(r);
				v.add(ns);
			} while (!r.isNextNonSpace(')'));

			return (Namespace[]) v.toArray(new Namespace[0]);
		}
	}

	public static class Namespace {
		public String prefix;
		public char delimiter;

		public Namespace(Response r) throws ProtocolException {
			if (!r.isNextNonSpace('(')) {
				throw new ProtocolException("Missing '(' at start of Namespace");
			} else {
				this.prefix = r.readString();
				if (!r.supportsUtf8()) {
					this.prefix = BASE64MailboxDecoder.decode(this.prefix);
				}

				r.skipSpaces();
				if (r.peekByte() == 34) {
					r.readByte();
					this.delimiter = (char) r.readByte();
					if (this.delimiter == '\\') {
						this.delimiter = (char) r.readByte();
					}

					if (r.readByte() != 34) {
						throw new ProtocolException("Missing '\"' at end of QUOTED_CHAR");
					}
				} else {
					String s = r.readAtom();
					if (s == null) {
						throw new ProtocolException("Expected NIL, got null");
					}

					if (!s.equalsIgnoreCase("NIL")) {
						throw new ProtocolException("Expected NIL, got " + s);
					}

					this.delimiter = 0;
				}

				if (!r.isNextNonSpace(')')) {
					r.readString();
					r.skipSpaces();
					r.readStringList();
					if (!r.isNextNonSpace(')')) {
						throw new ProtocolException("Missing ')' at end of Namespace");
					}
				}
			}
		}
	}
}