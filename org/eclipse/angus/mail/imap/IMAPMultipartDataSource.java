package org.eclipse.angus.mail.imap;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.MultipartDataSource;
import jakarta.mail.internet.MimePart;
import jakarta.mail.internet.MimePartDataSource;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.angus.mail.imap.protocol.BODYSTRUCTURE;

public class IMAPMultipartDataSource extends MimePartDataSource implements MultipartDataSource {
	private List<IMAPBodyPart> parts;

	protected IMAPMultipartDataSource(MimePart part, BODYSTRUCTURE[] bs, String sectionId, IMAPMessage msg) {
		super(part);
		this.parts = new ArrayList(bs.length);

		for (int i = 0; i < bs.length; ++i) {
			this.parts.add(new IMAPBodyPart(bs[i],
					sectionId == null ? Integer.toString(i + 1) : sectionId + "." + Integer.toString(i + 1), msg));
		}

	}

	public int getCount() {
		return this.parts.size();
	}

	public BodyPart getBodyPart(int index) throws MessagingException {
		return (BodyPart) this.parts.get(index);
	}
}