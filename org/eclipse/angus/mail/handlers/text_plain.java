package org.eclipse.angus.mail.handlers;

import jakarta.activation.ActivationDataFlavor;
import jakarta.activation.DataSource;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeUtility;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

public class text_plain extends handler_base {
	private static ActivationDataFlavor[] myDF = new ActivationDataFlavor[]{
			new ActivationDataFlavor(String.class, "text/plain", "Text String")};

	protected ActivationDataFlavor[] getDataFlavors() {
		return myDF;
	}

	public Object getContent(DataSource ds) throws IOException {
		String enc = null;
		InputStreamReader is = null;

		try {
			enc = this.getCharset(ds.getContentType());
			is = new InputStreamReader(ds.getInputStream(), enc);
		} catch (IllegalArgumentException var16) {
			throw new UnsupportedEncodingException(enc);
		}

		try {
			int pos = 0;
			char[] buf = new char[1024];

			int count;
			while ((count = is.read(buf, pos, buf.length - pos)) != -1) {
				pos += count;
				if (pos >= buf.length) {
					int size = buf.length;
					size += Math.min(size, 262144);
					char[] tbuf = new char[size];
					System.arraycopy(buf, 0, tbuf, 0, pos);
					buf = tbuf;
				}
			}

			String var18 = new String(buf, 0, pos);
			return var18;
		} finally {
			try {
				is.close();
			} catch (IOException var15) {
			}

		}
	}

	public void writeTo(Object obj, String type, OutputStream os) throws IOException {
		if (!(obj instanceof String)) {
			throw new IOException("\"" + this.getDataFlavors()[0].getMimeType()
					+ "\" DataContentHandler requires String object, was given object of type "
					+ obj.getClass().toString());
		} else {
			String enc = null;
			OutputStreamWriter osw = null;

			try {
				enc = this.getCharset(type);
				osw = new OutputStreamWriter(new NoCloseOutputStream(os), enc);
			} catch (IllegalArgumentException var7) {
				throw new UnsupportedEncodingException(enc);
			}

			String s = (String) obj;
			osw.write(s, 0, s.length());
			osw.close();
		}
	}

	private String getCharset(String type) {
		try {
			ContentType ct = new ContentType(type);
			String charset = ct.getParameter("charset");
			if (charset == null) {
				charset = "us-ascii";
			}

			return MimeUtility.javaCharset(charset);
		} catch (Exception var4) {
			return null;
		}
	}

	private static class NoCloseOutputStream extends FilterOutputStream {
		NoCloseOutputStream(OutputStream os) {
			super(os);
		}

		public void close() {
		}
	}
}