package org.eclipse.angus.mail.util;

import jakarta.mail.Session;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

public class PropUtil {
	private PropUtil() {
	}

	public static int getIntProperty(Properties props, String name, int def) {
		return getInt(getProp(props, name), def);
	}

	public static boolean getBooleanProperty(Properties props, String name, boolean def) {
		return getBoolean(getProp(props, name), def);
	}

	public static ScheduledExecutorService getScheduledExecutorServiceProperty(Properties props, String name) {
		return getScheduledExecutorService(getProp(props, name));
	}

	@Deprecated
	public static int getIntSessionProperty(Session session, String name, int def) {
		return getInt(getProp(session.getProperties(), name), def);
	}

	@Deprecated
	public static boolean getBooleanSessionProperty(Session session, String name, boolean def) {
		return getBoolean(getProp(session.getProperties(), name), def);
	}

	public static boolean getBooleanSystemProperty(String name, boolean def) {
		try {
			return getBoolean(getProp(System.getProperties(), name), def);
		} catch (SecurityException var4) {
			try {
				String value = System.getProperty(name);
				if (value == null) {
					return def;
				} else if (def) {
					return !value.equalsIgnoreCase("false");
				} else {
					return value.equalsIgnoreCase("true");
				}
			} catch (SecurityException var3) {
				return def;
			}
		}
	}

	private static Object getProp(Properties props, String name) {
		Object val = props.get(name);
		return val != null ? val : props.getProperty(name);
	}

	private static int getInt(Object value, int def) {
		if (value == null) {
			return def;
		} else {
			if (value instanceof String) {
				try {
					String s = (String) value;
					if (s.startsWith("0x")) {
						return Integer.parseInt(s.substring(2), 16);
					}

					return Integer.parseInt(s);
				} catch (NumberFormatException var3) {
				}
			}

			return value instanceof Integer ? (Integer) value : def;
		}
	}

	private static ScheduledExecutorService getScheduledExecutorService(Object value) {
		return (ScheduledExecutorService) value;
	}

	private static boolean getBoolean(Object value, boolean def) {
		if (value == null) {
			return def;
		} else if (value instanceof String) {
			if (def) {
				return !((String) value).equalsIgnoreCase("false");
			} else {
				return ((String) value).equalsIgnoreCase("true");
			}
		} else {
			return value instanceof Boolean ? (Boolean) value : def;
		}
	}
}