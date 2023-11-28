package org.eclipse.angus.mail.pop3;

import jakarta.mail.Provider;
import jakarta.mail.Provider.Type;
import org.eclipse.angus.mail.util.DefaultProvider;

@DefaultProvider
public class POP3Provider extends Provider {
	public POP3Provider() {
		super(Type.STORE, "pop3", POP3Store.class.getName(), "Oracle", (String) null);
	}
}