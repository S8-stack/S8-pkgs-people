package org.eclipse.angus.mail.smtp;

import jakarta.mail.Provider;
import jakarta.mail.Provider.Type;
import org.eclipse.angus.mail.util.DefaultProvider;

@DefaultProvider
public class SMTPSSLProvider extends Provider {
	public SMTPSSLProvider() {
		super(Type.TRANSPORT, "smtps", SMTPSSLTransport.class.getName(), "Oracle", (String) null);
	}
}