package org.eclipse.angus.mail.pop3;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.URLName;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import org.eclipse.angus.mail.util.MailConnectException;
import org.eclipse.angus.mail.util.MailLogger;
import org.eclipse.angus.mail.util.PropUtil;
import org.eclipse.angus.mail.util.SocketConnectException;

public class POP3Store extends Store {
	private String name;
	private int defaultPort;
	private boolean isSSL;
	private Protocol port;
	private POP3Folder portOwner;
	private String host;
	private int portNum;
	private String user;
	private String passwd;
	private boolean useStartTLS;
	private boolean requireStartTLS;
	private boolean usingSSL;
	private Map<String, String> capabilities;
	private MailLogger logger;
	volatile Constructor<?> messageConstructor;
	volatile boolean rsetBeforeQuit;
	volatile boolean disableTop;
	volatile boolean forgetTopHeaders;
	volatile boolean supportsUidl;
	volatile boolean cacheWriteTo;
	volatile boolean useFileCache;
	volatile File fileCacheDir;
	volatile boolean keepMessageContent;
	volatile boolean finalizeCleanClose;

	public POP3Store(Session session, URLName url) {
		this(session, url, "pop3", false);
	}

	public POP3Store(Session session, URLName url, String name, boolean isSSL) {
		super(session, url);
		this.name = "pop3";
		this.defaultPort = 110;
		this.isSSL = false;
		this.port = null;
		this.portOwner = null;
		this.host = null;
		this.portNum = -1;
		this.user = null;
		this.passwd = null;
		this.useStartTLS = false;
		this.requireStartTLS = false;
		this.usingSSL = false;
		this.messageConstructor = null;
		this.rsetBeforeQuit = false;
		this.disableTop = false;
		this.forgetTopHeaders = false;
		this.supportsUidl = true;
		this.cacheWriteTo = false;
		this.useFileCache = false;
		this.fileCacheDir = null;
		this.keepMessageContent = false;
		this.finalizeCleanClose = false;
		if (url != null) {
			name = url.getProtocol();
		}

		this.name = name;
		this.logger = new MailLogger(this.getClass(), "DEBUG POP3", session.getDebug(), session.getDebugOut());
		if (!isSSL) {
			isSSL = PropUtil.getBooleanProperty(session.getProperties(), "mail." + name + ".ssl.enable", false);
		}

		if (isSSL) {
			this.defaultPort = 995;
		} else {
			this.defaultPort = 110;
		}

		this.isSSL = isSSL;
		this.rsetBeforeQuit = this.getBoolProp("rsetbeforequit");
		this.disableTop = this.getBoolProp("disabletop");
		this.forgetTopHeaders = this.getBoolProp("forgettopheaders");
		this.cacheWriteTo = this.getBoolProp("cachewriteto");
		this.useFileCache = this.getBoolProp("filecache.enable");
		String dir = session.getProperty("mail." + name + ".filecache.dir");
		if (dir != null && this.logger.isLoggable(Level.CONFIG)) {
			this.logger.config("mail." + name + ".filecache.dir: " + dir);
		}

		if (dir != null) {
			this.fileCacheDir = new File(dir);
		}

		this.keepMessageContent = this.getBoolProp("keepmessagecontent");
		this.useStartTLS = this.getBoolProp("starttls.enable");
		this.requireStartTLS = this.getBoolProp("starttls.required");
		this.finalizeCleanClose = this.getBoolProp("finalizecleanclose");
		String s = session.getProperty("mail." + name + ".message.class");
		if (s != null) {
			this.logger.log(Level.CONFIG, "message class: {0}", s);

			try {
				ClassLoader cl = this.getClass().getClassLoader();
				Class<?> messageClass = null;

				try {
					messageClass = Class.forName(s, false, cl);
				} catch (ClassNotFoundException var10) {
					messageClass = Class.forName(s);
				}

				Class<?>[] c = new Class[]{Folder.class, Integer.TYPE};
				this.messageConstructor = messageClass.getConstructor(c);
			} catch (Exception var11) {
				this.logger.log(Level.CONFIG, "failed to load message class", var11);
			}
		}

	}

