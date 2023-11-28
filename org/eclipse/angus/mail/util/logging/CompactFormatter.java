package org.eclipse.angus.mail.util.logging;

import java.util.Date;
import java.util.Formattable;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class CompactFormatter extends Formatter {
	private final String fmt;

	private static Class<?>[] loadDeclaredClasses() {
		return new Class[]{Alternate.class};
	}

	public CompactFormatter() {
		String p = this.getClass().getName();
		this.fmt = this.initFormat(p);
	}

	public CompactFormatter(String format) {
		String p = this.getClass().getName();
		this.fmt = format == null ? this.initFormat(p) : format;
	}

	public String format(LogRecord record) {
		ResourceBundle rb = record.getResourceBundle();
		Locale l = rb == null ? null : rb.getLocale();
		String msg = this.formatMessage(record);
		String thrown = this.formatThrown(record);
		String err = this.formatError(record);
		Object[] params = new Object[]{this.formatZonedDateTime(record), this.formatSource(record),
				this.formatLoggerName(record), this.formatLevel(record), msg, thrown, new Alternate(msg, thrown),
				new Alternate(thrown, msg), record.getSequenceNumber(), this.formatThreadID(record), err,
				new Alternate(msg, err), new Alternate(err, msg), this.formatBackTrace(record),
				record.getResourceBundleName(), record.getMessage()};
		return l == null ? String.format(this.fmt, params) : String.format(l, this.fmt, params);
	}

	public String formatMessage(LogRecord record) {
		String msg = super.formatMessage(record);
		msg = replaceClassName(msg, record.getThrown());
		msg = replaceClassName(msg, record.getParameters());
		return msg;
	}

	public String formatMessage(Throwable t) {
		String r;
		if (t != null) {
			Throwable apply = this.apply(t);
			String m = apply.getLocalizedMessage();
			String s = apply.toString();
			String sn = simpleClassName(apply.getClass());
			if (!isNullOrSpaces(m)) {
				if (s.contains(m)) {
					if (!s.startsWith(apply.getClass().getName()) && !s.startsWith(sn)) {
						r = replaceClassName(simpleClassName(s), t);
					} else {
						r = replaceClassName(m, t);
					}
				} else {
					r = replaceClassName(simpleClassName(s) + ": " + m, t);
				}
			} else {
				r = replaceClassName(simpleClassName(s), t);
			}

			if (!r.contains(sn)) {
				r = sn + ": " + r;
			}
		} else {
			r = "";
		}

		return r;
	}

	public String formatLevel(LogRecord record) {
		return record.getLevel().getLocalizedName();
	}

	public String formatSource(LogRecord record) {
		String source = record.getSourceClassName();
		if (source != null) {
			if (record.getSourceMethodName() != null) {
				source = simpleClassName(source) + " " + record.getSourceMethodName();
			} else {
				source = simpleClassName(source);
			}
		} else {
			source = simpleClassName(record.getLoggerName());
		}

		return source;
	}

	public String formatLoggerName(LogRecord record) {
		return simpleClassName(record.getLoggerName());
	}

	public Number formatThreadID(LogRecord record) {
		return (long) record.getThreadID() & 4294967295L;
	}

	public String formatThrown(LogRecord record) {
		Throwable t = record.getThrown();
		String msg;
		if (t != null) {
			String site = this.formatBackTrace(record);
			msg = this.formatMessage(t) + (isNullOrSpaces(site) ? "" : ' ' + site);
		} else {
			msg = "";
		}

		return msg;
	}

	public String formatError(LogRecord record) {
		return this.formatMessage(record.getThrown());
	}

	public String formatBackTrace(LogRecord record) {
		String site = "";
		Throwable t = record.getThrown();
		if (t != null) {
			Throwable root = this.apply(t);
			StackTraceElement[] trace = root.getStackTrace();
			site = this.findAndFormat(trace);
			if (isNullOrSpaces(site)) {
				int limit = 0;

				for (Throwable c = t; c != null; c = c.getCause()) {
					StackTraceElement[] ste = c.getStackTrace();
					site = this.findAndFormat(ste);
					if (!isNullOrSpaces(site)) {
						break;
					}

					if (trace.length == 0) {
						trace = ste;
					}

					++limit;
					if (limit == 65536) {
						break;
					}
				}

				if (isNullOrSpaces(site) && trace.length != 0) {
					site = this.formatStackTraceElement(trace[0]);
				}
			}
		}

		return site;
	}

	private String findAndFormat(StackTraceElement[] trace) {
		String site = "";
		StackTraceElement[] var3 = trace;
		int var4 = trace.length;

		int var5;
		StackTraceElement s;
		for (var5 = 0; var5 < var4; ++var5) {
			s = var3[var5];
			if (!this.ignore(s)) {
				site = this.formatStackTraceElement(s);
				break;
			}
		}

		if (isNullOrSpaces(site)) {
			var3 = trace;
			var4 = trace.length;

			for (var5 = 0; var5 < var4; ++var5) {
				s = var3[var5];
				if (!this.defaultIgnore(s)) {
					site = this.formatStackTraceElement(s);
					break;
				}
			}
		}

		return site;
	}

	private String formatStackTraceElement(StackTraceElement s) {
		String v = simpleClassName(s.getClassName());
		String result = s.toString().replace(s.getClassName(), v);
		v = simpleFileName(s.getFileName());
		if (v != null && result.startsWith(v)) {
			result = result.replace(s.getFileName(), "");
		}

		return result;
	}

	protected Throwable apply(Throwable t) {
		return SeverityComparator.getInstance().apply(t);
	}

	protected boolean ignore(StackTraceElement s) {
		return this.isUnknown(s) || this.defaultIgnore(s);
	}

	protected String toAlternate(String s) {
		return s != null ? s.replaceAll("[\\x00-\\x1F\\x7F]+", "") : null;
	}

	private Comparable<?> formatZonedDateTime(LogRecord record) {
		Comparable<?> zdt = LogManagerProperties.getZonedDateTime(record);
		if (zdt == null) {
			zdt = new Date(record.getMillis());
		}

		return (Comparable) zdt;
	}

	private boolean defaultIgnore(StackTraceElement s) {
		return this.isSynthetic(s) || this.isStaticUtility(s) || this.isReflection(s);
	}

	private boolean isStaticUtility(StackTraceElement s) {
		try {
			return LogManagerProperties.isStaticUtilityClass(s.getClassName());
		} catch (LinkageError | Exception var3) {
			String cn = s.getClassName();
			return cn.endsWith("s") && !cn.endsWith("es") || cn.contains("Util") || cn.endsWith("Throwables");
		}
	}

	private boolean isSynthetic(StackTraceElement s) {
		return s.getMethodName().indexOf(36) > -1;
	}

	private boolean isUnknown(StackTraceElement s) {
		return s.getLineNumber() < 0;
	}

	private boolean isReflection(StackTraceElement s) {
		try {
			return LogManagerProperties.isReflectionClass(s.getClassName());
		} catch (LinkageError | Exception var3) {
			return s.getClassName().startsWith("java.lang.reflect.") || s.getClassName().startsWith("sun.reflect.");
		}
	}

	private String initFormat(String p) {
		String v = LogManagerProperties.fromLogManager(p.concat(".format"));
		if (isNullOrSpaces(v)) {
			v = "%7$#.160s%n";
		}

		return v;
	}

	private static String replaceClassName(String msg, Throwable t) {
		if (!isNullOrSpaces(msg)) {
			int limit = 0;

			for (Throwable c = t; c != null; c = c.getCause()) {
				Class<?> k = c.getClass();
				msg = msg.replace(k.getName(), simpleClassName(k));
				++limit;
				if (limit == 65536) {
					break;
				}
			}
		}

		return msg;
	}

	private static String replaceClassName(String msg, Object[] p) {
		if (!isNullOrSpaces(msg) && p != null) {
			Object[] var2 = p;
			int var3 = p.length;

			for (int var4 = 0; var4 < var3; ++var4) {
				Object o = var2[var4];
				if (o != null) {
					Class<?> k = o.getClass();
					msg = msg.replace(k.getName(), simpleClassName(k));
				}
			}
		}

		return msg;
	}

	private static String simpleClassName(Class<?> k) {
		try {
			return k.getSimpleName();
		} catch (InternalError var2) {
			return simpleClassName(k.getName());
		}
	}

	private static String simpleClassName(String name) {
		if (name != null) {
			int cursor = 0;
			int sign = -1;
			int dot = -1;

			int c;
			for (int prev = dot; cursor < name.length(); cursor += Character.charCount(c)) {
				c = name.codePointAt(cursor);
				if (!Character.isJavaIdentifierPart(c)) {
					if (c != 46) {
						if (dot + 1 == cursor) {
							dot = prev;
						}
						break;
					}

					if (dot + 1 == cursor || dot + 1 == sign) {
						return name;
					}

					prev = dot;
					dot = cursor;
				} else if (c == 36) {
					sign = cursor;
				}
			}

			if (dot > -1) {
				++dot;
				if (dot < cursor) {
					++sign;
					if (sign < cursor) {
						name = name.substring(Math.max(sign, dot));
					}
				}
			}
		}

		return name;
	}

	private static String simpleFileName(String name) {
		if (name != null) {
			int index = name.lastIndexOf(46);
			name = index > -1 ? name.substring(0, index) : name;
		}

		return name;
	}

	private static boolean isNullOrSpaces(String s) {
		return s == null || s.trim().isEmpty();
	}

	static {
		loadDeclaredClasses();
	}

	private class Alternate implements Formattable {
		private final String left;
		private final String right;

		Alternate(String left, String right) {
			this.left = String.valueOf(left);
			this.right = String.valueOf(right);
		}

		public void formatTo(java.util.Formatter formatter, int flags, int width, int precision) {
			String l = this.left;
			String r = this.right;
			if ((flags & 2) == 2) {
				l = l.toUpperCase(formatter.locale());
				r = r.toUpperCase(formatter.locale());
			}

			if ((flags & 4) == 4) {
				l = CompactFormatter.this.toAlternate(l);
				r = CompactFormatter.this.toAlternate(r);
			}

			int lc = 0;
			int rc = 0;
			if (precision >= 0) {
				lc = this.minCodePointCount(l, precision);
				rc = this.minCodePointCount(r, precision);
				if (lc > precision >> 1) {
					lc = Math.max(lc - rc, lc >> 1);
				}

				rc = Math.min(precision - lc, rc);
				l = l.substring(0, l.offsetByCodePoints(0, lc));
				r = r.substring(0, r.offsetByCodePoints(0, rc));
			}

			if (width > 0) {
				if (precision < 0) {
					lc = this.minCodePointCount(l, width);
					rc = this.minCodePointCount(r, width);
				}

				int half = width >> 1;
				if (lc < half) {
					l = this.pad(flags, l, half - lc);
				}

				if (rc < half) {
					r = this.pad(flags, r, half - rc);
				}
			}

			formatter.format(l);
			if (!l.isEmpty() && !r.isEmpty()) {
				formatter.format("|");
			}

			formatter.format(r);
		}

		private int minCodePointCount(String s, int limit) {
			int len = s.length();
			return len - limit >= limit ? limit : Math.min(s.codePointCount(0, len), limit);
		}

		private String pad(int flags, String s, int padding) {
			StringBuilder b = new StringBuilder(Math.max(s.length() + padding, padding));
			int i;
			if ((flags & 1) == 1) {
				for (i = 0; i < padding; ++i) {
					b.append(' ');
				}

				b.append(s);
			} else {
				b.append(s);

				for (i = 0; i < padding; ++i) {
					b.append(' ');
				}
			}

			return b.toString();
		}
	}
}