package org.eclipse.angus.mail.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class LineOutputStream extends FilterOutputStream implements jakarta.mail.util.LineOutputStream {
	private boolean allowutf8;
	private static byte[] newline = new byte[2];

	public LineOutputStream(OutputStream out) {
		this(out, false);
	}

	public LineOutputStream(OutputStream out, boolean allowutf8) {
		super(out);
		this.allowutf8 = allowutf8;
	}

	public void writeln(String s) throws IOException {
		byte[] bytes;
		if (this.allowutf8) {
			bytes = s.getBytes(StandardCharsets.UTF_8);
		} else {
			bytes = ASCIIUtility.getBytes(s);
		}

		this.out.write(bytes);
		this.out.write(newline);
	}

	public void writeln() throws IOException {
		this.out.write(newline);
	}

	static {
		newline[0] = 13;
		newline[1] = 10;
	}
}