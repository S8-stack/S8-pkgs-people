package org.eclipse.angus.mail.smtp;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;

public class SMTPAddressSucceededException extends MessagingException {
	protected InternetAddress addr;
	protected String cmd;
	protected int rc;
	private static final long serialVersionUID = -1168335848623096749L;

	public SMTPAddressSucceededException(InternetAddress addr, String cmd, int rc, String err) {
		super(err);
		this.addr = addr;
		this.cmd = cmd;
		this.rc = rc;
	}

	public InternetAddress getAddress() {
		return this.addr;
	}

	public String getCommand() {
		return this.cmd;
	}

	public int getReturnCode() {
		return this.rc;
	}
}