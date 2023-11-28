package org.eclipse.angus.mail.util.logging;

import java.lang.reflect.UndeclaredThrowableException;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class CollectorFormatter extends Formatter {
	private static final long INIT_TIME = System.currentTimeMillis();
	private final String fmt;
	private final Formatter formatter;
	private final Comparator<? super LogRecord> comparator;
	private LogRecord last;
	private long count;
	private long generation = 1L;
	private long thrown;
	private long minMillis;
	private long maxMillis;

	public CollectorFormatter() {
		this.minMillis = INIT_TIME;
		this.maxMillis = Long.MIN_VALUE;
		String p = this.getClass().getName();
		this.fmt = this.initFormat(p);
		this.formatter = this.initFormatter(p);
		this.comparator = this.initComparator(p);
	}

	public CollectorFormatter(String format) {
		this.minMillis = INIT_TIME;
		this.maxMillis = Long.MIN_VALUE;
		String p = this.getClass().getName();
		this.fmt = format == null ? this.initFormat(p) : format;
		this.formatter = this.initFormatter(p);
		this.comparator = this.initComparator(p);
	}

	public CollectorFormatter(String format, Formatter f, Comparator<? super LogRecord> c) {
		this.minMillis = INIT_TIME;
		this.maxMillis = Long.MIN_VALUE;
		String p = this.getClass().getName();
		this.fmt = format == null ? this.initFormat(p) : format;
		this.formatter = f;
		this.comparator = c;
	}

	public String format(LogRecord record) {
		if (record == null) {
			throw new NullPointerException();
		} else {
			boolean accepted;
			do {
				LogRecord peek = this.peek();
				LogRecord update = this.apply(peek != null ? peek : record, record);
				if (peek != update) {
					update.getSourceMethodName();
					accepted = this.acceptAndUpdate(peek, update);
				} else {
					accepted = this.accept(peek, record);
				}
			} while (!accepted);

			return "";
		}
	}

	public String getTail(Handler h) {
		super.getTail(h);
		return this.formatRecord(h, true);
	}

	public String toString() {
		String result;
		try {
			result = this.formatRecord((Handler) null, false);
		} catch (RuntimeException var3) {
			result = super.toString();
		}

		return result;
	}

	protected LogRecord apply(LogRecord t, LogRecord u) {
		if (t != null && u != null) {
			if (this.comparator != null) {
				return this.comparator.compare(t, u) >= 0 ? t : u;
			} else {
				return u;
			}
		} else {
			throw new NullPointerException();
		}
	}

	private synchronized boolean accept(LogRecord e, LogRecord u) {
		long millis = u.getMillis();
		Throwable ex = u.getThrown();
		if (this.last == e) {
			if (++this.count != 1L) {
				this.minMillis = Math.min(this.minMillis, millis);
			} else {
				this.minMillis = millis;
			}

			this.maxMillis = Math.max(this.maxMillis, millis);
			if (ex != null) {
				++this.thrown;
			}

			return true;
		} else {
			return false;
		}
	}

	private synchronized void reset(long min) {
		if (this.last != null) {
			this.last = null;
			++this.generation;
		}

		this.count = 0L;
		this.thrown = 0L;
		this.minMillis = min;
		this.maxMillis = Long.MIN_VALUE;
	}

	private String formatRecord(Handler h, boolean reset) {
		LogRecord record;
		long c;
		long t;
		long g;
		long msl;
		long msh;
		long now;
		synchronized (this) {
			record = this.last;
			c = this.count;
			g = this.generation;
			t = this.thrown;
			msl = this.minMillis;
			msh = this.maxMillis;
			now = System.currentTimeMillis();
			if (c == 0L) {
				msh = now;
			}

			if (reset) {
				this.reset(msh);
			}
		}

		Formatter f = this.formatter;
		String head;
		String msg;
		String tail;
		if (f != null) {
			synchronized (f) {
				head = f.getHead(h);
				msg = record != null ? f.format(record) : "";
				tail = f.getTail(h);
			}
		} else {
			head = "";
			msg = record != null ? this.formatMessage(record) : "";
			tail = "";
		}

		Locale l = null;
		if (record != null) {
			ResourceBundle rb = record.getResourceBundle();
			l = rb == null ? null : rb.getLocale();
		}

		MessageFormat mf;
		if (l == null) {
			mf = new MessageFormat(this.fmt);
		} else {
			mf = new MessageFormat(this.fmt, l);
		}

		return mf.format(new Object[]{this.finish(head), this.finish(msg), this.finish(tail), c, c - 1L, t, c - t, msl,
				msh, msh - msl, INIT_TIME, now, now - INIT_TIME, g});
	}

	protected String finish(String s) {
		return s.trim();
	}

	private synchronized LogRecord peek() {
		return this.last;
	}

	private synchronized boolean acceptAndUpdate(LogRecord e, LogRecord u) {
		if (this.accept(e, u)) {
			this.last = u;
			return true;
		} else {
			return false;
		}
	}

	private String initFormat(String p) {
		String v = LogManagerProperties.fromLogManager(p.concat(".format"));
		if (v == null || v.length() == 0) {
			v = "{0}{1}{2}{4,choice,-1#|0#|0<... {4,number,integer} more}\n";
		}

		return v;
	}

	private Formatter initFormatter(String p) {
		String v = LogManagerProperties.fromLogManager(p.concat(".formatter"));
		Formatter f;
		if (v != null && v.length() != 0) {
			if (!"null".equalsIgnoreCase(v)) {
				try {
					f = LogManagerProperties.newFormatter(v);
				} catch (RuntimeException var5) {
					throw var5;
				} catch (Exception var6) {
					throw new UndeclaredThrowableException(var6);
				}
			} else {
				f = null;
			}
		} else {
			f = (Formatter) Formatter.class.cast(new CompactFormatter());
		}

		return f;
	}

	private Comparator<? super LogRecord> initComparator(String p) {
		String name = LogManagerProperties.fromLogManager(p.concat(".comparator"));
		String reverse = LogManagerProperties.fromLogManager(p.concat(".comparator.reverse"));

		try {
			Comparator c;
			if (name != null && name.length() != 0) {
				if (!"null".equalsIgnoreCase(name)) {
					c = LogManagerProperties.newComparator(name);
					if (Boolean.parseBoolean(reverse)) {
						assert c != null;

						c = LogManagerProperties.reverseOrder(c);
					}
				} else {
					if (reverse != null) {
						throw new IllegalArgumentException("No comparator to reverse.");
					}

					c = null;
				}
			} else {
				if (reverse != null) {
					throw new IllegalArgumentException("No comparator to reverse.");
				}

				c = (Comparator) Comparator.class.cast(SeverityComparator.getInstance());
			}

			return c;
		} catch (RuntimeException var6) {
			throw var6;
		} catch (Exception var7) {
			throw new UndeclaredThrowableException(var7);
		}
	}
}