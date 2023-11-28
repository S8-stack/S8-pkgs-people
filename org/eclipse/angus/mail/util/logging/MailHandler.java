package org.eclipse.angus.mail.util.logging;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileTypeMap;
import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessageContext;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.SendFailedException;
import jakarta.mail.Service;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimePart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.mail.util.StreamProvider.EncoderTypes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class MailHandler extends Handler {
	private static final Filter[] EMPTY_FILTERS = new Filter[0];
	private static final Formatter[] EMPTY_FORMATTERS = new Formatter[0];
	private static final int MIN_HEADER_SIZE = 1024;
	private static final int offValue;
	private static final PrivilegedAction<Object> MAILHANDLER_LOADER;
	private static final ThreadLocal<Integer> MUTEX;
	private static final Integer MUTEX_PUBLISH;
	private static final Integer MUTEX_REPORT;
	private static final Integer MUTEX_LINKAGE;
	private volatile boolean sealed;
	private boolean isWriting;
	private Properties mailProps;
	private Authenticator auth;
	private Session session;
	private int[] matched;
	private LogRecord[] data;
	private int size;
	private int capacity;
	private Comparator<? super LogRecord> comparator;
	private Formatter subjectFormatter;
	private Level pushLevel;
	private Filter pushFilter;
	private volatile Filter filter;
	private volatile Level logLevel;
	private volatile Filter[] attachmentFilters;
	private String encoding;
	private Formatter formatter;
	private Formatter[] attachmentFormatters;
	private Formatter[] attachmentNames;
	private FileTypeMap contentTypes;
	private volatile ErrorManager errorManager;

	public MailHandler() {
		this.logLevel = Level.ALL;
		this.errorManager = this.defaultErrorManager();
		this.init((Properties) null);
		this.sealed = true;
		this.checkAccess();
	}

	public MailHandler(int capacity) {
		this.logLevel = Level.ALL;
		this.errorManager = this.defaultErrorManager();
		this.init((Properties) null);
		this.sealed = true;
		this.setCapacity0(capacity);
	}

	public MailHandler(Properties props) {
		this.logLevel = Level.ALL;
		this.errorManager = this.defaultErrorManager();
		if (props == null) {
			throw new NullPointerException();
		} else {
			this.init(props);
			this.sealed = true;
			this.setMailProperties0(props);
		}
	}

	public boolean isLoggable(LogRecord record) {
		if (record == null) {
			return false;
		} else {
			int levelValue = this.getLevel().intValue();
			if (record.getLevel().intValue() >= levelValue && levelValue != offValue) {
				Filter body = this.getFilter();
				if (body != null && !body.isLoggable(record)) {
					return this.isAttachmentLoggable(record);
				} else {
					this.setMatchedPart(-1);
					return true;
				}
			} else {
				return false;
			}
		}
	}

	public void publish(LogRecord record) {
		if (this.tryMutex()) {
			try {
				if (this.isLoggable(record)) {
					if (record != null) {
						record.getSourceMethodName();
						this.publish0(record);
					} else {
						this.reportNullError(1);
					}
				}
			} catch (LinkageError var6) {
				this.reportLinkageError(var6, 1);
			} finally {
				this.releaseMutex();
			}
		} else {
			this.reportUnPublishedError(record);
		}

	}

	private void publish0(LogRecord record) {
		Message msg;
		boolean priority;
		synchronized (this) {
			if (this.size == this.data.length && this.size < this.capacity) {
				this.grow();
			}

			if (this.size < this.data.length) {
				this.matched[this.size] = this.getMatchedPart();
				this.data[this.size] = record;
				++this.size;
				priority = this.isPushable(record);
				if (!priority && this.size < this.capacity) {
					msg = null;
				} else {
					msg = this.writeLogRecords(1);
				}
			} else {
				priority = false;
				msg = null;
			}
		}

		if (msg != null) {
			this.send(msg, priority, 1);
		}

	}

	private void reportUnPublishedError(LogRecord record) {
		Integer idx = (Integer) MUTEX.get();
		if (idx == null || idx > MUTEX_REPORT) {
			MUTEX.set(MUTEX_REPORT);

			try {
				String msg;
				if (record != null) {
					Formatter f = createSimpleFormatter();
					msg = "Log record " + record.getSequenceNumber() + " was not published. " + this.head(f)
							+ this.format(f, record) + this.tail(f, "");
				} else {
					msg = null;
				}

				Exception e = new IllegalStateException(
						"Recursive publish detected by thread " + Thread.currentThread());
				this.reportError((String) msg, e, 1);
			} finally {
				if (idx != null) {
					MUTEX.set(idx);
				} else {
					MUTEX.remove();
				}

			}
		}

	}

	private boolean tryMutex() {
		if (MUTEX.get() == null) {
			MUTEX.set(MUTEX_PUBLISH);
			return true;
		} else {
			return false;
		}
	}

	private void releaseMutex() {
		MUTEX.remove();
	}

	private int getMatchedPart() {
		Integer idx = (Integer) MUTEX.get();
		if (idx == null || idx >= this.readOnlyAttachmentFilters().length) {
			idx = MUTEX_PUBLISH;
		}

		return idx;
	}

	private void setMatchedPart(int index) {
		if (MUTEX_PUBLISH.equals(MUTEX.get())) {
			MUTEX.set(index);
		}

	}

	private void clearMatches(int index) {
		assert Thread.holdsLock(this);

		for (int r = 0; r < this.size; ++r) {
			if (this.matched[r] >= index) {
				this.matched[r] = MUTEX_PUBLISH;
			}
		}

	}

	public void postConstruct() {
	}

	public void preDestroy() {
		this.push(false, 3);
	}

	public void push() {
		this.push(true, 2);
	}

	public void flush() {
		this.push(false, 2);
	}

	public void close() {
		try {
			this.checkAccess();
			Message msg = null;
			synchronized (this) {
				try {
					msg = this.writeLogRecords(3);
				} finally {
					this.logLevel = Level.OFF;
					if (this.capacity > 0) {
						this.capacity = -this.capacity;
					}

					if (this.size == 0 && this.data.length != 1) {
						this.data = new LogRecord[1];
						this.matched = new int[this.data.length];
					}

				}
			}

			if (msg != null) {
				this.send(msg, false, 3);
			}
		} catch (LinkageError var10) {
			this.reportLinkageError(var10, 3);
		}

	}

	public void setLevel(Level newLevel) {
		if (newLevel == null) {
			throw new NullPointerException();
		} else {
			this.checkAccess();
			synchronized (this) {
				if (this.capacity > 0) {
					this.logLevel = newLevel;
				}

			}
		}
	}

	public Level getLevel() {
		return this.logLevel;
	}

	public ErrorManager getErrorManager() {
		this.checkAccess();
		return this.errorManager;
	}

	public void setErrorManager(ErrorManager em) {
		this.checkAccess();
		this.setErrorManager0(em);
	}

	private void setErrorManager0(ErrorManager em) {
		if (em == null) {
			throw new NullPointerException();
		} else {
			try {
				synchronized (this) {
					this.errorManager = em;
					super.setErrorManager(em);
				}
			} catch (LinkageError | RuntimeException var5) {
			}

		}
	}

	public Filter getFilter() {
		return this.filter;
	}

	public void setFilter(Filter newFilter) {
		this.checkAccess();
		synchronized (this) {
			if (newFilter != this.filter) {
				this.clearMatches(-1);
			}

			this.filter = newFilter;
		}
	}

	public synchronized String getEncoding() {
		return this.encoding;
	}

	public void setEncoding(String encoding) throws UnsupportedEncodingException {
		this.checkAccess();
		this.setEncoding0(encoding);
	}

	private void setEncoding0(String e) throws UnsupportedEncodingException {
		if (e != null) {
			try {
				if (!Charset.isSupported(e)) {
					throw new UnsupportedEncodingException(e);
				}
			} catch (IllegalCharsetNameException var5) {
				throw new UnsupportedEncodingException(e);
			}
		}

		synchronized (this) {
			this.encoding = e;
		}
	}

	public synchronized Formatter getFormatter() {
		return this.formatter;
	}

	public synchronized void setFormatter(Formatter newFormatter) throws SecurityException {
		this.checkAccess();
		if (newFormatter == null) {
			throw new NullPointerException();
		} else {
			this.formatter = newFormatter;
		}
	}

	public final synchronized Level getPushLevel() {
		return this.pushLevel;
	}

	public final synchronized void setPushLevel(Level level) {
		this.checkAccess();
		if (level == null) {
			throw new NullPointerException();
		} else if (this.isWriting) {
			throw new IllegalStateException();
		} else {
			this.pushLevel = level;
		}
	}

	public final synchronized Filter getPushFilter() {
		return this.pushFilter;
	}

	public final synchronized void setPushFilter(Filter filter) {
		this.checkAccess();
		if (this.isWriting) {
			throw new IllegalStateException();
		} else {
			this.pushFilter = filter;
		}
	}

	public final synchronized Comparator<? super LogRecord> getComparator() {
		return this.comparator;
	}

	public final synchronized void setComparator(Comparator<? super LogRecord> c) {
		this.checkAccess();
		if (this.isWriting) {
			throw new IllegalStateException();
		} else {
			this.comparator = c;
		}
	}

	public final synchronized int getCapacity() {
		assert this.capacity != Integer.MIN_VALUE && this.capacity != 0 : this.capacity;

		return Math.abs(this.capacity);
	}

	public final synchronized Authenticator getAuthenticator() {
		this.checkAccess();
		return this.auth;
	}

	public final void setAuthenticator(Authenticator auth) {
		this.setAuthenticator0(auth);
	}

	public final void setAuthenticator(char... password) {
		if (password == null) {
			this.setAuthenticator0((Authenticator) null);
		} else {
			this.setAuthenticator0(MailHandler.DefaultAuthenticator.of(new String(password)));
		}

	}

	private void setAuthenticator0(Authenticator auth) {
		this.checkAccess();
		Session settings;
		synchronized (this) {
			if (this.isWriting) {
				throw new IllegalStateException();
			}

			this.auth = auth;
			settings = this.updateSession();
		}

		this.verifySettings(settings);
	}

	public final void setMailProperties(Properties props) {
		this.setMailProperties0(props);
	}

	private void setMailProperties0(Properties props) {
		this.checkAccess();
		props = (Properties) props.clone();
		Session settings;
		synchronized (this) {
			if (this.isWriting) {
				throw new IllegalStateException();
			}

			this.mailProps = props;
			settings = this.updateSession();
		}

		this.verifySettings(settings);
	}

	public final Properties getMailProperties() {
		this.checkAccess();
		Properties props;
		synchronized (this) {
			props = this.mailProps;
		}

		return (Properties) props.clone();
	}

	public final Filter[] getAttachmentFilters() {
		return (Filter[]) this.readOnlyAttachmentFilters().clone();
	}

	public final void setAttachmentFilters(Filter... filters) {
		this.checkAccess();
		if (filters.length == 0) {
			filters = emptyFilterArray();
		} else {
			filters = (Filter[]) Arrays.copyOf(filters, filters.length, Filter[].class);
		}

		synchronized (this) {
			if (this.attachmentFormatters.length != filters.length) {
				throw attachmentMismatch(this.attachmentFormatters.length, filters.length);
			} else if (this.isWriting) {
				throw new IllegalStateException();
			} else {
				if (this.size != 0) {
					for (int i = 0; i < filters.length; ++i) {
						if (filters[i] != this.attachmentFilters[i]) {
							this.clearMatches(i);
							break;
						}
					}
				}

				this.attachmentFilters = filters;
			}
		}
	}

	public final Formatter[] getAttachmentFormatters() {
		Formatter[] formatters;
		synchronized (this) {
			formatters = this.attachmentFormatters;
		}

		return (Formatter[]) formatters.clone();
	}

	public final void setAttachmentFormatters(Formatter... formatters) {
		this.checkAccess();
		if (formatters.length == 0) {
			formatters = emptyFormatterArray();
		} else {
			formatters = (Formatter[]) Arrays.copyOf(formatters, formatters.length, Formatter[].class);

			for (int i = 0; i < formatters.length; ++i) {
				if (formatters[i] == null) {
					throw new NullPointerException(atIndexMsg(i));
				}
			}
		}

		synchronized (this) {
			if (this.isWriting) {
				throw new IllegalStateException();
			} else {
				this.attachmentFormatters = formatters;
				this.alignAttachmentFilters();
				this.alignAttachmentNames();
			}
		}
	}

	public final Formatter[] getAttachmentNames() {
		Formatter[] formatters;
		synchronized (this) {
			formatters = this.attachmentNames;
		}

		return (Formatter[]) formatters.clone();
	}

	public final void setAttachmentNames(String... names) {
		this.checkAccess();
		Formatter[] formatters;
		if (names.length == 0) {
			formatters = emptyFormatterArray();
		} else {
			formatters = new Formatter[names.length];
		}

		for (int i = 0; i < names.length; ++i) {
			String name = names[i];
			if (name == null) {
				throw new NullPointerException(atIndexMsg(i));
			}

			if (name.length() <= 0) {
				throw new IllegalArgumentException(atIndexMsg(i));
			}

			formatters[i] = MailHandler.TailNameFormatter.of(name);
		}

		synchronized (this) {
			if (this.attachmentFormatters.length != names.length) {
				throw attachmentMismatch(this.attachmentFormatters.length, names.length);
			} else if (this.isWriting) {
				throw new IllegalStateException();
			} else {
				this.attachmentNames = formatters;
			}
		}
	}

	public final void setAttachmentNames(Formatter... formatters) {
		this.checkAccess();
		if (formatters.length == 0) {
			formatters = emptyFormatterArray();
		} else {
			formatters = (Formatter[]) Arrays.copyOf(formatters, formatters.length, Formatter[].class);
		}

		for (int i = 0; i < formatters.length; ++i) {
			if (formatters[i] == null) {
				throw new NullPointerException(atIndexMsg(i));
			}
		}

		synchronized (this) {
			if (this.attachmentFormatters.length != formatters.length) {
				throw attachmentMismatch(this.attachmentFormatters.length, formatters.length);
			} else if (this.isWriting) {
				throw new IllegalStateException();
			} else {
				this.attachmentNames = formatters;
			}
		}
	}

	public final synchronized Formatter getSubject() {
		return this.subjectFormatter;
	}

	public final void setSubject(String subject) {
		if (subject != null) {
			this.setSubject(MailHandler.TailNameFormatter.of(subject));
		} else {
			this.checkAccess();
			throw new NullPointerException();
		}
	}

	public final void setSubject(Formatter format) {
		this.checkAccess();
		if (format == null) {
			throw new NullPointerException();
		} else {
			synchronized (this) {
				if (this.isWriting) {
					throw new IllegalStateException();
				} else {
					this.subjectFormatter = format;
				}
			}
		}
	}

	protected void reportError(String msg, Exception ex, int code) {
		try {
			if (msg != null) {
				this.errorManager.error(Level.SEVERE.getName().concat(": ").concat(msg), ex, code);
			} else {
				this.errorManager.error((String) null, ex, code);
			}
		} catch (LinkageError | RuntimeException var5) {
			this.reportLinkageError(var5, code);
		}

	}

	private void checkAccess() {
		if (this.sealed) {
			LogManagerProperties.checkLogManagerAccess();
		}

	}

	final String contentTypeOf(CharSequence chunk) {
		if (!isEmpty(chunk)) {
			int MAX_CHARS = true;
			if (chunk.length() > 25) {
				chunk = chunk.subSequence(0, 25);
			}

			try {
				String charset = this.getEncodingName();
				byte[] b = chunk.toString().getBytes(charset);
				ByteArrayInputStream in = new ByteArrayInputStream(b);

				assert in.markSupported() : in.getClass().getName();

				return URLConnection.guessContentTypeFromStream(in);
			} catch (IOException var6) {
				this.reportError((String) var6.getMessage(), var6, 5);
			}
		}

		return null;
	}

	final String contentTypeOf(Formatter f) {
		assert Thread.holdsLock(this);

		if (f != null) {
			String type = this.getContentType(f.getClass().getName());
			if (type != null) {
				return type;
			}

			for (Class<?> k = f.getClass(); k != Formatter.class; k = k.getSuperclass()) {
				String name;
				try {
					name = k.getSimpleName();
				} catch (InternalError var6) {
					name = k.getName();
				}

				name = name.toLowerCase(Locale.ENGLISH);

				for (int idx = name.indexOf(36) + 1; (idx = name.indexOf("ml", idx)) > -1; idx += 2) {
					if (idx > 0) {
						if (name.charAt(idx - 1) == 'x') {
							return "application/xml";
						}

						if (idx > 1 && name.charAt(idx - 2) == 'h' && name.charAt(idx - 1) == 't') {
							return "text/html";
						}
					}
				}
			}
		}

		return null;
	}

	final boolean isMissingContent(Message msg, Throwable t) {
		Object ccl = this.getAndSetContextClassLoader(MAILHANDLER_LOADER);

		try {
			msg.writeTo(new ByteArrayOutputStream(1024));
			return false;
		} catch (RuntimeException var12) {
			throw var12;
		} catch (Exception var13) {
			Exception noContent = var13;
			String txt = var13.getMessage();
			if (isEmpty(txt)) {
				return false;
			} else {
				int limit = 0;

				do {
					if (t == null) {
						return false;
					}

					if (noContent.getClass() == t.getClass() && txt.equals(((Throwable) t).getMessage())) {
						boolean var15 = true;
						return var15;
					}

					Throwable cause = ((Throwable) t).getCause();
					if (cause == null && t instanceof MessagingException) {
						t = ((MessagingException) t).getNextException();
					} else {
						t = cause;
					}

					++limit;
				} while (limit != 65536);

				return false;
			}
		} finally {
			this.getAndSetContextClassLoader(ccl);
		}
	}

	private void reportError(Message msg, Exception ex, int code) {
		try {
			try {
				this.errorManager.error(this.toRawString(msg), ex, code);
			} catch (Exception var5) {
				this.reportError(this.toMsgString(var5), ex, code);
			}
		} catch (LinkageError var6) {
			this.reportLinkageError(var6, code);
		}

	}

	private void reportLinkageError(Throwable le, int code) {
		if (le == null) {
			throw new NullPointerException(String.valueOf(code));
		} else {
			Integer idx = (Integer) MUTEX.get();
			if (idx == null || idx > MUTEX_LINKAGE) {
				MUTEX.set(MUTEX_LINKAGE);

				try {
					Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), le);
				} catch (LinkageError | RuntimeException var8) {
				} finally {
					if (idx != null) {
						MUTEX.set(idx);
					} else {
						MUTEX.remove();
					}

				}
			}

		}
	}

	private String getContentType(String name) {
		assert Thread.holdsLock(this);

		String type = this.contentTypes.getContentType(name);
		return "application/octet-stream".equalsIgnoreCase(type) ? null : type;
	}

	private String getEncodingName() {
		String charset = this.getEncoding();
		if (charset == null) {
			charset = MimeUtility.getDefaultJavaCharset();
		}

		return charset;
	}

	private void setContent(MimePart part, CharSequence buf, String type) throws MessagingException {
		String charset = this.getEncodingName();
		if (type != null && !"text/plain".equalsIgnoreCase(type)) {
			type = this.contentWithEncoding(type, charset);

			try {
				DataSource source = new ByteArrayDataSource(buf.toString(), type);
				part.setDataHandler(new DataHandler(source));
			} catch (IOException var6) {
				this.reportError((String) var6.getMessage(), var6, 5);
				part.setText(buf.toString(), charset);
			}
		} else {
			part.setText(buf.toString(), MimeUtility.mimeCharset(charset));
		}

	}

	private String contentWithEncoding(String type, String encoding) {
		assert encoding != null;

		try {
			ContentType ct = new ContentType(type);
			ct.setParameter("charset", MimeUtility.mimeCharset(encoding));
			encoding = ct.toString();
			if (!isEmpty(encoding)) {
				type = encoding;
			}
		} catch (MessagingException var4) {
			this.reportError((String) type, var4, 5);
		}

		return type;
	}

	private synchronized void setCapacity0(int newCapacity) {
		this.checkAccess();
		if (newCapacity <= 0) {
			throw new IllegalArgumentException("Capacity must be greater than zero.");
		} else if (this.isWriting) {
			throw new IllegalStateException();
		} else {
			if (this.capacity < 0) {
				this.capacity = -newCapacity;
			} else {
				this.capacity = newCapacity;
			}

		}
	}

	private Filter[] readOnlyAttachmentFilters() {
		return this.attachmentFilters;
	}

	private static Formatter[] emptyFormatterArray() {
		return EMPTY_FORMATTERS;
	}

	private static Filter[] emptyFilterArray() {
		return EMPTY_FILTERS;
	}

	private boolean alignAttachmentNames() {
		assert Thread.holdsLock(this);

		boolean fixed = false;
		int expect = this.attachmentFormatters.length;
		int current = this.attachmentNames.length;
		if (current != expect) {
			this.attachmentNames = (Formatter[]) Arrays.copyOf(this.attachmentNames, expect, Formatter[].class);
			fixed = current != 0;
		}

		if (expect == 0) {
			this.attachmentNames = emptyFormatterArray();

			assert this.attachmentNames.length == 0;
		} else {
			for (int i = 0; i < expect; ++i) {
				if (this.attachmentNames[i] == null) {
					this.attachmentNames[i] = MailHandler.TailNameFormatter
							.of(this.toString(this.attachmentFormatters[i]));
				}
			}
		}

		return fixed;
	}

	private boolean alignAttachmentFilters() {
		assert Thread.holdsLock(this);

		boolean fixed = false;
		int expect = this.attachmentFormatters.length;
		int current = this.attachmentFilters.length;
		if (current != expect) {
			this.attachmentFilters = (Filter[]) Arrays.copyOf(this.attachmentFilters, expect, Filter[].class);
			this.clearMatches(current);
			fixed = current != 0;
			Filter body = this.filter;
			if (body != null) {
				for (int i = current; i < expect; ++i) {
					this.attachmentFilters[i] = body;
				}
			}
		}

		if (expect == 0) {
			this.attachmentFilters = emptyFilterArray();

			assert this.attachmentFilters.length == 0;
		}

		return fixed;
	}

	private void reset() {
		assert Thread.holdsLock(this);

		if (this.size < this.data.length) {
			Arrays.fill(this.data, 0, this.size, (Object) null);
		} else {
			Arrays.fill(this.data, (Object) null);
		}

		this.size = 0;
	}

	private void grow() {
		assert Thread.holdsLock(this);

		int len = this.data.length;
		int newCapacity = len + (len >> 1) + 1;
		if (newCapacity > this.capacity || newCapacity < len) {
			newCapacity = this.capacity;
		}

		assert len != this.capacity : len;

		this.data = (LogRecord[]) Arrays.copyOf(this.data, newCapacity, LogRecord[].class);
		this.matched = Arrays.copyOf(this.matched, newCapacity);
	}

	private synchronized void init(Properties props) {
		assert this.errorManager != null;

		String p = this.getClass().getName();
		this.mailProps = new Properties();
		Object ccl = this.getAndSetContextClassLoader(MAILHANDLER_LOADER);

		try {
			this.contentTypes = FileTypeMap.getDefaultFileTypeMap();
		} finally {
			this.getAndSetContextClassLoader(ccl);
		}

		this.initErrorManager(p);
		this.initLevel(p);
		this.initFilter(p);
		this.initCapacity(p);
		this.initAuthenticator(p);
		this.initEncoding(p);
		this.initFormatter(p);
		this.initComparator(p);
		this.initPushLevel(p);
		this.initPushFilter(p);
		this.initSubject(p);
		this.initAttachmentFormaters(p);
		this.initAttachmentFilters(p);
		this.initAttachmentNames(p);
		if (props == null && LogManagerProperties.fromLogManager(p.concat(".verify")) != null) {
			this.verifySettings(this.initSession());
		}

		this.intern();
	}

	private void intern() {
		assert Thread.holdsLock(this);

		try {
			Map<Object, Object> seen = new HashMap();

			try {
				this.intern(seen, this.errorManager);
			} catch (SecurityException var6) {
				this.reportError((String) var6.getMessage(), var6, 4);
			}

			Filter canidate;
			Object result;
			Formatter canidate;
			try {
				canidate = this.filter;
				result = this.intern(seen, canidate);
				if (result != canidate && result instanceof Filter) {
					this.filter = (Filter) result;
				}

				canidate = this.formatter;
				result = this.intern(seen, canidate);
				if (result != canidate && result instanceof Formatter) {
					this.formatter = (Formatter) result;
				}
			} catch (SecurityException var5) {
				this.reportError((String) var5.getMessage(), var5, 4);
			}

			canidate = this.subjectFormatter;
			result = this.intern(seen, canidate);
			if (result != canidate && result instanceof Formatter) {
				this.subjectFormatter = (Formatter) result;
			}

			canidate = this.pushFilter;
			result = this.intern(seen, canidate);
			if (result != canidate && result instanceof Filter) {
				this.pushFilter = (Filter) result;
			}

			for (int i = 0; i < this.attachmentFormatters.length; ++i) {
				canidate = this.attachmentFormatters[i];
				result = this.intern(seen, canidate);
				if (result != canidate && result instanceof Formatter) {
					this.attachmentFormatters[i] = (Formatter) result;
				}

				canidate = this.attachmentFilters[i];
				result = this.intern(seen, canidate);
				if (result != canidate && result instanceof Filter) {
					this.attachmentFilters[i] = (Filter) result;
				}

				canidate = this.attachmentNames[i];
				result = this.intern(seen, canidate);
				if (result != canidate && result instanceof Formatter) {
					this.attachmentNames[i] = (Formatter) result;
				}
			}
		} catch (Exception var7) {
			this.reportError((String) var7.getMessage(), var7, 4);
		} catch (LinkageError var8) {
			this.reportError((String) var8.getMessage(), new InvocationTargetException(var8), 4);
		}

	}

	private Object intern(Map<Object, Object> m, Object o) throws Exception {
		if (o == null) {
			return null;
		} else {
			Object key;
			if (o.getClass().getName().equals(TailNameFormatter.class.getName())) {
				key = o;
			} else {
				key = o.getClass().getConstructor().newInstance();
			}

			Object use;
			if (key.getClass() == o.getClass()) {
				Object found = m.get(key);
				if (found == null) {
					boolean right = key.equals(o);
					boolean left = o.equals(key);
					if (right && left) {
						found = m.put(o, o);
						if (found != null) {
							this.reportNonDiscriminating(key, found);
							found = m.remove(key);
							if (found != o) {
								this.reportNonDiscriminating(key, found);
								m.clear();
							}
						}
					} else if (right != left) {
						this.reportNonSymmetric(o, key);
					}

					use = o;
				} else if (o.getClass() == found.getClass()) {
					use = found;
				} else {
					this.reportNonDiscriminating(o, found);
					use = o;
				}
			} else {
				use = o;
			}

			return use;
		}
	}

	private static Formatter createSimpleFormatter() {
		return (Formatter) Formatter.class.cast(new SimpleFormatter());
	}

	private static boolean isEmpty(CharSequence s) {
		return s == null || s.length() == 0;
	}

	private static boolean hasValue(String name) {
		return !isEmpty(name) && !"null".equalsIgnoreCase(name);
	}

	private void initAttachmentFilters(String p) {
		assert Thread.holdsLock(this);

		assert this.attachmentFormatters != null;

		String list = LogManagerProperties.fromLogManager(p.concat(".attachment.filters"));
		if (!isEmpty(list)) {
			String[] names = list.split(",");
			Filter[] a = new Filter[names.length];

			for (int i = 0; i < a.length; ++i) {
				names[i] = names[i].trim();
				if (!"null".equalsIgnoreCase(names[i])) {
					try {
						a[i] = LogManagerProperties.newFilter(names[i]);
					} catch (SecurityException var7) {
						throw var7;
					} catch (Exception var8) {
						this.reportError((String) var8.getMessage(), var8, 4);
					}
				}
			}

			this.attachmentFilters = a;
			if (this.alignAttachmentFilters()) {
				this.reportError((String) "Attachment filters.", attachmentMismatch("Length mismatch."), 4);
			}
		} else {
			this.attachmentFilters = emptyFilterArray();
			this.alignAttachmentFilters();
		}

	}

	private void initAttachmentFormaters(String p) {
		assert Thread.holdsLock(this);

		String list = LogManagerProperties.fromLogManager(p.concat(".attachment.formatters"));
		if (!isEmpty(list)) {
			String[] names = list.split(",");
			Formatter[] a;
			if (names.length == 0) {
				a = emptyFormatterArray();
			} else {
				a = new Formatter[names.length];
			}

			for (int i = 0; i < a.length; ++i) {
				names[i] = names[i].trim();
				if (!"null".equalsIgnoreCase(names[i])) {
					try {
						a[i] = LogManagerProperties.newFormatter(names[i]);
						if (a[i] instanceof TailNameFormatter) {
							Exception CNFE = new ClassNotFoundException(a[i].toString());
							this.reportError((String) "Attachment formatter.", CNFE, 4);
							a[i] = createSimpleFormatter();
						}
					} catch (SecurityException var7) {
						throw var7;
					} catch (Exception var8) {
						this.reportError((String) var8.getMessage(), var8, 4);
						a[i] = createSimpleFormatter();
					}
				} else {
					Exception NPE = new NullPointerException(atIndexMsg(i));
					this.reportError((String) "Attachment formatter.", NPE, 4);
					a[i] = createSimpleFormatter();
				}
			}

			this.attachmentFormatters = a;
		} else {
			this.attachmentFormatters = emptyFormatterArray();
		}

	}

	private void initAttachmentNames(String p) {
		assert Thread.holdsLock(this);

		assert this.attachmentFormatters != null;

		String list = LogManagerProperties.fromLogManager(p.concat(".attachment.names"));
		if (!isEmpty(list)) {
			String[] names = list.split(",");
			Formatter[] a = new Formatter[names.length];

			for (int i = 0; i < a.length; ++i) {
				names[i] = names[i].trim();
				if (!"null".equalsIgnoreCase(names[i])) {
					try {
						try {
							a[i] = LogManagerProperties.newFormatter(names[i]);
						} catch (ClassCastException | ClassNotFoundException var7) {
							a[i] = MailHandler.TailNameFormatter.of(names[i]);
						}
					} catch (SecurityException var8) {
						throw var8;
					} catch (Exception var9) {
						this.reportError((String) var9.getMessage(), var9, 4);
					}
				} else {
					Exception NPE = new NullPointerException(atIndexMsg(i));
					this.reportError((String) "Attachment names.", NPE, 4);
				}
			}

			this.attachmentNames = a;
			if (this.alignAttachmentNames()) {
				this.reportError((String) "Attachment names.", attachmentMismatch("Length mismatch."), 4);
			}
		} else {
			this.attachmentNames = emptyFormatterArray();
			this.alignAttachmentNames();
		}

	}

	private void initAuthenticator(String p) {
		assert Thread.holdsLock(this);

		String name = LogManagerProperties.fromLogManager(p.concat(".authenticator"));
		if (name != null && !"null".equalsIgnoreCase(name)) {
			if (name.length() != 0) {
				try {
					this.auth = (Authenticator) LogManagerProperties.newObjectFrom(name, Authenticator.class);
				} catch (SecurityException var4) {
					throw var4;
				} catch (ClassCastException | ClassNotFoundException var5) {
					this.auth = MailHandler.DefaultAuthenticator.of(name);
				} catch (Exception var6) {
					this.reportError((String) var6.getMessage(), var6, 4);
				}
			} else {
				this.auth = MailHandler.DefaultAuthenticator.of(name);
			}
		}

	}

	private void initLevel(String p) {
		assert Thread.holdsLock(this);

		try {
			String val = LogManagerProperties.fromLogManager(p.concat(".level"));
			if (val != null) {
				this.logLevel = Level.parse(val);
			} else {
				this.logLevel = Level.WARNING;
			}
		} catch (SecurityException var3) {
			throw var3;
		} catch (RuntimeException var4) {
			this.reportError((String) var4.getMessage(), var4, 4);
			this.logLevel = Level.WARNING;
		}

	}

	private void initFilter(String p) {
		assert Thread.holdsLock(this);

		try {
			String name = LogManagerProperties.fromLogManager(p.concat(".filter"));
			if (hasValue(name)) {
				this.filter = LogManagerProperties.newFilter(name);
			}
		} catch (SecurityException var3) {
			throw var3;
		} catch (Exception var4) {
			this.reportError((String) var4.getMessage(), var4, 4);
		}

	}

	private void initCapacity(String p) {
		assert Thread.holdsLock(this);

		int DEFAULT_CAPACITY = true;

		try {
			String value = LogManagerProperties.fromLogManager(p.concat(".capacity"));
			if (value != null) {
				this.setCapacity0(Integer.parseInt(value));
			} else {
				this.setCapacity0(1000);
			}
		} catch (SecurityException var4) {
			throw var4;
		} catch (RuntimeException var5) {
			this.reportError((String) var5.getMessage(), var5, 4);
		}

		if (this.capacity <= 0) {
			this.capacity = 1000;
		}

		this.data = new LogRecord[1];
		this.matched = new int[this.data.length];
	}

	private void initEncoding(String p) {
		assert Thread.holdsLock(this);

		try {
			String e = LogManagerProperties.fromLogManager(p.concat(".encoding"));
			if (e != null) {
				this.setEncoding0(e);
			}
		} catch (SecurityException var3) {
			throw var3;
		} catch (RuntimeException | UnsupportedEncodingException var4) {
			this.reportError((String) var4.getMessage(), var4, 4);
		}

	}

	private ErrorManager defaultErrorManager() {
		ErrorManager em;
		try {
			em = super.getErrorManager();
		} catch (LinkageError | RuntimeException var3) {
			em = null;
		}

		if (em == null) {
			em = new ErrorManager();
		}

		return em;
	}

	private void initErrorManager(String p) {
		assert Thread.holdsLock(this);

		try {
			String name = LogManagerProperties.fromLogManager(p.concat(".errorManager"));
			if (name != null) {
				this.setErrorManager0(LogManagerProperties.newErrorManager(name));
			}
		} catch (SecurityException var3) {
			throw var3;
		} catch (Exception var4) {
			this.reportError((String) var4.getMessage(), var4, 4);
		}

	}

	private void initFormatter(String p) {
		assert Thread.holdsLock(this);

		try {
			String name = LogManagerProperties.fromLogManager(p.concat(".formatter"));
			if (hasValue(name)) {
				Formatter f = LogManagerProperties.newFormatter(name);

				assert f != null;

				if (!(f instanceof TailNameFormatter)) {
					this.formatter = f;
				} else {
					this.formatter = createSimpleFormatter();
				}
			} else {
				this.formatter = createSimpleFormatter();
			}
		} catch (SecurityException var4) {
			throw var4;
		} catch (Exception var5) {
			this.reportError((String) var5.getMessage(), var5, 4);
			this.formatter = createSimpleFormatter();
		}

	}

	private void initComparator(String p) {
		assert Thread.holdsLock(this);

		try {
			String name = LogManagerProperties.fromLogManager(p.concat(".comparator"));
			String reverse = LogManagerProperties.fromLogManager(p.concat(".comparator.reverse"));
			if (hasValue(name)) {
				this.comparator = LogManagerProperties.newComparator(name);
				if (Boolean.parseBoolean(reverse)) {
					assert this.comparator != null : "null";

					this.comparator = LogManagerProperties.reverseOrder(this.comparator);
				}
			} else if (!isEmpty(reverse)) {
				throw new IllegalArgumentException("No comparator to reverse.");
			}
		} catch (SecurityException var4) {
			throw var4;
		} catch (Exception var5) {
			this.reportError((String) var5.getMessage(), var5, 4);
		}

	}

	private void initPushLevel(String p) {
		assert Thread.holdsLock(this);

		try {
			String val = LogManagerProperties.fromLogManager(p.concat(".pushLevel"));
			if (val != null) {
				this.pushLevel = Level.parse(val);
			}
		} catch (RuntimeException var3) {
			this.reportError((String) var3.getMessage(), var3, 4);
		}

		if (this.pushLevel == null) {
			this.pushLevel = Level.OFF;
		}

	}

	private void initPushFilter(String p) {
		assert Thread.holdsLock(this);

		try {
			String name = LogManagerProperties.fromLogManager(p.concat(".pushFilter"));
			if (hasValue(name)) {
				this.pushFilter = LogManagerProperties.newFilter(name);
			}
		} catch (SecurityException var3) {
			throw var3;
		} catch (Exception var4) {
			this.reportError((String) var4.getMessage(), var4, 4);
		}

	}

	private void initSubject(String p) {
		assert Thread.holdsLock(this);

		String name = LogManagerProperties.fromLogManager(p.concat(".subject"));
		if (name == null) {
			name = "org.eclipse.angus.mail.util.logging.CollectorFormatter";
		}

		if (hasValue(name)) {
			try {
				this.subjectFormatter = LogManagerProperties.newFormatter(name);
			} catch (SecurityException var4) {
				throw var4;
			} catch (ClassCastException | ClassNotFoundException var5) {
				this.subjectFormatter = MailHandler.TailNameFormatter.of(name);
			} catch (Exception var6) {
				this.subjectFormatter = MailHandler.TailNameFormatter.of(name);
				this.reportError((String) var6.getMessage(), var6, 4);
			}
		} else {
			this.subjectFormatter = MailHandler.TailNameFormatter.of(name);
		}

	}

	private boolean isAttachmentLoggable(LogRecord record) {
		Filter[] filters = this.readOnlyAttachmentFilters();

		for (int i = 0; i < filters.length; ++i) {
			Filter f = filters[i];
			if (f == null || f.isLoggable(record)) {
				this.setMatchedPart(i);
				return true;
			}
		}

		return false;
	}

	private boolean isPushable(LogRecord record) {
		assert Thread.holdsLock(this);

		int value = this.getPushLevel().intValue();
		if (value != offValue && record.getLevel().intValue() >= value) {
			Filter push = this.getPushFilter();
			if (push == null) {
				return true;
			} else {
				int match = this.getMatchedPart();
				return (match != -1 || this.getFilter() != push) && (match < 0 || this.attachmentFilters[match] != push)
						? push.isLoggable(record)
						: true;
			}
		} else {
			return false;
		}
	}

	private void push(boolean priority, int code) {
		if (this.tryMutex()) {
			try {
				Message msg = this.writeLogRecords(code);
				if (msg != null) {
					this.send(msg, priority, code);
				}
			} catch (LinkageError var7) {
				this.reportLinkageError(var7, code);
			} finally {
				this.releaseMutex();
			}
		} else {
			this.reportUnPublishedError((LogRecord) null);
		}

	}

	private void send(Message msg, boolean priority, int code) {
		try {
			this.envelopeFor(msg, priority);
			Object ccl = this.getAndSetContextClassLoader(MAILHANDLER_LOADER);

			try {
				Transport.send(msg);
			} finally {
				this.getAndSetContextClassLoader(ccl);
			}
		} catch (Exception var9) {
			this.reportError(msg, var9, code);
		}

	}

	private void sort() {
		assert Thread.holdsLock(this);

		if (this.comparator != null) {
			try {
				if (this.size != 1) {
					Arrays.sort(this.data, 0, this.size, this.comparator);
				} else if (this.comparator.compare(this.data[0], this.data[0]) != 0) {
					throw new IllegalArgumentException(this.comparator.getClass().getName());
				}
			} catch (RuntimeException var2) {
				this.reportError((String) var2.getMessage(), var2, 5);
			}
		}

	}

	private Message writeLogRecords(int code) {
		try {
			synchronized (this) {
				if (this.size > 0 && !this.isWriting) {
					this.isWriting = true;

					Message var3;
					try {
						var3 = this.writeLogRecords0();
					} finally {
						this.isWriting = false;
						if (this.size > 0) {
							this.reset();
						}

					}

					return var3;
				}
			}
		} catch (Exception var11) {
			this.reportError(var11.getMessage(), var11, code);
		}

		return null;
	}

	private Message writeLogRecords0() throws Exception {
		assert Thread.holdsLock(this);

		this.sort();
		if (this.session == null) {
			this.initSession();
		}

		MimeMessage msg = new MimeMessage(this.session);
		MimeBodyPart[] parts = new MimeBodyPart[this.attachmentFormatters.length];
		StringBuilder[] buffers = new StringBuilder[parts.length];
		StringBuilder buf = null;
		Object body;
		if (parts.length == 0) {
			msg.setDescription(this.descriptionFrom(this.getFormatter(), this.getFilter(), this.subjectFormatter));
			body = msg;
		} else {
			msg.setDescription(this.descriptionFrom(this.comparator, this.pushLevel, this.pushFilter));
			body = this.createBodyPart();
		}

		this.appendSubject(msg, this.head(this.subjectFormatter));
		Formatter bodyFormat = this.getFormatter();
		Filter bodyFilter = this.getFilter();
		Locale lastLocale = null;

		int i;
		for (i = 0; i < this.size; ++i) {
			boolean formatted = false;
			int match = this.matched[i];
			LogRecord r = this.data[i];
			this.data[i] = null;
			Locale locale = this.localeFor(r);
			this.appendSubject(msg, this.format(this.subjectFormatter, r));
			Filter lmf = null;
			if (bodyFilter == null || match == -1 || parts.length == 0 || match < -1 && bodyFilter.isLoggable(r)) {
				lmf = bodyFilter;
				if (buf == null) {
					buf = new StringBuilder();
					buf.append(this.head(bodyFormat));
				}

				formatted = true;
				buf.append(this.format(bodyFormat, r));
				if (locale != null && !locale.equals(lastLocale)) {
					this.appendContentLang((MimePart) body, locale);
				}
			}

			for (int i = 0; i < parts.length; ++i) {
				Filter af = this.attachmentFilters[i];
				if (af == null || lmf == af || match == i || match < i && af.isLoggable(r)) {
					if (lmf == null && af != null) {
						lmf = af;
					}

					if (parts[i] == null) {
						parts[i] = this.createBodyPart(i);
						buffers[i] = new StringBuilder();
						buffers[i].append(this.head(this.attachmentFormatters[i]));
						this.appendFileName(parts[i], this.head(this.attachmentNames[i]));
					}

					formatted = true;
					this.appendFileName(parts[i], this.format(this.attachmentNames[i], r));
					buffers[i].append(this.format(this.attachmentFormatters[i], r));
					if (locale != null && !locale.equals(lastLocale)) {
						this.appendContentLang(parts[i], locale);
					}
				}
			}

			if (formatted) {
				if (body != msg && locale != null && !locale.equals(lastLocale)) {
					this.appendContentLang(msg, locale);
				}
			} else {
				this.reportFilterError(r);
			}

			lastLocale = locale;
		}

		this.size = 0;

		String name;
		for (i = parts.length - 1; i >= 0; --i) {
			if (parts[i] != null) {
				this.appendFileName(parts[i], this.tail(this.attachmentNames[i], "err"));
				buffers[i].append(this.tail(this.attachmentFormatters[i], ""));
				if (buffers[i].length() > 0) {
					name = parts[i].getFileName();
					if (isEmpty(name)) {
						name = this.toString(this.attachmentFormatters[i]);
						parts[i].setFileName(name);
					}

					this.setContent(parts[i], buffers[i], this.getContentType(name));
				} else {
					this.setIncompleteCopy(msg);
					parts[i] = null;
				}

				buffers[i] = null;
			}
		}

		if (buf != null) {
			buf.append(this.tail(bodyFormat, ""));
		} else {
			buf = new StringBuilder(0);
		}

		this.appendSubject(msg, this.tail(this.subjectFormatter, ""));
		String contentType = this.contentTypeOf((CharSequence) buf);
		name = this.contentTypeOf(bodyFormat);
		this.setContent((MimePart) body, buf, name == null ? contentType : name);
		if (body != msg) {
			MimeMultipart multipart = new MimeMultipart();
			multipart.addBodyPart((BodyPart) body);

			for (int i = 0; i < parts.length; ++i) {
				if (parts[i] != null) {
					multipart.addBodyPart(parts[i]);
				}
			}

			msg.setContent(multipart);
		}

		return msg;
	}

	private void verifySettings(Session session) {
		try {
			if (session != null) {
				Properties props = session.getProperties();
				Object check = props.put("verify", "");
				if (check instanceof String) {
					String value = (String) check;
					if (hasValue(value)) {
						this.verifySettings0(session, value);
					}
				} else if (check != null) {
					this.verifySettings0(session, check.getClass().toString());
				}
			}
		} catch (LinkageError var5) {
			this.reportLinkageError(var5, 4);
		}

	}

	private void verifySettings0(Session session, String verify) {
		assert verify != null : (String) null;

		if (!"local".equals(verify) && !"remote".equals(verify) && !"limited".equals(verify)
				&& !"resolve".equals(verify) && !"login".equals(verify)) {
			this.reportError((String) "Verify must be 'limited', local', 'resolve', 'login', or 'remote'.",
					new IllegalArgumentException(verify), 4);
		} else {
			MimeMessage abort = new MimeMessage(session);
			String msg;
			if (!"limited".equals(verify)) {
				msg = "Local address is " + InternetAddress.getLocalAddress(session) + '.';

				try {
					Charset.forName(this.getEncodingName());
				} catch (RuntimeException var70) {
					UnsupportedEncodingException UEE = new UnsupportedEncodingException(var70.toString());
					UEE.initCause(var70);
					this.reportError((String) msg, UEE, 5);
				}
			} else {
				msg = "Skipping local address check.";
			}

			String[] atn;
			synchronized (this) {
				this.appendSubject(abort, this.head(this.subjectFormatter));
				this.appendSubject(abort, this.tail(this.subjectFormatter, ""));
				atn = new String[this.attachmentNames.length];
				int i = 0;

				while (true) {
					if (i >= atn.length) {
						break;
					}

					atn[i] = this.head(this.attachmentNames[i]);
					if (atn[i].length() == 0) {
						atn[i] = this.tail(this.attachmentNames[i], "");
					} else {
						atn[i] = atn[i].concat(this.tail(this.attachmentNames[i], ""));
					}

					++i;
				}
			}

			this.setIncompleteCopy(abort);
			this.envelopeFor(abort, true);
			this.saveChangesNoContent(abort, msg);

			try {
				Address[] all = abort.getAllRecipients();
				if (all == null) {
					all = new InternetAddress[0];
				}

				Object ccl;
				Transport t;
				MessagingException closed;
				try {
					Address[] any = ((Object[]) all).length != 0 ? all : abort.getFrom();
					if (any == null || ((Object[]) any).length == 0) {
						closed = new MessagingException("No recipient or from address.");
						this.reportError((String) msg, closed, 4);
						throw closed;
					}

					t = session.getTransport((Address) ((Object[]) any)[0]);
					session.getProperty("mail.transport.protocol");
				} catch (MessagingException var77) {
					MessagingException protocol = var77;
					ccl = this.getAndSetContextClassLoader(MAILHANDLER_LOADER);

					try {
						t = session.getTransport();
					} catch (MessagingException var68) {
						throw attach(protocol, var68);
					} finally {
						this.getAndSetContextClassLoader(ccl);
					}
				}

				String local = null;
				String mailHost;
				MessagingException ME;
				if (!"remote".equals(verify) && !"login".equals(verify)) {
					String protocol = t.getURLName().getProtocol();
					verifyProperties(session, protocol);
					mailHost = session.getProperty("mail." + protocol + ".host");
					if (isEmpty(mailHost)) {
						mailHost = session.getProperty("mail.host");
					} else {
						session.getProperty("mail.host");
					}

					local = session.getProperty("mail." + protocol + ".localhost");
					if (isEmpty(local)) {
						local = session.getProperty("mail." + protocol + ".localaddress");
					} else {
						session.getProperty("mail." + protocol + ".localaddress");
					}

					if ("resolve".equals(verify)) {
						try {
							String transportHost = t.getURLName().getHost();
							if (!isEmpty(transportHost)) {
								verifyHost(transportHost);
								if (!transportHost.equalsIgnoreCase(mailHost)) {
									verifyHost(mailHost);
								}
							} else {
								verifyHost(mailHost);
							}
						} catch (IOException | RuntimeException var66) {
							ME = new MessagingException(msg, var66);
							this.setErrorContent(abort, verify, ME);
							this.reportError((Message) abort, ME, 4);
						}
					}
				} else {
					closed = null;
					t.connect();

					try {
						try {
							local = this.getLocalHost(t);
							if ("remote".equals(verify)) {
								t.sendMessage(abort, (Address[]) all);
							}
						} finally {
							try {
								t.close();
							} catch (MessagingException var67) {
								closed = var67;
							}

						}

						if ("remote".equals(verify)) {
							this.reportUnexpectedSend(abort, verify, (Exception) null);
						} else {
							mailHost = t.getURLName().getProtocol();
							verifyProperties(session, mailHost);
						}
					} catch (SendFailedException var75) {
						Address[] recip = var75.getInvalidAddresses();
						if (recip != null && recip.length != 0) {
							this.setErrorContent(abort, verify, var75);
							this.reportError((Message) abort, var75, 4);
						}

						recip = var75.getValidSentAddresses();
						if (recip != null && recip.length != 0) {
							this.reportUnexpectedSend(abort, verify, var75);
						}
					} catch (MessagingException var76) {
						if (!this.isMissingContent(abort, var76)) {
							this.setErrorContent(abort, verify, var76);
							this.reportError((Message) abort, var76, 4);
						}
					}

					if (closed != null) {
						this.setErrorContent(abort, verify, closed);
						this.reportError((Message) abort, closed, 3);
					}
				}

				if (!"limited".equals(verify)) {
					MessagingException ME;
					try {
						if (!"remote".equals(verify) && !"login".equals(verify)) {
							local = this.getLocalHost(t);
						}

						verifyHost(local);
					} catch (IOException | RuntimeException var65) {
						ME = new MessagingException(msg, var65);
						this.setErrorContent(abort, verify, ME);
						this.reportError((Message) abort, ME, 4);
					}

					try {
						ccl = this.getAndSetContextClassLoader(MAILHANDLER_LOADER);

						try {
							MimeMultipart multipart = new MimeMultipart();
							MimeBodyPart[] ambp = new MimeBodyPart[atn.length];
							String bodyContentType;
							MimeBodyPart body;
							synchronized (this) {
								bodyContentType = this.contentTypeOf(this.getFormatter());
								body = this.createBodyPart();
								int i = 0;

								while (true) {
									if (i >= atn.length) {
										break;
									}

									ambp[i] = this.createBodyPart(i);
									ambp[i].setFileName(atn[i]);
									atn[i] = this.getContentType(atn[i]);
									++i;
								}
							}

							body.setDescription(verify);
							this.setContent(body, "", bodyContentType);
							multipart.addBodyPart(body);

							for (int i = 0; i < ambp.length; ++i) {
								ambp[i].setDescription(verify);
								this.setContent(ambp[i], "", atn[i]);
							}

							abort.setContent(multipart);
							abort.saveChanges();
							abort.writeTo(new ByteArrayOutputStream(1024));
						} finally {
							this.getAndSetContextClassLoader(ccl);
						}
					} catch (IOException var73) {
						ME = new MessagingException(msg, var73);
						this.setErrorContent(abort, verify, ME);
						this.reportError((Message) abort, ME, 5);
					}
				}

				if (((Object[]) all).length == 0) {
					throw new MessagingException("No recipient addresses.");
				}

				verifyAddresses((Address[]) all);
				Address[] from = abort.getFrom();
				Address sender = abort.getSender();
				if (sender instanceof InternetAddress) {
					((InternetAddress) sender).validate();
				}

				if (abort.getHeader("From", ",") != null && from.length != 0) {
					verifyAddresses(from);

					for (int i = 0; i < from.length; ++i) {
						if (from[i].equals(sender)) {
							ME = new MessagingException("Sender address '" + sender + "' equals from address.");
							throw new MessagingException(msg, ME);
						}
					}
				} else if (sender == null) {
					MessagingException ME = new MessagingException("No from or sender address.");
					throw new MessagingException(msg, ME);
				}

				verifyAddresses(abort.getReplyTo());
			} catch (Exception var78) {
				this.setErrorContent(abort, verify, var78);
				this.reportError((Message) abort, var78, 4);
			}

		}
	}

	private void saveChangesNoContent(Message abort, String msg) {
		if (abort != null) {
			try {
				try {
					abort.saveChanges();
				} catch (NullPointerException var6) {
					NullPointerException xferEncoding = var6;

					try {
						String cte = "Content-Transfer-Encoding";
						if (abort.getHeader(cte) != null) {
							throw xferEncoding;
						}

						abort.setHeader(cte, EncoderTypes.BASE_64.getEncoder());
						abort.saveChanges();
					} catch (MessagingException | RuntimeException var5) {
						if (var5 != var6) {
							var5.addSuppressed(var6);
						}

						throw var5;
					}
				}
			} catch (MessagingException | RuntimeException var7) {
				this.reportError((String) msg, var7, 5);
			}
		}

	}

	private static void verifyProperties(Session session, String protocol) {
		session.getProperty("mail.from");
		session.getProperty("mail." + protocol + ".from");
		session.getProperty("mail.dsn.ret");
		session.getProperty("mail." + protocol + ".dsn.ret");
		session.getProperty("mail.dsn.notify");
		session.getProperty("mail." + protocol + ".dsn.notify");
		session.getProperty("mail." + protocol + ".port");
		session.getProperty("mail.user");
		session.getProperty("mail." + protocol + ".user");
		session.getProperty("mail." + protocol + ".localport");
	}

	private static InetAddress verifyHost(String host) throws IOException {
		InetAddress a;
		if (isEmpty(host)) {
			a = InetAddress.getLocalHost();
		} else {
			a = InetAddress.getByName(host);
		}

		if (a.getCanonicalHostName().length() == 0) {
			throw new UnknownHostException();
		} else {
			return a;
		}
	}

	private static void verifyAddresses(Address[] all) throws AddressException {
		if (all != null) {
			for (int i = 0; i < all.length; ++i) {
				Address a = all[i];
				if (a instanceof InternetAddress) {
					((InternetAddress) a).validate();
				}
			}
		}

	}

	private void reportUnexpectedSend(MimeMessage msg, String verify, Exception cause) {
		MessagingException write = new MessagingException("An empty message was sent.", cause);
		this.setErrorContent(msg, verify, write);
		this.reportError((Message) msg, write, 4);
	}

	private void setErrorContent(MimeMessage msg, String verify, Throwable t) {
		try {
			MimeBodyPart body;
			String subjectType;
			String msgDesc;
			synchronized (this) {
				body = this.createBodyPart();
				msgDesc = this.descriptionFrom(this.comparator, this.pushLevel, this.pushFilter);
				subjectType = this.getClassId(this.subjectFormatter);
			}

			body.setDescription("Formatted using " + (t == null ? Throwable.class.getName() : t.getClass().getName())
					+ ", filtered with " + verify + ", and named by " + subjectType + '.');
			this.setContent(body, this.toMsgString(t), "text/plain");
			MimeMultipart multipart = new MimeMultipart();
			multipart.addBodyPart(body);
			msg.setContent(multipart);
			msg.setDescription(msgDesc);
			this.setAcceptLang(msg);
			msg.saveChanges();
		} catch (RuntimeException | MessagingException var10) {
			this.reportError((String) "Unable to create body.", var10, 4);
		}

	}

	private Session updateSession() {
		assert Thread.holdsLock(this);

		Session settings;
		if (this.mailProps.getProperty("verify") != null) {
			settings = this.initSession();

			assert settings == this.session : this.session;
		} else {
			this.session = null;
			settings = null;
		}

		return settings;
	}

	private Session initSession() {
		assert Thread.holdsLock(this);

		String p = this.getClass().getName();
		LogManagerProperties proxy = new LogManagerProperties(this.mailProps, p);
		this.session = Session.getInstance(proxy, this.auth);
		return this.session;
	}

	private void envelopeFor(Message msg, boolean priority) {
		this.setAcceptLang(msg);
		this.setFrom(msg);
		if (!this.setRecipient(msg, "mail.to", RecipientType.TO)) {
			this.setDefaultRecipient(msg, RecipientType.TO);
		}

		this.setRecipient(msg, "mail.cc", RecipientType.CC);
		this.setRecipient(msg, "mail.bcc", RecipientType.BCC);
		this.setReplyTo(msg);
		this.setSender(msg);
		this.setMailer(msg);
		this.setAutoSubmitted(msg);
		if (priority) {
			this.setPriority(msg);
		}

		try {
			msg.setSentDate(new Date());
		} catch (MessagingException var4) {
			this.reportError((String) var4.getMessage(), var4, 5);
		}

	}

	private MimeBodyPart createBodyPart() throws MessagingException {
		assert Thread.holdsLock(this);

		MimeBodyPart part = new MimeBodyPart();
		part.setDisposition("inline");
		part.setDescription(this.descriptionFrom(this.getFormatter(), this.getFilter(), this.subjectFormatter));
		this.setAcceptLang(part);
		return part;
	}

	private MimeBodyPart createBodyPart(int index) throws MessagingException {
		assert Thread.holdsLock(this);

		MimeBodyPart part = new MimeBodyPart();
		part.setDisposition("attachment");
		part.setDescription(this.descriptionFrom(this.attachmentFormatters[index], this.attachmentFilters[index],
				this.attachmentNames[index]));
		this.setAcceptLang(part);
		return part;
	}

	private String descriptionFrom(Comparator<?> c, Level l, Filter f) {
		return "Sorted using " + (c == null ? "no comparator" : c.getClass().getName()) + ", pushed when " + l.getName()
				+ ", and " + (f == null ? "no push filter" : f.getClass().getName()) + '.';
	}

	private String descriptionFrom(Formatter f, Filter filter, Formatter name) {
		return "Formatted using " + this.getClassId(f) + ", filtered with "
				+ (filter == null ? "no filter" : filter.getClass().getName()) + ", and named by "
				+ this.getClassId(name) + '.';
	}

	private String getClassId(Formatter f) {
		return f instanceof TailNameFormatter ? String.class.getName() : f.getClass().getName();
	}

	private String toString(Formatter f) {
		String name = f.toString();
		return !isEmpty(name) ? name : this.getClassId(f);
	}

	private void appendFileName(Part part, String chunk) {
		if (chunk != null) {
			if (chunk.length() > 0) {
				this.appendFileName0(part, chunk);
			}
		} else {
			this.reportNullError(5);
		}

	}

	private void appendFileName0(Part part, String chunk) {
		try {
			chunk = chunk.replaceAll("[\\x00-\\x1F\\x7F]+", "");
			String old = part.getFileName();
			part.setFileName(old != null ? old.concat(chunk) : chunk);
		} catch (MessagingException var4) {
			this.reportError((String) var4.getMessage(), var4, 5);
		}

	}

	private void appendSubject(Message msg, String chunk) {
		if (chunk != null) {
			if (chunk.length() > 0) {
				this.appendSubject0(msg, chunk);
			}
		} else {
			this.reportNullError(5);
		}

	}

	private void appendSubject0(Message msg, String chunk) {
		try {
			chunk = chunk.replaceAll("[\\x00-\\x1F\\x7F]+", "");
			String charset = this.getEncodingName();
			String old = msg.getSubject();

			assert msg instanceof MimeMessage : msg;

			((MimeMessage) msg).setSubject(old != null ? old.concat(chunk) : chunk, MimeUtility.mimeCharset(charset));
		} catch (MessagingException var5) {
			this.reportError((String) var5.getMessage(), var5, 5);
		}

	}

	private Locale localeFor(LogRecord r) {
		ResourceBundle rb = r.getResourceBundle();
		Locale l;
		if (rb != null) {
			l = rb.getLocale();
			if (l == null || isEmpty(l.getLanguage())) {
				l = Locale.getDefault();
			}
		} else {
			l = null;
		}

		return l;
	}

	private void appendContentLang(MimePart p, Locale l) {
		try {
			String lang = LogManagerProperties.toLanguageTag(l);
			if (lang.length() != 0) {
				String header = p.getHeader("Content-Language", (String) null);
				if (isEmpty(header)) {
					p.setHeader("Content-Language", lang);
				} else if (!header.equalsIgnoreCase(lang)) {
					lang = ",".concat(lang);
					int idx = 0;

					while ((idx = header.indexOf(lang, idx)) > -1) {
						idx += lang.length();
						if (idx == header.length() || header.charAt(idx) == ',') {
							break;
						}
					}

					if (idx < 0) {
						int len = header.lastIndexOf("\r\n\t");
						if (len < 0) {
							len = 20 + header.length();
						} else {
							len = header.length() - len + 8;
						}

						if (len + lang.length() > 76) {
							header = header.concat("\r\n\t".concat(lang));
						} else {
							header = header.concat(lang);
						}

						p.setHeader("Content-Language", header);
					}
				}
			}
		} catch (MessagingException var7) {
			this.reportError((String) var7.getMessage(), var7, 5);
		}

	}

	private void setAcceptLang(Part p) {
		try {
			String lang = LogManagerProperties.toLanguageTag(Locale.getDefault());
			if (lang.length() != 0) {
				p.setHeader("Accept-Language", lang);
			}
		} catch (MessagingException var3) {
			this.reportError((String) var3.getMessage(), var3, 5);
		}

	}

	private void reportFilterError(LogRecord record) {
		assert Thread.holdsLock(this);

		Formatter f = createSimpleFormatter();
		String msg = "Log record " + record.getSequenceNumber() + " was filtered from all message parts.  "
				+ this.head(f) + this.format(f, record) + this.tail(f, "");
		String txt = this.getFilter() + ", " + Arrays.asList(this.readOnlyAttachmentFilters());
		this.reportError((String) msg, new IllegalArgumentException(txt), 5);
	}

	private void reportNonSymmetric(Object o, Object found) {
		this.reportError((String) "Non symmetric equals implementation.",
				new IllegalArgumentException(o.getClass().getName() + " is not equal to " + found.getClass().getName()),
				4);
	}

	private void reportNonDiscriminating(Object o, Object found) {
		this.reportError((String) "Non discriminating equals implementation.", new IllegalArgumentException(
				o.getClass().getName() + " should not be equal to " + found.getClass().getName()), 4);
	}

	private void reportNullError(int code) {
		this.reportError((String) "null", new NullPointerException(), code);
	}

	private String head(Formatter f) {
		try {
			return f.getHead(this);
		} catch (RuntimeException var3) {
			this.reportError((String) var3.getMessage(), var3, 5);
			return "";
		}
	}

	private String format(Formatter f, LogRecord r) {
		try {
			return f.format(r);
		} catch (RuntimeException var4) {
			this.reportError((String) var4.getMessage(), var4, 5);
			return "";
		}
	}

	private String tail(Formatter f, String def) {
		try {
			return f.getTail(this);
		} catch (RuntimeException var4) {
			this.reportError((String) var4.getMessage(), var4, 5);
			return def;
		}
	}

	private void setMailer(Message msg) {
		try {
			Class<?> mail = MailHandler.class;
			Class<?> k = this.getClass();
			String value;
			if (k == mail) {
				value = mail.getName();
			} else {
				try {
					value = MimeUtility.encodeText(k.getName());
				} catch (UnsupportedEncodingException var6) {
					this.reportError((String) var6.getMessage(), var6, 5);
					value = k.getName().replaceAll("[^\\x00-\\x7F]", "");
				}

				value = MimeUtility.fold(10, mail.getName() + " using the " + value + " extension.");
			}

			msg.setHeader("X-Mailer", value);
		} catch (MessagingException var7) {
			this.reportError((String) var7.getMessage(), var7, 5);
		}

	}

	private void setPriority(Message msg) {
		try {
			msg.setHeader("Importance", "High");
			msg.setHeader("Priority", "urgent");
			msg.setHeader("X-Priority", "2");
		} catch (MessagingException var3) {
			this.reportError((String) var3.getMessage(), var3, 5);
		}

	}

	private void setIncompleteCopy(Message msg) {
		try {
			msg.setHeader("Incomplete-Copy", "");
		} catch (MessagingException var3) {
			this.reportError((String) var3.getMessage(), var3, 5);
		}

	}

	private void setAutoSubmitted(Message msg) {
		if (this.allowRestrictedHeaders()) {
			try {
				msg.setHeader("auto-submitted", "auto-generated");
			} catch (MessagingException var3) {
				this.reportError((String) var3.getMessage(), var3, 5);
			}
		}

	}

	private void setFrom(Message msg) {
		String from = this.getSession(msg).getProperty("mail.from");
		if (from != null) {
			try {
				Address[] address = InternetAddress.parse(from, false);
				if (address.length > 0) {
					if (address.length == 1) {
						msg.setFrom(address[0]);
					} else {
						msg.addFrom(address);
					}
				}
			} catch (MessagingException var4) {
				this.reportError((String) var4.getMessage(), var4, 5);
				this.setDefaultFrom(msg);
			}
		} else {
			this.setDefaultFrom(msg);
		}

	}

	private void setDefaultFrom(Message msg) {
		try {
			msg.setFrom();
		} catch (MessagingException var3) {
			this.reportError((String) var3.getMessage(), var3, 5);
		}

	}

	private void setDefaultRecipient(Message msg, Message.RecipientType type) {
		try {
			Address a = InternetAddress.getLocalAddress(this.getSession(msg));
			if (a != null) {
				msg.setRecipient(type, a);
			} else {
				MimeMessage m = new MimeMessage(this.getSession(msg));
				m.setFrom();
				Address[] from = m.getFrom();
				if (from.length <= 0) {
					throw new MessagingException("No local address.");
				}

				msg.setRecipients(type, from);
			}
		} catch (RuntimeException | MessagingException var6) {
			this.reportError((String) "Unable to compute a default recipient.", var6, 5);
		}

	}

	private void setReplyTo(Message msg) {
		String reply = this.getSession(msg).getProperty("mail.reply.to");
		if (!isEmpty(reply)) {
			try {
				Address[] address = InternetAddress.parse(reply, false);
				if (address.length > 0) {
					msg.setReplyTo(address);
				}
			} catch (MessagingException var4) {
				this.reportError((String) var4.getMessage(), var4, 5);
			}
		}

	}

	private void setSender(Message msg) {
		assert msg instanceof MimeMessage : msg;

		String sender = this.getSession(msg).getProperty("mail.sender");
		if (!isEmpty(sender)) {
			try {
				InternetAddress[] address = InternetAddress.parse(sender, false);
				if (address.length > 0) {
					((MimeMessage) msg).setSender(address[0]);
					if (address.length > 1) {
						this.reportError((String) "Ignoring other senders.", this.tooManyAddresses(address, 1), 5);
					}
				}
			} catch (MessagingException var4) {
				this.reportError((String) var4.getMessage(), var4, 5);
			}
		}

	}

	private AddressException tooManyAddresses(Address[] address, int offset) {
		Object l = Arrays.asList(address).subList(offset, address.length);
		return new AddressException(l.toString());
	}

	private boolean setRecipient(Message msg, String key, Message.RecipientType type) {
		String value = this.getSession(msg).getProperty(key);
		boolean containsKey = value != null;
		if (!isEmpty(value)) {
			try {
				Address[] address = InternetAddress.parse(value, false);
				if (address.length > 0) {
					msg.setRecipients(type, address);
				}
			} catch (MessagingException var7) {
				this.reportError((String) var7.getMessage(), var7, 5);
			}
		}

		return containsKey;
	}

	private String toRawString(Message msg) throws MessagingException, IOException {
		if (msg != null) {
			Object ccl = this.getAndSetContextClassLoader(MAILHANDLER_LOADER);

			String var5;
			try {
				int nbytes = Math.max(msg.getSize() + 1024, 1024);
				ByteArrayOutputStream out = new ByteArrayOutputStream(nbytes);
				msg.writeTo(out);
				var5 = out.toString("UTF-8");
			} finally {
				this.getAndSetContextClassLoader(ccl);
			}

			return var5;
		} else {
			return null;
		}
	}

	private String toMsgString(Throwable t) {
		if (t == null) {
			return "null";
		} else {
			String charset = this.getEncodingName();

			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
				OutputStreamWriter ows = new OutputStreamWriter(out, charset);

				try {
					PrintWriter pw = new PrintWriter(ows);

					try {
						pw.println(t.getMessage());
						t.printStackTrace(pw);
						pw.flush();
					} catch (Throwable var10) {
						try {
							pw.close();
						} catch (Throwable var9) {
							var10.addSuppressed(var9);
						}

						throw var10;
					}

					pw.close();
				} catch (Throwable var11) {
					try {
						ows.close();
					} catch (Throwable var8) {
						var11.addSuppressed(var8);
					}

					throw var11;
				}

				ows.close();
				return out.toString(charset);
			} catch (Exception var12) {
				return t.toString() + ' ' + var12.toString();
			}
		}
	}

	private Object getAndSetContextClassLoader(Object ccl) {
		if (ccl != MailHandler.GetAndSetContext.NOT_MODIFIED) {
			try {
				Object pa;
				if (ccl instanceof PrivilegedAction) {
					pa = (PrivilegedAction) ccl;
				} else {
					pa = new GetAndSetContext(ccl);
				}

				return AccessController.doPrivileged((PrivilegedAction) pa);
			} catch (SecurityException var3) {
			}
		}

		return MailHandler.GetAndSetContext.NOT_MODIFIED;
	}

	private static RuntimeException attachmentMismatch(String msg) {
		return new IndexOutOfBoundsException(msg);
	}

	private static RuntimeException attachmentMismatch(int expected, int found) {
		return attachmentMismatch("Attachments mismatched, expected " + expected + " but given " + found + '.');
	}

	private static MessagingException attach(MessagingException required, Exception optional) {
		if (optional != null && !required.setNextException(optional)) {
			if (optional instanceof MessagingException) {
				MessagingException head = (MessagingException) optional;
				if (head.setNextException(required)) {
					return head;
				}
			}

			if (optional != required) {
				required.addSuppressed(optional);
			}
		}

		return required;
	}

	private String getLocalHost(Service s) {
		try {
			return LogManagerProperties.getLocalHost(s);
		} catch (NoSuchMethodException | LinkageError | SecurityException var3) {
		} catch (Exception var4) {
			this.reportError((String) s.toString(), var4, 4);
		}

		return null;
	}

	private Session getSession(Message msg) {
		if (msg == null) {
			throw new NullPointerException();
		} else {
			return (new MessageContext(msg)).getSession();
		}
	}

	private boolean allowRestrictedHeaders() {
		return LogManagerProperties.hasLogManager();
	}

	private static String atIndexMsg(int i) {
		return "At index: " + i + '.';
	}

	static {
		offValue = Level.OFF.intValue();
		MAILHANDLER_LOADER = new GetAndSetContext(MailHandler.class);
		MUTEX = new ThreadLocal();
		MUTEX_PUBLISH = -2;
		MUTEX_REPORT = -4;
		MUTEX_LINKAGE = -8;
	}

	private static final class TailNameFormatter extends Formatter {
		private final String name;

		static Formatter of(String name) {
			return new TailNameFormatter(name);
		}

		private TailNameFormatter(String name) {
			assert name != null;

			this.name = name;
		}

		public final String format(LogRecord record) {
			return "";
		}

		public final String getTail(Handler h) {
			return this.name;
		}

		public final boolean equals(Object o) {
			return o instanceof TailNameFormatter ? this.name.equals(((TailNameFormatter) o).name) : false;
		}

		public final int hashCode() {
			return this.getClass().hashCode() + this.name.hashCode();
		}

		public final String toString() {
			return this.name;
		}
	}

	private static final class GetAndSetContext implements PrivilegedAction<Object> {
		public static final Object NOT_MODIFIED = GetAndSetContext.class;
		private final Object source;

		GetAndSetContext(Object source) {
			this.source = source;
		}

		public final Object run() {
			Thread current = Thread.currentThread();
			ClassLoader ccl = current.getContextClassLoader();
			ClassLoader loader;
			if (this.source == null) {
				loader = null;
			} else if (this.source instanceof ClassLoader) {
				loader = (ClassLoader) this.source;
			} else if (this.source instanceof Class) {
				loader = ((Class) this.source).getClassLoader();
			} else if (this.source instanceof Thread) {
				loader = ((Thread) this.source).getContextClassLoader();
			} else {
				assert !(this.source instanceof Class) : this.source;

				loader = this.source.getClass().getClassLoader();
			}

			if (ccl != loader) {
				current.setContextClassLoader(loader);
				return ccl;
			} else {
				return NOT_MODIFIED;
			}
		}
	}

	private static final class DefaultAuthenticator extends Authenticator {
		private final String pass;

		static Authenticator of(String pass) {
			return new DefaultAuthenticator(pass);
		}

		private DefaultAuthenticator(String pass) {
			assert pass != null;

			this.pass = pass;
		}

		protected final PasswordAuthentication getPasswordAuthentication() {
			return new PasswordAuthentication(this.getDefaultUserName(), this.pass);
		}
	}
}