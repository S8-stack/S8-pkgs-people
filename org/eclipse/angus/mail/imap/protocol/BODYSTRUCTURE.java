package org.eclipse.angus.mail.imap.protocol;

import jakarta.mail.internet.ParameterList;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.angus.mail.iap.ParsingException;
import org.eclipse.angus.mail.iap.Response;
import org.eclipse.angus.mail.util.PropUtil;

public class BODYSTRUCTURE implements Item {
	static final char[] name = new char[]{'B', 'O', 'D', 'Y', 'S', 'T', 'R', 'U', 'C', 'T', 'U', 'R', 'E'};
	public int msgno;
	public String type;
	public String subtype;
	public String encoding;
	public int lines = -1;
	public int size = -1;
	public String disposition;
	public String id;
	public String description;
	public String md5;
	public String attachment;
	public ParameterList cParams;
	public ParameterList dParams;
	public String[] language;
	public BODYSTRUCTURE[] bodies;
	public ENVELOPE envelope;
	private static int SINGLE = 1;
	private static int MULTI = 2;
	private static int NESTED = 3;
	private int processedType;
	private static final boolean parseDebug = PropUtil.getBooleanSystemProperty("mail.imap.parse.debug", false);

	public BODYSTRUCTURE(FetchResponse r) throws ParsingException {
		if (parseDebug) {
			System.out.println("DEBUG IMAP: parsing BODYSTRUCTURE");
		}

		this.msgno = r.getNumber();
		if (parseDebug) {
			System.out.println("DEBUG IMAP: msgno " + this.msgno);
		}

		r.skipSpaces();
		if (r.readByte() != 40) {
			throw new ParsingException("BODYSTRUCTURE parse error: missing ``('' at start");
		} else {
			if (r.peekByte() == 40) {
				if (parseDebug) {
					System.out.println("DEBUG IMAP: parsing multipart");
				}

				this.type = "multipart";
				this.processedType = MULTI;
				List<BODYSTRUCTURE> v = new ArrayList(1);

				do {
					v.add(new BODYSTRUCTURE(r));
					r.skipSpaces();
				} while (r.peekByte() == 40);

				this.bodies = (BODYSTRUCTURE[]) v.toArray(new BODYSTRUCTURE[0]);
				this.subtype = r.readString();
				if (parseDebug) {
					System.out.println("DEBUG IMAP: subtype " + this.subtype);
				}

				if (r.isNextNonSpace(')')) {
					if (parseDebug) {
						System.out.println("DEBUG IMAP: parse DONE");
					}

					return;
				}

				if (parseDebug) {
					System.out.println("DEBUG IMAP: parsing extension data");
				}

				this.cParams = this.parseParameters(r);
				if (r.isNextNonSpace(')')) {
					if (parseDebug) {
						System.out.println("DEBUG IMAP: body parameters DONE");
					}

					return;
				}

				byte b = r.peekByte();
				if (b == 40) {
					if (parseDebug) {
						System.out.println("DEBUG IMAP: parse disposition");
					}

					r.readByte();
					this.disposition = r.readString();
					if (parseDebug) {
						System.out.println("DEBUG IMAP: disposition " + this.disposition);
					}

					this.dParams = this.parseParameters(r);
					if (!r.isNextNonSpace(')')) {
						throw new ParsingException(
								"BODYSTRUCTURE parse error: missing ``)'' at end of disposition in multipart");
					}

					if (parseDebug) {
						System.out.println("DEBUG IMAP: disposition DONE");
					}
				} else {
					if (b != 78 && b != 110) {
						if (parseDebug) {
							System.out
									.println("DEBUG IMAP: bad multipart disposition, applying Exchange bug workaround");
						}

						this.description = r.readString();
						if (parseDebug) {
							System.out.println("DEBUG IMAP: multipart description " + this.description);
						}

						while (r.readByte() == 32) {
							this.parseBodyExtension(r);
						}

						return;
					}

					if (parseDebug) {
						System.out.println("DEBUG IMAP: disposition NIL");
					}

					r.skip(3);
				}

				if (r.isNextNonSpace(')')) {
					if (parseDebug) {
						System.out.println("DEBUG IMAP: no body-fld-lang");
					}

					return;
				}

				if (r.peekByte() == 40) {
					this.language = r.readStringList();
					if (parseDebug) {
						System.out.println("DEBUG IMAP: language len " + this.language.length);
					}
				} else {
					String l = r.readString();
					if (l != null) {
						String[] la = new String[]{l};
						this.language = la;
						if (parseDebug) {
							System.out.println("DEBUG IMAP: language " + l);
						}
					}
				}

				while (r.readByte() == 32) {
					this.parseBodyExtension(r);
				}
			} else {
				if (r.peekByte() == 41) {
					throw new ParsingException("BODYSTRUCTURE parse error: missing body content");
				}

				if (parseDebug) {
					System.out.println("DEBUG IMAP: single part");
				}

				this.type = r.readString();
				if (parseDebug) {
					System.out.println("DEBUG IMAP: type " + this.type);
				}

				this.processedType = SINGLE;
				this.subtype = r.readString();
				if (parseDebug) {
					System.out.println("DEBUG IMAP: subtype " + this.subtype);
				}

				if (this.type == null) {
					this.type = "application";
					this.subtype = "octet-stream";
				}

				this.cParams = this.parseParameters(r);
				if (parseDebug) {
					System.out.println("DEBUG IMAP: cParams " + this.cParams);
				}

				this.id = r.readString();
				if (parseDebug) {
					System.out.println("DEBUG IMAP: id " + this.id);
				}

				this.description = r.readString();
				if (parseDebug) {
					System.out.println("DEBUG IMAP: description " + this.description);
				}

				this.encoding = r.readAtomString();
				if (this.encoding != null && this.encoding.equalsIgnoreCase("NIL")) {
					if (parseDebug) {
						System.out.println("DEBUG IMAP: NIL encoding, applying Exchange bug workaround");
					}

					this.encoding = null;
				}

				if (this.encoding != null) {
					this.encoding = this.encoding.trim();
				}

				if (parseDebug) {
					System.out.println("DEBUG IMAP: encoding " + this.encoding);
				}

				this.size = r.readNumber();
				if (parseDebug) {
					System.out.println("DEBUG IMAP: size " + this.size);
				}

				if (this.size < 0) {
					throw new ParsingException("BODYSTRUCTURE parse error: bad ``size'' element");
				}

				byte b;
				if (this.type.equalsIgnoreCase("text")) {
					this.lines = r.readNumber();
					if (parseDebug) {
						System.out.println("DEBUG IMAP: lines " + this.lines);
					}

					if (this.lines < 0) {
						throw new ParsingException("BODYSTRUCTURE parse error: bad ``lines'' element");
					}
				} else if (this.type.equalsIgnoreCase("message") && this.subtype.equalsIgnoreCase("rfc822")) {
					this.processedType = NESTED;
					r.skipSpaces();
					if (r.peekByte() == 40) {
						this.envelope = new ENVELOPE(r);
						if (parseDebug) {
							System.out.println("DEBUG IMAP: got envelope of nested message");
						}

						BODYSTRUCTURE[] bs = new BODYSTRUCTURE[]{new BODYSTRUCTURE(r)};
						this.bodies = bs;
						this.lines = r.readNumber();
						if (parseDebug) {
							System.out.println("DEBUG IMAP: lines " + this.lines);
						}

						if (this.lines < 0) {
							throw new ParsingException("BODYSTRUCTURE parse error: bad ``lines'' element");
						}
					} else if (parseDebug) {
						System.out.println("DEBUG IMAP: missing envelope and body of nested message");
					}
				} else {
					r.skipSpaces();
					b = r.peekByte();
					if (Character.isDigit((char) b)) {
						throw new ParsingException(
								"BODYSTRUCTURE parse error: server erroneously included ``lines'' element with type "
										+ this.type + "/" + this.subtype);
					}
				}

				if (r.isNextNonSpace(')')) {
					if (parseDebug) {
						System.out.println("DEBUG IMAP: parse DONE");
					}

					return;
				}

				this.md5 = r.readString();
				if (r.isNextNonSpace(')')) {
					if (parseDebug) {
						System.out.println("DEBUG IMAP: no MD5 DONE");
					}

					return;
				}

				b = r.readByte();
				if (b == 40) {
					this.disposition = r.readString();
					if (parseDebug) {
						System.out.println("DEBUG IMAP: disposition " + this.disposition);
					}

					this.dParams = this.parseParameters(r);
					if (parseDebug) {
						System.out.println("DEBUG IMAP: dParams " + this.dParams);
					}

					if (!r.isNextNonSpace(')')) {
						throw new ParsingException("BODYSTRUCTURE parse error: missing ``)'' at end of disposition");
					}
				} else {
					if (b != 78 && b != 110) {
						throw new ParsingException("BODYSTRUCTURE parse error: " + this.type + "/" + this.subtype
								+ ": bad single part disposition, b " + b);
					}

					if (parseDebug) {
						System.out.println("DEBUG IMAP: disposition NIL");
					}

					r.skip(2);
				}

				if (r.isNextNonSpace(')')) {
					if (parseDebug) {
						System.out.println("DEBUG IMAP: disposition DONE");
					}

					return;
				}

				if (r.peekByte() == 40) {
					this.language = r.readStringList();
					if (parseDebug) {
						System.out.println("DEBUG IMAP: language len " + this.language.length);
					}
				} else {
					String l = r.readString();
					if (l != null) {
						String[] la = new String[]{l};
						this.language = la;
						if (parseDebug) {
							System.out.println("DEBUG IMAP: language " + l);
						}
					}
				}

				while (r.readByte() == 32) {
					this.parseBodyExtension(r);
				}

				if (parseDebug) {
					System.out.println("DEBUG IMAP: all DONE");
				}
			}

		}
	}

