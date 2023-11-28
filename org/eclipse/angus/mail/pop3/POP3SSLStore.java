package org.eclipse.angus.mail.pop3;

import jakarta.mail.Session;
import jakarta.mail.URLName;

public class POP3SSLStore extends POP3Store {
	public POP3SSLStore(Session session, URLName url) {
		super(session, url, "pop3s", true);
	}
}