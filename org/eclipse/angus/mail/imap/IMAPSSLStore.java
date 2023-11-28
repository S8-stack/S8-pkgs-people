package org.eclipse.angus.mail.imap;

import jakarta.mail.Session;
import jakarta.mail.URLName;

public class IMAPSSLStore extends IMAPStore {
	public IMAPSSLStore(Session session, URLName url) {
		super(session, url, "imaps", true);
	}
}