	public boolean isMulti() {
		return this.processedType == MULTI;
	}

	public boolean isSingle() {
		return this.processedType == SINGLE;
	}

	public boolean isNested() {
		return this.processedType == NESTED;
	}

	private ParameterList parseParameters(Response r) throws ParsingException {
		r.skipSpaces();
		ParameterList list = null;
		byte b = r.readByte();
		if (b == 40) {
			list = new ParameterList();

			do {
				String name = r.readString();
				if (parseDebug) {
					System.out.println("DEBUG IMAP: parameter name " + name);
				}

				if (name == null) {
					throw new ParsingException("BODYSTRUCTURE parse error: " + this.type + "/" + this.subtype
							+ ": null name in parameter list");
				}

				String value = r.readString();
				if (parseDebug) {
					System.out.println("DEBUG IMAP: parameter value " + value);
				}

				if (value == null) {
					if (parseDebug) {
						System.out.println("DEBUG IMAP: NIL parameter value, applying Exchange bug workaround");
					}

					value = "";
				}

				list.set(name, value);
			} while (!r.isNextNonSpace(')'));

			list.combineSegments();
		} else {
			if (b != 78 && b != 110) {
				throw new ParsingException("Parameter list parse error");
			}

			if (parseDebug) {
				System.out.println("DEBUG IMAP: parameter list NIL");
			}

			r.skip(2);
		}

		return list;
	}

	private void parseBodyExtension(Response r) throws ParsingException {
		r.skipSpaces();
		byte b = r.peekByte();
		if (b == 40) {
			r.skip(1);

			do {
				this.parseBodyExtension(r);
			} while (!r.isNextNonSpace(')'));
		} else if (Character.isDigit((char) b)) {
			r.readNumber();
		} else {
			r.readString();
		}

	}
}