package org.eclipse.angus.mail.util.logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.LoggingPermission;

final class LogManagerProperties extends Properties {
	private static final long serialVersionUID = -2239983349056806252L;
	private static final Method LR_GET_INSTANT;
	private static final Method ZI_SYSTEM_DEFAULT;
	private static final Method ZDT_OF_INSTANT;
	private static volatile String[] REFLECT_NAMES;
	private static final Object LOG_MANAGER;
	private final String prefix;

	private static Object loadLogManager() {
		Object m;
		try {
			m = LogManager.getLogManager();
		} catch (RuntimeException | LinkageError var2) {
			m = readConfiguration();
		}

		return m;
	}

	private static Properties readConfiguration() {
		Properties props = new Properties();

		try {
			String n = System.getProperty("java.util.logging.config.file");
			if (n != null) {
				File f = (new File(n)).getCanonicalFile();
				InputStream in = new FileInputStream(f);

				try {
					props.load(in);
				} finally {
					in.close();
				}
			}
		} catch (Exception | LinkageError var8) {
		}

		return props;
	}

	static String fromLogManager(String name) {
		if (name == null) {
			throw new NullPointerException();
		} else {
			Object m = LOG_MANAGER;

			try {
				if (m instanceof Properties) {
					return ((Properties) m).getProperty(name);
				}
			} catch (RuntimeException var4) {
			}

			if (m != null) {
				try {
					if (m instanceof LogManager) {
						return ((LogManager) m).getProperty(name);
					}
				} catch (RuntimeException | LinkageError var3) {
				}
			}

			return null;
		}
	}

	static void checkLogManagerAccess() {
		boolean checked = false;
		Object m = LOG_MANAGER;
		if (m != null) {
			try {
				if (m instanceof LogManager) {
					checked = true;
					((LogManager) m).checkAccess();
				}
			} catch (SecurityException var3) {
				if (checked) {
					throw var3;
				}
			} catch (RuntimeException | LinkageError var4) {
			}
		}

		if (!checked) {
			checkLoggingAccess();
		}

	}

	private static void checkLoggingAccess() {
		boolean checked = false;
		Logger global = Logger.getLogger("global");

		try {
			if (Logger.class == global.getClass()) {
				global.removeHandler((Handler) null);
				checked = true;
			}
		} catch (NullPointerException var3) {
		}

		if (!checked) {
			SecurityManager sm = System.getSecurityManager();
			if (sm != null) {
				sm.checkPermission(new LoggingPermission("control", (String) null));
			}
		}

	}

	static boolean hasLogManager() {
		Object m = LOG_MANAGER;
		return m != null && !(m instanceof Properties);
	}

	static Comparable<?> getZonedDateTime(LogRecord record) {
		if (record == null) {
			throw new NullPointerException();
		} else {
			Method m = ZDT_OF_INSTANT;
			if (m != null) {
				try {
					return (Comparable) m.invoke((Object) null, LR_GET_INSTANT.invoke(record),
							ZI_SYSTEM_DEFAULT.invoke((Object) null));
				} catch (RuntimeException var4) {
					assert LR_GET_INSTANT != null && ZI_SYSTEM_DEFAULT != null : var4;
				} catch (InvocationTargetException var5) {
					Throwable cause = var5.getCause();
					if (cause instanceof Error) {
						throw (Error) cause;
					}

					if (cause instanceof RuntimeException) {
						throw (RuntimeException) cause;
					}

					throw new UndeclaredThrowableException(var5);
				} catch (Exception var6) {
				}
			}

			return null;
		}
	}

