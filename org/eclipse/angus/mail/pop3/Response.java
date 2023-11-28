package org.eclipse.angus.mail.pop3;

import java.io.InputStream;

class Response {
	boolean ok = false;
	boolean cont = false;
	String data = null;
	InputStream bytes = null;
}