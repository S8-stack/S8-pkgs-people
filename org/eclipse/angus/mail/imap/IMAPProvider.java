package org.eclipse.angus.mail.imap;

import jakarta.mail.Provider;
import jakarta.mail.Provider.Type;
import org.eclipse.angus.mail.util.DefaultProvider;

@DefaultProvider
public class IMAPProvider extends Provider {
	public IMAPProvider() {
		super(Type.STORE, "imap", IMAPStore.class.getName(), "Oracle", (String) null);
	}
}