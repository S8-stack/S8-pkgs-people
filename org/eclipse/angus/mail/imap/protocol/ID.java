package org.eclipse.angus.mail.imap.protocol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.eclipse.angus.mail.iap.Argument;
import org.eclipse.angus.mail.iap.ProtocolException;
import org.eclipse.angus.mail.iap.Response;

public class ID {
	private Map<String, String> serverParams = null;

	public ID(Response r) throws ProtocolException {
		r.skipSpaces();
		int c = r.peekByte();
		if (c != 78 && c != 110) {
			if (c != 40) {
				throw new ProtocolException("Missing '(' at start of ID");
			} else {
				this.serverParams = new HashMap();
				String[] v = r.readStringList();
				if (v != null) {
					for (int i = 0; i < v.length; i += 2) {
						String name = v[i];
						if (name == null) {
							throw new ProtocolException("ID field name null");
						}

						if (i + 1 >= v.length) {
							throw new ProtocolException("ID field without value: " + name);
						}

						String value = v[i + 1];
						this.serverParams.put(name, value);
					}
				}

				this.serverParams = Collections.unmodifiableMap(this.serverParams);
			}
		}
	}

	Map<String, String> getServerParams() {
		return this.serverParams;
	}

	static Argument getArgumentList(Map<String, String> clientParams) {
		Argument arg = new Argument();
		if (clientParams == null) {
			arg.writeAtom("NIL");
			return arg;
		} else {
			Argument list = new Argument();
			Iterator var3 = clientParams.entrySet().iterator();

			while (var3.hasNext()) {
				Map.Entry<String, String> e = (Map.Entry) var3.next();
				list.writeNString((String) e.getKey());
				list.writeNString((String) e.getValue());
			}

			arg.writeArgument(list);
			return arg;
		}
	}
}