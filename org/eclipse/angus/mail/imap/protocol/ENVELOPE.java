package org.eclipse.angus.mail.imap.protocol;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MailDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.eclipse.angus.mail.iap.ParsingException;
import org.eclipse.angus.mail.iap.Response;
import org.eclipse.angus.mail.util.PropUtil;

public class ENVELOPE implements Item {
	static final char[] name = new char[]{'E', 'N', 'V', 'E', 'L', 'O', 'P', 'E'};
	public int msgno;
	public Date date = null;
	public String subject;
	public InternetAddress[] from;
	public InternetAddress[] sender;
	public InternetAddress[] replyTo;
	public InternetAddress[] to;
	public InternetAddress[] cc;
	public InternetAddress[] bcc;
	public String inReplyTo;
	public String messageId;
	private static final MailDateFormat mailDateFormat = new MailDateFormat();
	private static final boolean parseDebug = PropUtil.getBooleanSystemProperty("mail.imap.parse.debug", false);

	public ENVELOPE(FetchResponse r) throws ParsingException {
		if (parseDebug) {
			System.out.println("parse ENVELOPE");
		}

		this.msgno = r.getNumber();
		r.skipSpaces();
		if (r.readByte() != 40) {
			throw new ParsingException("ENVELOPE parse error");
		} else {
			String s = r.readString();
			if (s != null) {
				try {
					synchronized (mailDateFormat) {
						this.date = mailDateFormat.parse(s);
					}
				} catch (ParseException var6) {
				}
			}

			if (parseDebug) {
				System.out.println("  Date: " + this.date);
			}

			this.subject = r.readString();
			if (parseDebug) {
				System.out.println("  Subject: " + this.subject);
			}

			if (parseDebug) {
				System.out.println("  From addresses:");
			}

			this.from = this.parseAddressList(r);
			if (parseDebug) {
				System.out.println("  Sender addresses:");
			}

			this.sender = this.parseAddressList(r);
			if (parseDebug) {
				System.out.println("  Reply-To addresses:");
			}

			this.replyTo = this.parseAddressList(r);
			if (parseDebug) {
				System.out.println("  To addresses:");
			}

			this.to = this.parseAddressList(r);
			if (parseDebug) {
				System.out.println("  Cc addresses:");
			}

			this.cc = this.parseAddressList(r);
			if (parseDebug) {
				System.out.println("  Bcc addresses:");
			}

			this.bcc = this.parseAddressList(r);
			this.inReplyTo = r.readString();
			if (parseDebug) {
				System.out.println("  In-Reply-To: " + this.inReplyTo);
			}

			this.messageId = r.readString();
			if (parseDebug) {
				System.out.println("  Message-ID: " + this.messageId);
			}

			if (!r.isNextNonSpace(')')) {
				throw new ParsingException("ENVELOPE parse error");
			}
		}
	}

	private InternetAddress[] parseAddressList(Response r) throws ParsingException {
		r.skipSpaces();
		byte b = r.readByte();
		if (b == 40) {
			if (r.isNextNonSpace(')')) {
				return null;
			} else {
				List<InternetAddress> v = new ArrayList();

				do {
					IMAPAddress a = new IMAPAddress(r);
					if (parseDebug) {
						System.out.println("    Address: " + a);
					}

					if (!a.isEndOfGroup()) {
						v.add(a);
					}
				} while (!r.isNextNonSpace(')'));

				return (InternetAddress[]) v.toArray(new InternetAddress[0]);
			}
		} else if (b != 78 && b != 110) {
			throw new ParsingException("ADDRESS parse error");
		} else {
			r.skip(2);
			return null;
		}
	}
}