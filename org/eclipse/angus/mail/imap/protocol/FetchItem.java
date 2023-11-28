package org.eclipse.angus.mail.imap.protocol;

import jakarta.mail.FetchProfile;
import org.eclipse.angus.mail.iap.ParsingException;

public abstract class FetchItem {
	private String name;
	private FetchProfile.Item fetchProfileItem;

	public FetchItem(String name, FetchProfile.Item fetchProfileItem) {
		this.name = name;
		this.fetchProfileItem = fetchProfileItem;
	}

	public String getName() {
		return this.name;
	}

	public FetchProfile.Item getFetchProfileItem() {
		return this.fetchProfileItem;
	}

	public abstract Object parseItem(FetchResponse var1) throws ParsingException;
}