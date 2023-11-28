package org.eclipse.angus.mail.imap;

import jakarta.mail.Provider;
import jakarta.mail.Provider.Type;
import org.eclipse.angus.mail.util.DefaultProvider;

@DefaultProvider
public class IMAPSSLProvider extends Provider {
	public IMAPSSLProvider() {
		super(Type.STORE, "imaps", IMAPSSLStore.class.getName(), "Oracle", (String) null);
	}
}