	static String getLocalHost(Object s) throws Exception {
		try {
			Method m = s.getClass().getMethod("getLocalHost");
			if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == String.class) {
				return (String) m.invoke(s);
			} else {
				throw new NoSuchMethodException(m.toString());
			}
		} catch (ExceptionInInitializerError var2) {
			throw wrapOrThrow(var2);
		} catch (InvocationTargetException var3) {
			throw paramOrError(var3);
		}
	}

	static long parseDurationToMillis(CharSequence value) throws Exception {
		try {
			Class<?> k = findClass("java.time.Duration");
			Method parse = k.getMethod("parse", CharSequence.class);
			if (k.isAssignableFrom(parse.getReturnType()) && Modifier.isStatic(parse.getModifiers())) {
				Method toMillis = k.getMethod("toMillis");
				if (Long.TYPE.isAssignableFrom(toMillis.getReturnType())
						&& !Modifier.isStatic(toMillis.getModifiers())) {
					return (Long) toMillis.invoke(parse.invoke((Object) null, value));
				} else {
					throw new NoSuchMethodException(toMillis.toString());
				}
			} else {
				throw new NoSuchMethodException(parse.toString());
			}
		} catch (ExceptionInInitializerError var4) {
			throw wrapOrThrow(var4);
		} catch (InvocationTargetException var5) {
			throw paramOrError(var5);
		}
	}

	static String toLanguageTag(Locale locale) {
		String l = locale.getLanguage();
		String c = locale.getCountry();
		String v = locale.getVariant();
		char[] b = new char[l.length() + c.length() + v.length() + 2];
		int count = l.length();
		l.getChars(0, count, b, 0);
		if (c.length() != 0 || l.length() != 0 && v.length() != 0) {
			b[count] = '-';
			++count;
			c.getChars(0, c.length(), b, count);
			count += c.length();
		}

		if (v.length() != 0 && (l.length() != 0 || c.length() != 0)) {
			b[count] = '-';
			++count;
			v.getChars(0, v.length(), b, count);
			count += v.length();
		}

		return String.valueOf(b, 0, count);
	}

	static Filter newFilter(String name) throws Exception {
		return (Filter) newObjectFrom(name, Filter.class);
	}

	static Formatter newFormatter(String name) throws Exception {
		return (Formatter) newObjectFrom(name, Formatter.class);
	}

	static Comparator<? super LogRecord> newComparator(String name) throws Exception {
		return (Comparator) newObjectFrom(name, Comparator.class);
	}

	static <T> Comparator<T> reverseOrder(Comparator<T> c) {
		if (c == null) {
			throw new NullPointerException();
		} else {
			Comparator<T> reverse = null;

			try {
				Method m = c.getClass().getMethod("reversed");
				if (!Modifier.isStatic(m.getModifiers()) && Comparator.class.isAssignableFrom(m.getReturnType())) {
					try {
						reverse = (Comparator) m.invoke(c);
					} catch (ExceptionInInitializerError var4) {
						throw wrapOrThrow(var4);
					}
				}
			} catch (RuntimeException | IllegalAccessException | NoSuchMethodException var5) {
			} catch (InvocationTargetException var6) {
				paramOrError(var6);
			}

			if (reverse == null) {
				reverse = Collections.reverseOrder(c);
			}

			return reverse;
		}
	}

	static ErrorManager newErrorManager(String name) throws Exception {
		return (ErrorManager) newObjectFrom(name, ErrorManager.class);
	}

	static boolean isStaticUtilityClass(String name) throws Exception {
		Class<?> c = findClass(name);
		Class<?> obj = Object.class;
		Method[] methods;
		boolean util;
		if (c != obj && (methods = c.getMethods()).length != 0) {
			util = true;
			Method[] var5 = methods;
			int var6 = methods.length;

			for (int var7 = 0; var7 < var6; ++var7) {
				Method m = var5[var7];
				if (m.getDeclaringClass() != obj && !Modifier.isStatic(m.getModifiers())) {
					util = false;
					break;
				}
			}
		} else {
			util = false;
		}

		return util;
	}

	static boolean isReflectionClass(String name) throws Exception {
		String[] names = REFLECT_NAMES;
		if (names == null) {
			REFLECT_NAMES = names = reflectionClassNames();
		}

		String[] var2 = names;
		int var3 = names.length;

		for (int var4 = 0; var4 < var3; ++var4) {
			String rf = var2[var4];
			if (name.equals(rf)) {
				return true;
			}
		}

		findClass(name);
		return false;
	}

	private static String[] reflectionClassNames() throws Exception {
		Class<?> thisClass = LogManagerProperties.class;

		assert Modifier.isFinal(thisClass.getModifiers()) : thisClass;

		try {
			HashSet<String> traces = new HashSet();
			Throwable t = (Throwable) Throwable.class.getConstructor().newInstance();
			StackTraceElement[] var3 = t.getStackTrace();
			int var4 = var3.length;

			int var5;
			StackTraceElement ste;
			for (var5 = 0; var5 < var4; ++var5) {
				ste = var3[var5];
				if (thisClass.getName().equals(ste.getClassName())) {
					break;
				}

				traces.add(ste.getClassName());
			}

			Throwable.class.getMethod("fillInStackTrace").invoke(t);
			var3 = t.getStackTrace();
			var4 = var3.length;

			for (var5 = 0; var5 < var4; ++var5) {
				ste = var3[var5];
				if (thisClass.getName().equals(ste.getClassName())) {
					break;
				}

				traces.add(ste.getClassName());
			}

			return (String[]) traces.toArray(new String[0]);
		} catch (InvocationTargetException var7) {
			throw paramOrError(var7);
		}
	}

	static <T> T newObjectFrom(String name, Class<T> type) throws Exception {
		try {
			Class<?> clazz = findClass(name);
			if (type.isAssignableFrom(clazz)) {
				try {
					return type.cast(clazz.getConstructor().newInstance());
				} catch (InvocationTargetException var4) {
					throw paramOrError(var4);
				}
			} else {
				throw new ClassCastException(clazz.getName() + " cannot be cast to " + type.getName());
			}
		} catch (NoClassDefFoundError var5) {
			throw new ClassNotFoundException(var5.toString(), var5);
		} catch (ExceptionInInitializerError var6) {
			throw wrapOrThrow(var6);
		}
	}

	private static Exception paramOrError(InvocationTargetException ite) {
		Throwable cause = ite.getCause();
		if (cause != null && cause instanceof VirtualMachineError | cause instanceof ThreadDeath) {
			throw (Error) cause;
		} else {
			return ite;
		}
	}

	private static InvocationTargetException wrapOrThrow(ExceptionInInitializerError eiie) {
		if (eiie.getCause() instanceof Error) {
			throw eiie;
		} else {
			return new InvocationTargetException(eiie);
		}
	}

	private static Class<?> findClass(String name) throws ClassNotFoundException {
		ClassLoader[] loaders = getClassLoaders();

		assert loaders.length == 2 : loaders.length;

		Class clazz;
		if (loaders[0] != null) {
			try {
				clazz = Class.forName(name, false, loaders[0]);
			} catch (ClassNotFoundException var4) {
				clazz = tryLoad(name, loaders[1]);
			}
		} else {
			clazz = tryLoad(name, loaders[1]);
		}

		return clazz;
	}

	private static Class<?> tryLoad(String name, ClassLoader l) throws ClassNotFoundException {
		return l != null ? Class.forName(name, false, l) : Class.forName(name);
	}

	private static ClassLoader[] getClassLoaders() {
		return (ClassLoader[]) AccessController.doPrivileged(new PrivilegedAction<ClassLoader[]>() {
			public ClassLoader[] run() {
				ClassLoader[] loaders = new ClassLoader[2];

				try {
					loaders[0] = ClassLoader.getSystemClassLoader();
				} catch (SecurityException var4) {
					loaders[0] = null;
				}

				try {
					loaders[1] = Thread.currentThread().getContextClassLoader();
				} catch (SecurityException var3) {
					loaders[1] = null;
				}

				return loaders;
			}
		});
	}

	LogManagerProperties(Properties parent, String prefix) {
		super(parent);
		if (parent != null && prefix != null) {
			this.prefix = prefix;
		} else {
			throw new NullPointerException();
		}
	}

	public synchronized Object clone() {
		return this.exportCopy(this.defaults);
	}

	public synchronized String getProperty(String key) {
		String value = this.defaults.getProperty(key);
		if (value == null) {
			if (key.length() > 0) {
				value = fromLogManager(this.prefix + '.' + key);
			}

			if (value == null) {
				value = fromLogManager(key);
			}

			if (value != null) {
				super.put(key, value);
			} else {
				Object v = super.get(key);
				value = v instanceof String ? (String) v : null;
			}
		}

		return value;
	}

	public String getProperty(String key, String def) {
		String value = this.getProperty(key);
		return value == null ? def : value;
	}

	public synchronized Object get(Object key) {
		Object value;
		if (key instanceof String) {
			value = this.getProperty((String) key);
		} else {
			value = null;
		}

		if (value == null) {
			value = this.defaults.get(key);
			if (value == null && !this.defaults.containsKey(key)) {
				value = super.get(key);
			}
		}

		return value;
	}

	public synchronized Object put(Object key, Object value) {
		if (key instanceof String && value instanceof String) {
			Object def = this.preWrite(key);
			Object man = super.put(key, value);
			return man == null ? def : man;
		} else {
			return super.put(key, value);
		}
	}

	public Object setProperty(String key, String value) {
		return this.put(key, value);
	}

	public synchronized boolean containsKey(Object key) {
		boolean found = key instanceof String && this.getProperty((String) key) != null;
		if (!found) {
			found = this.defaults.containsKey(key) || super.containsKey(key);
		}

		return found;
	}

	public synchronized Object remove(Object key) {
		Object def = this.preWrite(key);
		Object man = super.remove(key);
		return man == null ? def : man;
	}

	public Enumeration<?> propertyNames() {
		assert false;

		return super.propertyNames();
	}

	public boolean equals(Object o) {
		if (o == null) {
			return false;
		} else if (o == this) {
			return true;
		} else if (!(o instanceof Properties)) {
			return false;
		} else {
			assert false : this.prefix;

			return super.equals(o);
		}
	}

	public int hashCode() {
		assert false : this.prefix.hashCode();

		return super.hashCode();
	}

	private Object preWrite(Object key) {
		assert Thread.holdsLock(this);

		return this.get(key);
	}

	private Properties exportCopy(Properties parent) {
		Thread.holdsLock(this);
		Properties child = new Properties(parent);
		child.putAll(this);
		return child;
	}

	private synchronized Object writeReplace() throws ObjectStreamException {
		assert false;

		return this.exportCopy((Properties) this.defaults.clone());
	}

	static {
		Method lrgi = null;
		Method zisd = null;
		Method zdtoi = null;

		try {
			lrgi = LogRecord.class.getMethod("getInstant");

			assert Comparable.class.isAssignableFrom(lrgi.getReturnType()) : lrgi;

			zisd = findClass("java.time.ZoneId").getMethod("systemDefault");
			if (!Modifier.isStatic(zisd.getModifiers())) {
				throw new NoSuchMethodException(zisd.toString());
			}

			zdtoi = findClass("java.time.ZonedDateTime").getMethod("ofInstant", findClass("java.time.Instant"),
					findClass("java.time.ZoneId"));
			if (!Modifier.isStatic(zdtoi.getModifiers())) {
				throw new NoSuchMethodException(zdtoi.toString());
			}

			if (!Comparable.class.isAssignableFrom(zdtoi.getReturnType())) {
				throw new NoSuchMethodException(zdtoi.toString());
			}
		} catch (LinkageError | RuntimeException var8) {
		} catch (Exception var9) {
		} finally {
			if (lrgi == null || zisd == null || zdtoi == null) {
				lrgi = null;
				zisd = null;
				zdtoi = null;
			}

		}

		LR_GET_INSTANT = lrgi;
		ZI_SYSTEM_DEFAULT = zisd;
		ZDT_OF_INSTANT = zdtoi;
		LOG_MANAGER = loadLogManager();
	}
}