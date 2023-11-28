package org.eclipse.angus.mail.handlers;

import jakarta.activation.ActivationDataFlavor;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.io.OutputStream;

public class multipart_mixed extends handler_base {
	private static ActivationDataFlavor[] myDF = new ActivationDataFlavor[]{
			new ActivationDataFlavor(Multipart.class, "multipart/mixed", "Multipart")};

	protected ActivationDataFlavor[] getDataFlavors() {
		return myDF;
	}

	public Object getContent(DataSource ds) throws IOException {
		try {
			return new MimeMultipart(ds);
		} catch (MessagingException var4) {
			IOException ioex = new IOException("Exception while constructing MimeMultipart");
			ioex.initCause(var4);
			throw ioex;
		}
	}

	public void writeTo(Object obj, String mimeType, OutputStream os) throws IOException {
		if (!(obj instanceof Multipart)) {
			throw new IOException("\"" + this.getDataFlavors()[0].getMimeType()
					+ "\" DataContentHandler requires Multipart object, was given object of type "
					+ obj.getClass().toString() + "; obj.cl " + obj.getClass().getClassLoader() + ", Multipart.cl "
					+ Multipart.class.getClassLoader());
		} else {
			try {
				((Multipart) obj).writeTo(os);
			} catch (MessagingException var6) {
				IOException ioex = new IOException("Exception writing Multipart");
				ioex.initCause(var6);
				throw ioex;
			}
		}
	}
}