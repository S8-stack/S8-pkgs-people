package org.eclipse.angus.mail.handlers;

import jakarta.activation.ActivationDataFlavor;
import jakarta.activation.DataSource;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class image_gif extends handler_base {
	private static ActivationDataFlavor[] myDF = new ActivationDataFlavor[]{
			new ActivationDataFlavor(Image.class, "image/gif", "GIF Image")};

	protected ActivationDataFlavor[] getDataFlavors() {
		return myDF;
	}

	public Object getContent(DataSource ds) throws IOException {
		InputStream is = ds.getInputStream();
		int pos = 0;
		byte[] buf = new byte[1024];

		int count;
		while ((count = is.read(buf, pos, buf.length - pos)) != -1) {
			pos += count;
			if (pos >= buf.length) {
				int size = buf.length;
				size += Math.min(size, 262144);
				byte[] tbuf = new byte[size];
				System.arraycopy(buf, 0, tbuf, 0, pos);
				buf = tbuf;
			}
		}

		Toolkit tk = Toolkit.getDefaultToolkit();
		return tk.createImage(buf, 0, pos);
	}

	public void writeTo(Object obj, String type, OutputStream os) throws IOException {
		if (!(obj instanceof Image)) {
			throw new IOException("\"" + this.getDataFlavors()[0].getMimeType()
					+ "\" DataContentHandler requires Image object, was given object of type "
					+ obj.getClass().toString());
		} else {
			throw new IOException(this.getDataFlavors()[0].getMimeType() + " encoding not supported");
		}
	}
}