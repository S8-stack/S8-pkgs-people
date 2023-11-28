package org.eclipse.angus.mail.util;

import java.io.OutputStream;

public class BEncoderStream extends BASE64EncoderStream {
	public BEncoderStream(OutputStream out) {
		super(out, Integer.MAX_VALUE);
	}
}