	private final synchronized boolean getBoolProp(String prop) {
		prop = "mail." + this.name + "." + prop;
		boolean val = PropUtil.getBooleanProperty(this.session.getProperties(), prop, false);
		if (this.logger.isLoggable(Level.CONFIG)) {
			this.logger.config(prop + ": " + val);
		}

		return val;
	}

	synchronized Session getSession() {
		return this.session;
	}

	protected synchronized boolean protocolConnect(String host, int portNum, String user, String passwd)
			throws MessagingException {
		if (host != null && passwd != null && user != null) {
			if (portNum == -1) {
				portNum = PropUtil.getIntProperty(this.session.getProperties(), "mail." + this.name + ".port", -1);
			}

			if (portNum == -1) {
				portNum = this.defaultPort;
			}

			this.host = host;
			this.portNum = portNum;
			this.user = user;
			this.passwd = passwd;

			try {
				this.port = this.getPort((POP3Folder) null);
				return true;
			} catch (EOFException var6) {
				throw new AuthenticationFailedException(var6.getMessage());
			} catch (SocketConnectException var7) {
				throw new MailConnectException(var7);
			} catch (IOException var8) {
				throw new MessagingException("Connect failed", var8);
			}
		} else {
			return false;
		}
	}

	public synchronized boolean isConnected() {
		if (!super.isConnected()) {
			return false;
		} else {
			try {
				if (this.port == null) {
					this.port = this.getPort((POP3Folder) null);
				} else if (!this.port.noop()) {
					throw new IOException("NOOP failed");
				}

				return true;
			} catch (IOException var4) {
				try {
					super.close();
				} catch (MessagingException var3) {
				}

				return false;
			}
		}
	}

	synchronized Protocol getPort(POP3Folder owner) throws IOException {
		if (this.port != null && this.portOwner == null) {
			this.portOwner = owner;
			return this.port;
		} else {
			Protocol p = new Protocol(this.host, this.portNum, this.logger, this.session.getProperties(),
					"mail." + this.name, this.isSSL);
			if (this.useStartTLS || this.requireStartTLS) {
				if (p.hasCapability("STLS")) {
					if (p.stls()) {
						p.setCapabilities(p.capa());
					} else if (this.requireStartTLS) {
						this.logger.fine("STLS required but failed");
						throw cleanupAndThrow(p, new EOFException("STLS required but failed"));
					}
				} else if (this.requireStartTLS) {
					this.logger.fine("STLS required but not supported");
					throw cleanupAndThrow(p, new EOFException("STLS required but not supported"));
				}
			}

			this.capabilities = p.getCapabilities();
			this.usingSSL = p.isSSL();
			if (!this.disableTop && this.capabilities != null && !this.capabilities.containsKey("TOP")) {
				this.disableTop = true;
				this.logger.fine("server doesn't support TOP, disabling it");
			}

			this.supportsUidl = this.capabilities == null || this.capabilities.containsKey("UIDL");

			try {
				if (!this.authenticate(p, this.user, this.passwd)) {
					throw cleanupAndThrow(p, new EOFException("login failed"));
				}
			} catch (EOFException var4) {
				throw cleanupAndThrow(p, var4);
			} catch (Exception var5) {
				throw cleanupAndThrow(p, new EOFException(var5.getMessage()));
			}

			if (this.port == null && owner != null) {
				this.port = p;
				this.portOwner = owner;
			}

			if (this.portOwner == null) {
				this.portOwner = owner;
			}

			return p;
		}
	}

	private static IOException cleanupAndThrow(Protocol p, IOException ife) {
		try {
			p.quit();
		} catch (Throwable var3) {
			if (!isRecoverable(var3)) {
				var3.addSuppressed(ife);
				if (var3 instanceof Error) {
					throw (Error) var3;
				}

				if (var3 instanceof RuntimeException) {
					throw (RuntimeException) var3;
				}

				throw new RuntimeException("unexpected exception", var3);
			}

			ife.addSuppressed(var3);
		}

		return ife;
	}

