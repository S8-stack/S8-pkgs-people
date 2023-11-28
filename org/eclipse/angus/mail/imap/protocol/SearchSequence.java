package org.eclipse.angus.mail.imap.protocol;

import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.Flags.Flag;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.search.AddressTerm;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.BodyTerm;
import jakarta.mail.search.DateTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.FromTerm;
import jakarta.mail.search.HeaderTerm;
import jakarta.mail.search.MessageIDTerm;
import jakarta.mail.search.NotTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.RecipientStringTerm;
import jakarta.mail.search.RecipientTerm;
import jakarta.mail.search.SearchException;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SentDateTerm;
import jakarta.mail.search.SizeTerm;
import jakarta.mail.search.StringTerm;
import jakarta.mail.search.SubjectTerm;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import org.eclipse.angus.mail.iap.Argument;
import org.eclipse.angus.mail.imap.ModifiedSinceTerm;
import org.eclipse.angus.mail.imap.OlderTerm;
import org.eclipse.angus.mail.imap.YoungerTerm;

public class SearchSequence {
	private IMAPProtocol protocol;
	private static String[] monthTable = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep",
			"Oct", "Nov", "Dec"};
	protected Calendar cal = new GregorianCalendar();

	public SearchSequence(IMAPProtocol p) {
		this.protocol = p;
	}

	@Deprecated
	public SearchSequence() {
	}

	public Argument generateSequence(SearchTerm term, String charset) throws SearchException, IOException {
		if (term instanceof AndTerm) {
			return this.and((AndTerm) term, charset);
		} else if (term instanceof OrTerm) {
			return this.or((OrTerm) term, charset);
		} else if (term instanceof NotTerm) {
			return this.not((NotTerm) term, charset);
		} else if (term instanceof HeaderTerm) {
			return this.header((HeaderTerm) term, charset);
		} else if (term instanceof FlagTerm) {
			return this.flag((FlagTerm) term);
		} else if (term instanceof FromTerm) {
			FromTerm fterm = (FromTerm) term;
			return this.from(fterm.getAddress().toString(), charset);
		} else if (term instanceof FromStringTerm) {
			FromStringTerm fterm = (FromStringTerm) term;
			return this.from(fterm.getPattern(), charset);
		} else if (term instanceof RecipientTerm) {
			RecipientTerm rterm = (RecipientTerm) term;
			return this.recipient(rterm.getRecipientType(), rterm.getAddress().toString(), charset);
		} else if (term instanceof RecipientStringTerm) {
			RecipientStringTerm rterm = (RecipientStringTerm) term;
			return this.recipient(rterm.getRecipientType(), rterm.getPattern(), charset);
		} else if (term instanceof SubjectTerm) {
			return this.subject((SubjectTerm) term, charset);
		} else if (term instanceof BodyTerm) {
			return this.body((BodyTerm) term, charset);
		} else if (term instanceof SizeTerm) {
			return this.size((SizeTerm) term);
		} else if (term instanceof SentDateTerm) {
			return this.sentdate((SentDateTerm) term);
		} else if (term instanceof ReceivedDateTerm) {
			return this.receiveddate((ReceivedDateTerm) term);
		} else if (term instanceof OlderTerm) {
			return this.older((OlderTerm) term);
		} else if (term instanceof YoungerTerm) {
			return this.younger((YoungerTerm) term);
		} else if (term instanceof MessageIDTerm) {
			return this.messageid((MessageIDTerm) term, charset);
		} else if (term instanceof ModifiedSinceTerm) {
			return this.modifiedSince((ModifiedSinceTerm) term);
		} else {
			throw new SearchException("Search too complex");
		}
	}

	public static boolean isAscii(SearchTerm term) {
		if (term instanceof AndTerm) {
			return isAscii(((AndTerm) term).getTerms());
		} else if (term instanceof OrTerm) {
			return isAscii(((OrTerm) term).getTerms());
		} else if (term instanceof NotTerm) {
			return isAscii(((NotTerm) term).getTerm());
		} else if (term instanceof StringTerm) {
			return isAscii(((StringTerm) term).getPattern());
		} else {
			return term instanceof AddressTerm ? isAscii(((AddressTerm) term).getAddress().toString()) : true;
		}
	}

	public static boolean isAscii(SearchTerm[] terms) {
		for (int i = 0; i < terms.length; ++i) {
			if (!isAscii(terms[i])) {
				return false;
			}
		}

		return true;
	}

	public static boolean isAscii(String s) {
		int l = s.length();

		for (int i = 0; i < l; ++i) {
			if (s.charAt(i) > 127) {
				return false;
			}
		}

		return true;
	}

	protected Argument and(AndTerm term, String charset) throws SearchException, IOException {
		SearchTerm[] terms = term.getTerms();
		Argument result = this.generateSequence(terms[0], charset);

		for (int i = 1; i < terms.length; ++i) {
			result.append(this.generateSequence(terms[i], charset));
		}

		return result;
	}

	protected Argument or(OrTerm term, String charset) throws SearchException, IOException {
		SearchTerm[] terms = term.getTerms();
		if (terms.length > 2) {
			SearchTerm t = terms[0];

			for (int i = 1; i < terms.length; ++i) {
				t = new OrTerm((SearchTerm) t, terms[i]);
			}

			term = (OrTerm) t;
			terms = term.getTerms();
		}

		Argument result = new Argument();
		if (terms.length > 1) {
			result.writeAtom("OR");
		}

		if (!(terms[0] instanceof AndTerm) && !(terms[0] instanceof FlagTerm)) {
			result.append(this.generateSequence(terms[0], charset));
		} else {
			result.writeArgument(this.generateSequence(terms[0], charset));
		}

		if (terms.length > 1) {
			if (!(terms[1] instanceof AndTerm) && !(terms[1] instanceof FlagTerm)) {
				result.append(this.generateSequence(terms[1], charset));
			} else {
				result.writeArgument(this.generateSequence(terms[1], charset));
			}
		}

		return result;
	}

	protected Argument not(NotTerm term, String charset) throws SearchException, IOException {
		Argument result = new Argument();
		result.writeAtom("NOT");
		SearchTerm nterm = term.getTerm();
		if (!(nterm instanceof AndTerm) && !(nterm instanceof FlagTerm)) {
			result.append(this.generateSequence(nterm, charset));
		} else {
			result.writeArgument(this.generateSequence(nterm, charset));
		}

		return result;
	}

	protected Argument header(HeaderTerm term, String charset) throws SearchException, IOException {
		Argument result = new Argument();
		result.writeAtom("HEADER");
		result.writeString(term.getHeaderName());
		result.writeString(term.getPattern(), charset);
		return result;
	}

	protected Argument messageid(MessageIDTerm term, String charset) throws SearchException, IOException {
		Argument result = new Argument();
		result.writeAtom("HEADER");
		result.writeString("Message-ID");
		result.writeString(term.getPattern(), charset);
		return result;
	}

	protected Argument flag(FlagTerm term) throws SearchException {
		boolean set = term.getTestSet();
		Argument result = new Argument();
		Flags flags = term.getFlags();
		Flags.Flag[] sf = flags.getSystemFlags();
		String[] uf = flags.getUserFlags();
		if (sf.length == 0 && uf.length == 0) {
			throw new SearchException("Invalid FlagTerm");
		} else {
			int i;
			for (i = 0; i < sf.length; ++i) {
				if (sf[i] == Flag.DELETED) {
					result.writeAtom(set ? "DELETED" : "UNDELETED");
				} else if (sf[i] == Flag.ANSWERED) {
					result.writeAtom(set ? "ANSWERED" : "UNANSWERED");
				} else if (sf[i] == Flag.DRAFT) {
					result.writeAtom(set ? "DRAFT" : "UNDRAFT");
				} else if (sf[i] == Flag.FLAGGED) {
					result.writeAtom(set ? "FLAGGED" : "UNFLAGGED");
				} else if (sf[i] == Flag.RECENT) {
					result.writeAtom(set ? "RECENT" : "OLD");
				} else if (sf[i] == Flag.SEEN) {
					result.writeAtom(set ? "SEEN" : "UNSEEN");
				}
			}

			for (i = 0; i < uf.length; ++i) {
				result.writeAtom(set ? "KEYWORD" : "UNKEYWORD");
				result.writeAtom(uf[i]);
			}

			return result;
		}
	}

	protected Argument from(String address, String charset) throws SearchException, IOException {
		Argument result = new Argument();
		result.writeAtom("FROM");
		result.writeString(address, charset);
		return result;
	}

	protected Argument recipient(Message.RecipientType type, String address, String charset)
			throws SearchException, IOException {
		Argument result = new Argument();
		if (type == RecipientType.TO) {
			result.writeAtom("TO");
		} else if (type == RecipientType.CC) {
			result.writeAtom("CC");
		} else {
			if (type != RecipientType.BCC) {
				throw new SearchException("Illegal Recipient type");
			}

			result.writeAtom("BCC");
		}

		result.writeString(address, charset);
		return result;
	}

	protected Argument subject(SubjectTerm term, String charset) throws SearchException, IOException {
		Argument result = new Argument();
		result.writeAtom("SUBJECT");
		result.writeString(term.getPattern(), charset);
		return result;
	}

	protected Argument body(BodyTerm term, String charset) throws SearchException, IOException {
		Argument result = new Argument();
		result.writeAtom("BODY");
		result.writeString(term.getPattern(), charset);
		return result;
	}

	protected Argument size(SizeTerm term) throws SearchException {
		Argument result = new Argument();
		switch (term.getComparison()) {
			case 2 :
				result.writeAtom("SMALLER");
				break;
			case 5 :
				result.writeAtom("LARGER");
				break;
			default :
				throw new SearchException("Cannot handle Comparison");
		}

		result.writeNumber(term.getNumber());
		return result;
	}

	protected String toIMAPDate(Date date) {
		StringBuilder s = new StringBuilder();
		this.cal.setTime(date);
		s.append(this.cal.get(5)).append("-");
		s.append(monthTable[this.cal.get(2)]).append('-');
		s.append(this.cal.get(1));
		return s.toString();
	}

	protected Argument sentdate(DateTerm term) throws SearchException {
		Argument result = new Argument();
		String date = this.toIMAPDate(term.getDate());
		switch (term.getComparison()) {
			case 1 :
				result.writeAtom("OR SENTBEFORE " + date + " SENTON " + date);
				break;
			case 2 :
				result.writeAtom("SENTBEFORE " + date);
				break;
			case 3 :
				result.writeAtom("SENTON " + date);
				break;
			case 4 :
				result.writeAtom("NOT SENTON " + date);
				break;
			case 5 :
				result.writeAtom("NOT SENTON " + date + " SENTSINCE " + date);
				break;
			case 6 :
				result.writeAtom("SENTSINCE " + date);
				break;
			default :
				throw new SearchException("Cannot handle Date Comparison");
		}

		return result;
	}

	protected Argument receiveddate(DateTerm term) throws SearchException {
		Argument result = new Argument();
		String date = this.toIMAPDate(term.getDate());
		switch (term.getComparison()) {
			case 1 :
				result.writeAtom("OR BEFORE " + date + " ON " + date);
				break;
			case 2 :
				result.writeAtom("BEFORE " + date);
				break;
			case 3 :
				result.writeAtom("ON " + date);
				break;
			case 4 :
				result.writeAtom("NOT ON " + date);
				break;
			case 5 :
				result.writeAtom("NOT ON " + date + " SINCE " + date);
				break;
			case 6 :
				result.writeAtom("SINCE " + date);
				break;
			default :
				throw new SearchException("Cannot handle Date Comparison");
		}

		return result;
	}

	protected Argument older(OlderTerm term) throws SearchException {
		if (this.protocol != null && !this.protocol.hasCapability("WITHIN")) {
			throw new SearchException("Server doesn't support OLDER searches");
		} else {
			Argument result = new Argument();
			result.writeAtom("OLDER");
			result.writeNumber(term.getInterval());
			return result;
		}
	}

	protected Argument younger(YoungerTerm term) throws SearchException {
		if (this.protocol != null && !this.protocol.hasCapability("WITHIN")) {
			throw new SearchException("Server doesn't support YOUNGER searches");
		} else {
			Argument result = new Argument();
			result.writeAtom("YOUNGER");
			result.writeNumber(term.getInterval());
			return result;
		}
	}

	protected Argument modifiedSince(ModifiedSinceTerm term) throws SearchException {
		if (this.protocol != null && !this.protocol.hasCapability("CONDSTORE")) {
			throw new SearchException("Server doesn't support MODSEQ searches");
		} else {
			Argument result = new Argument();
			result.writeAtom("MODSEQ");
			result.writeNumber(term.getModSeq());
			return result;
		}
	}
}