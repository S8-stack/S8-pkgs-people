package org.eclipse.angus.mail.handlers;

import jakarta.activation.ActivationDataFlavor;
import jakarta.activation.DataSource;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessageAware;
import jakarta.mail.MessageContext;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

public class message_rfc822 extends handler_base {
	private static ActivationDataFlavor[] ourDataFlavor = new ActivationDataFlavor[]{
			new ActivationDataFlavor(Message.class, "message/rfc822", "Message")};

	protected ActivationDataFlavor[] getDataFlavors() {
		return ourDataFlavor;
	}

	public Object getContent(DataSource ds) throws IOException {
		try {
			Session session;
			if (ds instanceof MessageAware) {
				MessageContext mc = ((MessageAware) ds).getMessageContext();
				session = mc.getSession();
			} else {
				session = Session.getDefaultInstance(new Properties(), (Authenticator) null);
			}

			return new MimeMessage(session, ds.getInputStream());
		} catch (MessagingException var4) {
			IOException ioex = new IOException("Exception creating MimeMessage in message/rfc822 DataContentHandler");
			ioex.initCause(var4);
			throw ioex;
		}
	}

	public void writeTo(Object obj, String mimeType, OutputStream os) throws IOException {
		if (!(obj instanceof Message)) {
			throw new IOException("\"" + this.getDataFlavors()[0].getMimeType()
					+ "\" DataContentHandler requires Message object, was given object of type "
					+ obj.getClass().toString() + "; obj.cl " + obj.getClass().getClassLoader() + ", Message.cl "
					+ Message.class.getClassLoader());
		} else {
			Message m = (Message) obj;

			try {
				m.writeTo(os);
			} catch (MessagingException var7) {
				IOException ioex = new IOException("Exception writing message");
				ioex.initCause(var7);
				throw ioex;
			}
		}
	}
}