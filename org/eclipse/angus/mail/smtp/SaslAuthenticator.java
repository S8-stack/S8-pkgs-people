package org.eclipse.angus.mail.smtp;

import jakarta.mail.MessagingException;

public interface SaslAuthenticator {
	boolean authenticate(String[] var1, String var2, String var3, String var4, String var5) throws MessagingException;
}