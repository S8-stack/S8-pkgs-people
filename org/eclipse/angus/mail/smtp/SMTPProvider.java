package org.eclipse.angus.mail.smtp;

import jakarta.mail.Provider;
import jakarta.mail.Provider.Type;
import org.eclipse.angus.mail.util.DefaultProvider;

@DefaultProvider
public class SMTPProvider extends Provider {
	public SMTPProvider() {
		super(Type.TRANSPORT, "smtp", SMTPTransport.class.getName(), "Oracle", (String) null);
	}
}