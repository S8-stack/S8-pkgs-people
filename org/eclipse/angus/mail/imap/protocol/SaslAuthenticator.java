package org.eclipse.angus.mail.imap.protocol;

import org.eclipse.angus.mail.iap.ProtocolException;

public interface SaslAuthenticator {
	boolean authenticate(String[] var1, String var2, String var3, String var4, String var5) throws ProtocolException;
}