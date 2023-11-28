package org.eclipse.angus.mail.imap.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.angus.mail.iap.Protocol;
import org.eclipse.angus.mail.iap.ProtocolException;
import org.eclipse.angus.mail.iap.Response;
import org.eclipse.angus.mail.util.ASCIIUtility;

public class IMAPResponse extends Response {
	private String key;
	private int number;

	public IMAPResponse(Protocol c) throws IOException, ProtocolException {
		super(c);
		this.init();
	}

	private void init() throws IOException, ProtocolException {
		if (this.isUnTagged() && !this.isOK() && !this.isNO() && !this.isBAD() && !this.isBYE()) {
			this.key = this.readAtom();

			try {
				this.number = Integer.parseInt(this.key);
				this.key = this.readAtom();
			} catch (NumberFormatException var2) {
			}
		}

	}

	public IMAPResponse(IMAPResponse r) {
		super(r);
		this.key = r.key;
		this.number = r.number;
	}

	public IMAPResponse(String r) throws IOException, ProtocolException {
		this(r, true);
	}

	public IMAPResponse(String r, boolean utf8) throws IOException, ProtocolException {
		super(r, utf8);
		this.init();
	}

	public String[] readSimpleList() {
		this.skipSpaces();
		if (this.buffer[this.index] != 40) {
			return null;
		} else {
			++this.index;
			List<String> v = new ArrayList();

			int start;
			for (start = this.index; this.buffer[this.index] != 41; ++this.index) {
				if (this.buffer[this.index] == 32) {
					v.add(ASCIIUtility.toString(this.buffer, start, this.index));
					start = this.index + 1;
				}
			}

			if (this.index > start) {
				v.add(ASCIIUtility.toString(this.buffer, start, this.index));
			}

			++this.index;
			int size = v.size();
			if (size > 0) {
				return (String[]) v.toArray(new String[size]);
			} else {
				return null;
			}
		}
	}

	public String getKey() {
		return this.key;
	}

	public boolean keyEquals(String k) {
		return this.key != null && this.key.equalsIgnoreCase(k);
	}

	public int getNumber() {
		return this.number;
	}
}