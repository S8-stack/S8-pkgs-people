package org.eclipse.angus.mail.smtp;

import jakarta.mail.Session;
import jakarta.mail.URLName;

public class SMTPSSLTransport extends SMTPTransport {
	public SMTPSSLTransport(Session session, URLName urlname) {
		super(session, urlname, "smtps", true);
	}
}