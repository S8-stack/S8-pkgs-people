package org.eclipse.angus.mail.pop3;

import jakarta.mail.Provider;
import jakarta.mail.Provider.Type;
import org.eclipse.angus.mail.util.DefaultProvider;

@DefaultProvider
public class POP3SSLProvider extends Provider {
	public POP3SSLProvider() {
		super(Type.STORE, "pop3s", POP3SSLStore.class.getName(), "Oracle", (String) null);
	}
}