	private boolean authenticate(Protocol p, String user, String passwd) throws MessagingException {
		String mechs = this.session.getProperty("mail." + this.name + ".auth.mechanisms");
		boolean usingDefaultMechs = false;
		if (mechs == null) {
			mechs = p.getDefaultMechanisms();
			usingDefaultMechs = true;
		}

		String authzid = this.session.getProperty("mail." + this.name + ".sasl.authorizationid");
		if (authzid == null) {
			authzid = user;
		}

		if (this.logger.isLoggable(Level.FINE)) {
			this.logger.fine("Attempt to authenticate using mechanisms: " + mechs);
		}

		StringTokenizer st = new StringTokenizer(mechs);

		String m;
		String dprop;
		label55 : while (true) {
			while (true) {
				while (st.hasMoreTokens()) {
					m = st.nextToken();
					m = m.toUpperCase(Locale.ENGLISH);
					if (p.supportsMechanism(m)) {
						if (p.supportsAuthentication(m)) {
							if (!usingDefaultMechs) {
								break label55;
							}

							dprop = "mail." + this.name + ".auth." + m.toLowerCase(Locale.ENGLISH) + ".disable";
							boolean disabled = PropUtil.getBooleanProperty(this.session.getProperties(), dprop,
									!p.isMechanismEnabled(m));
							if (!disabled) {
								break label55;
							}

							if (this.logger.isLoggable(Level.FINE)) {
								this.logger.fine("mechanism " + m + " disabled by property: " + dprop);
							}
						} else {
							this.logger.log(Level.FINE, "mechanism {0} not supported by server", m);
						}
					} else {
						this.logger.log(Level.FINE, "no authenticator for mechanism {0}", m);
					}
				}

				throw new AuthenticationFailedException(
						"No authentication mechanisms supported by both server and client");
			}
		}

		this.logger.log(Level.FINE, "Using mechanism {0}", m);
		dprop = p.authenticate(m, this.host, authzid, user, passwd);
		if (dprop != null) {
			throw new AuthenticationFailedException(dprop);
		} else {
			return true;
		}
	}

	private static boolean isRecoverable(Throwable t) {
		return t instanceof Exception || t instanceof LinkageError;
	}

	synchronized void closePort(POP3Folder owner) {
		if (this.portOwner == owner) {
			this.port = null;
			this.portOwner = null;
		}

	}

	public synchronized void close() throws MessagingException {
		this.close(false);
	}

	synchronized void close(boolean force) throws MessagingException {
		try {
			if (this.port != null) {
				if (force) {
					this.port.close();
				} else {
					this.port.quit();
				}
			}
		} catch (IOException var6) {
		} finally {
			this.port = null;
			super.close();
		}

	}

	public Folder getDefaultFolder() throws MessagingException {
		this.checkConnected();
		return new DefaultFolder(this);
	}

	public Folder getFolder(String name) throws MessagingException {
		this.checkConnected();
		return new POP3Folder(this, name);
	}

	public Folder getFolder(URLName url) throws MessagingException {
		this.checkConnected();
		return new POP3Folder(this, url.getFile());
	}

	public Map<String, String> capabilities() throws MessagingException {
		Map c;
		synchronized (this) {
			c = this.capabilities;
		}

		return c != null ? Collections.unmodifiableMap(c) : Collections.emptyMap();
	}

	public synchronized boolean isSSL() {
		return this.usingSSL;
	}

	protected void finalize() throws Throwable {
		try {
			if (this.port != null) {
				this.close(!this.finalizeCleanClose);
			}
		} finally {
			super.finalize();
		}

	}

	private void checkConnected() throws MessagingException {
		if (!super.isConnected()) {
			throw new MessagingException("Not connected");
		}
	}
}