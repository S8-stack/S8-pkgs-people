package org.eclipse.angus.mail.imap;

import jakarta.mail.Message;
import jakarta.mail.search.SearchTerm;
import java.util.Date;

public final class OlderTerm extends SearchTerm {
	private int interval;
	private static final long serialVersionUID = 3951078948727995682L;

	public OlderTerm(int interval) {
		this.interval = interval;
	}

	public int getInterval() {
		return this.interval;
	}

	public boolean match(Message msg) {
		Date d;
		try {
			d = msg.getReceivedDate();
		} catch (Exception var4) {
			return false;
		}

		if (d == null) {
			return false;
		} else {
			return d.getTime() <= System.currentTimeMillis() - (long) this.interval * 1000L;
		}
	}

	public boolean equals(Object obj) {
		if (!(obj instanceof OlderTerm)) {
			return false;
		} else {
			return this.interval == ((OlderTerm) obj).interval;
		}
	}

	public int hashCode() {
		return this.interval;
	}
}