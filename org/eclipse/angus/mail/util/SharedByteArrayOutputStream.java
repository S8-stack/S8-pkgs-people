package org.eclipse.angus.mail.util;

import jakarta.mail.util.SharedByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class SharedByteArrayOutputStream extends ByteArrayOutputStream {
	public SharedByteArrayOutputStream(int size) {
		super(size);
	}

	public InputStream toStream() {
		return new SharedByteArrayInputStream(this.buf, 0, this.count);
	}
}