package org.eclipse.angus.mail.imap.protocol;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.angus.mail.iap.ParsingException;

public class ListInfo {
	public String name = null;
	public char separator = '/';
	public boolean hasInferiors = true;
	public boolean canOpen = true;
	public int changeState = 3;
	public String[] attrs;
	public static final int CHANGED = 1;
	public static final int UNCHANGED = 2;
	public static final int INDETERMINATE = 3;

	public ListInfo(IMAPResponse r) throws ParsingException {
		String[] s = r.readSimpleList();
		List<String> v = new ArrayList();
		if (s != null) {
			for (int i = 0; i < s.length; ++i) {
				if (s[i].equalsIgnoreCase("\\Marked")) {
					this.changeState = 1;
				} else if (s[i].equalsIgnoreCase("\\Unmarked")) {
					this.changeState = 2;
				} else if (s[i].equalsIgnoreCase("\\Noselect")) {
					this.canOpen = false;
				} else if (s[i].equalsIgnoreCase("\\Noinferiors")) {
					this.hasInferiors = false;
				}

				v.add(s[i]);
			}
		}

		this.attrs = (String[]) v.toArray(new String[0]);
		r.skipSpaces();
		if (r.readByte() == 34) {
			if ((this.separator = (char) r.readByte()) == '\\') {
				this.separator = (char) r.readByte();
			}

			r.skip(1);
		} else {
			r.skip(2);
		}

		r.skipSpaces();
		this.name = r.readAtomString();
		if (!r.supportsUtf8()) {
			this.name = BASE64MailboxDecoder.decode(this.name);
		}

	}
}