package org.eclipse.angus.mail.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.Proxy.Type;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.SocketFactory;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class SocketFetcher {
	private static MailLogger logger;

	private SocketFetcher() {
	}

	public static Socket getSocket(String host, int port, Properties props, String prefix, boolean useSSL)
			throws IOException {
		if (logger.isLoggable(Level.FINER)) {
			logger.finer("getSocket, host " + host + ", port " + port + ", prefix " + prefix + ", useSSL " + useSSL);
		}

		if (prefix == null) {
			prefix = "socket";
		}

		if (props == null) {
			props = new Properties();
		}

		int cto = PropUtil.getIntProperty(props, prefix + ".connectiontimeout", -1);
		Socket socket = null;
		String localaddrstr = props.getProperty(prefix + ".localaddress", (String) null);
		InetAddress localaddr = null;
		if (localaddrstr != null) {
			localaddr = InetAddress.getByName(localaddrstr);
		}

		int localport = PropUtil.getIntProperty(props, prefix + ".localport", 0);
		boolean fb = PropUtil.getBooleanProperty(props, prefix + ".socketFactory.fallback", true);
		int sfPort = -1;
		String sfErr = "unknown socket factory";
		int to = PropUtil.getIntProperty(props, prefix + ".timeout", -1);

		try {
			SocketFactory sf = null;
			String sfPortName = null;
			Object sfo;
			String sfClass;
			if (useSSL) {
				sfo = props.get(prefix + ".ssl.socketFactory");
				if (sfo instanceof SocketFactory) {
					sf = (SocketFactory) sfo;
					(new StringBuilder()).append("SSL socket factory instance ").append(sf).toString();
				}

				if (sf == null) {
					sfClass = props.getProperty(prefix + ".ssl.socketFactory.class");
					sf = getSocketFactory(sfClass);
					(new StringBuilder()).append("SSL socket factory class ").append(sfClass).toString();
				}

				sfPortName = ".ssl.socketFactory.port";
			}

			if (sf == null) {
				sfo = props.get(prefix + ".socketFactory");
				if (sfo instanceof SocketFactory) {
					sf = (SocketFactory) sfo;
					(new StringBuilder()).append("socket factory instance ").append(sf).toString();
				}

				if (sf == null) {
					sfClass = props.getProperty(prefix + ".socketFactory.class");
					sf = getSocketFactory(sfClass);
					(new StringBuilder()).append("socket factory class ").append(sfClass).toString();
				}

				sfPortName = ".socketFactory.port";
			}

			if (sf != null) {
				sfPort = PropUtil.getIntProperty(props, prefix + sfPortName, -1);
				if (sfPort == -1) {
					sfPort = port;
				}

				socket = createSocket(localaddr, localport, host, sfPort, cto, to, props, prefix, sf, useSSL);
			}
		} catch (SocketTimeoutException var18) {
			throw var18;
		} catch (Exception var19) {
			Exception ex = var19;
			if (!fb) {
				if (var19 instanceof InvocationTargetException) {
					Throwable t = ((InvocationTargetException) var19).getTargetException();
					if (t instanceof Exception) {
						ex = (Exception) t;
					}
				}

				if (ex instanceof IOException) {
					throw (IOException) ex;
				}

				throw new SocketConnectException("Using " + sfErr, ex, host, sfPort, cto);
			}
		}

		if (socket == null) {
			socket = createSocket(localaddr, localport, host, port, cto, to, props, prefix, (SocketFactory) null,
					useSSL);
		} else if (to >= 0) {
			if (logger.isLoggable(Level.FINEST)) {
				logger.finest("set socket read timeout " + to);
			}

			socket.setSoTimeout(to);
		}

		return socket;
	}

	public static Socket getSocket(String host, int port, Properties props, String prefix) throws IOException {
		return getSocket(host, port, props, prefix, false);
	}

	private static Socket createSocket(InetAddress localaddr, int localport, String host, int port, int cto, int to,
			Properties props, String prefix, SocketFactory sf, boolean useSSL) throws IOException {
		Socket socket = null;
		if (logger.isLoggable(Level.FINEST)) {
			logger.finest("create socket: prefix " + prefix + ", localaddr " + localaddr + ", localport " + localport
					+ ", host " + host + ", port " + port + ", connection timeout " + cto + ", timeout " + to
					+ ", socket factory " + sf + ", useSSL " + useSSL);
		}

		String proxyHost = props.getProperty(prefix + ".proxy.host", (String) null);
		String proxyUser = props.getProperty(prefix + ".proxy.user", (String) null);
		String proxyPassword = props.getProperty(prefix + ".proxy.password", (String) null);
		int proxyPort = 80;
		String socksHost = null;
		int socksPort = 1080;
		String err = null;
		int writeTimeout;
		if (proxyHost != null) {
			writeTimeout = proxyHost.indexOf(58);
			if (writeTimeout >= 0) {
				try {
					proxyPort = Integer.parseInt(proxyHost.substring(writeTimeout + 1));
				} catch (NumberFormatException var26) {
				}

				proxyHost = proxyHost.substring(0, writeTimeout);
			}

			proxyPort = PropUtil.getIntProperty(props, prefix + ".proxy.port", proxyPort);
			err = "Using web proxy host, port: " + proxyHost + ", " + proxyPort;
			if (logger.isLoggable(Level.FINER)) {
				logger.finer("web proxy host " + proxyHost + ", port " + proxyPort);
				if (proxyUser != null) {
					logger.finer("web proxy user " + proxyUser + ", password "
							+ (proxyPassword == null ? "<null>" : "<non-null>"));
				}
			}
		} else if ((socksHost = props.getProperty(prefix + ".socks.host", (String) null)) != null) {
			writeTimeout = socksHost.indexOf(58);
			if (writeTimeout >= 0) {
				try {
					socksPort = Integer.parseInt(socksHost.substring(writeTimeout + 1));
				} catch (NumberFormatException var25) {
				}

				socksHost = socksHost.substring(0, writeTimeout);
			}

			socksPort = PropUtil.getIntProperty(props, prefix + ".socks.port", socksPort);
			err = "Using SOCKS host, port: " + socksHost + ", " + socksPort;
			if (logger.isLoggable(Level.FINER)) {
				logger.finer("socks host " + socksHost + ", port " + socksPort);
			}
		}

		if (sf != null && !(sf instanceof SSLSocketFactory)) {
			socket = ((SocketFactory) sf).createSocket();
		}

		if (socket == null) {
			if (socksHost != null) {
				socket = new Socket(new Proxy(Type.SOCKS, new InetSocketAddress(socksHost, socksPort)));
			} else if (PropUtil.getBooleanProperty(props, prefix + ".usesocketchannels", false)) {
				logger.finer("using SocketChannels");
				socket = SocketChannel.open().socket();
			} else {
				socket = new Socket();
			}
		}

		if (to >= 0) {
			if (logger.isLoggable(Level.FINEST)) {
				logger.finest("set socket read timeout " + to);
			}

			((Socket) socket).setSoTimeout(to);
		}

		writeTimeout = PropUtil.getIntProperty(props, prefix + ".writetimeout", -1);
		if (writeTimeout != -1) {
			if (logger.isLoggable(Level.FINEST)) {
				logger.finest("set socket write timeout " + writeTimeout);
			}

			ScheduledExecutorService executorService = PropUtil.getScheduledExecutorServiceProperty(props,
					prefix + ".executor.writetimeout");
			socket = executorService == null
					? new WriteTimeoutSocket((Socket) socket, writeTimeout)
					: new WriteTimeoutSocket((Socket) socket, writeTimeout, executorService);
		}

		if (localaddr != null) {
			((Socket) socket).bind(new InetSocketAddress(localaddr, localport));
		}

		try {
			logger.finest("connecting...");
			if (proxyHost != null) {
				proxyConnect((Socket) socket, proxyHost, proxyPort, proxyUser, proxyPassword, host, port, cto);
			} else if (cto >= 0) {
				((Socket) socket).connect(new InetSocketAddress(host, port), cto);
			} else {
				((Socket) socket).connect(new InetSocketAddress(host, port));
			}

			logger.finest("success!");
		} catch (IOException var24) {
			logger.log(Level.FINEST, "connection failed", var24);
			throw new SocketConnectException(err, var24, host, port, cto);
		}

		if ((useSSL || sf instanceof SSLSocketFactory) && !(socket instanceof SSLSocket)) {
			Object ssf;
			String trusted;
			if ((trusted = props.getProperty(prefix + ".ssl.trust")) != null) {
				try {
					MailSSLSocketFactory msf = new MailSSLSocketFactory();
					if (trusted.equals("*")) {
						msf.setTrustAllHosts(true);
					} else {
						msf.setTrustedHosts(trusted.split("\\s+"));
					}

					ssf = msf;
				} catch (GeneralSecurityException var23) {
					IOException ioex = new IOException("Can't create MailSSLSocketFactory");
					ioex.initCause(var23);
					throw ioex;
				}
			} else if (sf instanceof SSLSocketFactory) {
				ssf = (SSLSocketFactory) sf;
			} else {
				ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
			}

			socket = ((SSLSocketFactory) ssf).createSocket((Socket) socket, host, port, true);
			sf = ssf;
		}

		configureSSLSocket((Socket) socket, host, props, prefix, (SocketFactory) sf);
		return (Socket) socket;
	}

	private static SocketFactory getSocketFactory(String sfClass)
			throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		if (sfClass != null && sfClass.length() != 0) {
			ClassLoader cl = getContextClassLoader();
			Class<?> clsSockFact = null;
			if (cl != null) {
				try {
					clsSockFact = Class.forName(sfClass, false, cl);
				} catch (ClassNotFoundException var5) {
				}
			}

			if (clsSockFact == null) {
				clsSockFact = Class.forName(sfClass);
			}

			Method mthGetDefault = clsSockFact.getMethod("getDefault");
			SocketFactory sf = (SocketFactory) mthGetDefault.invoke(new Object());
			return sf;
		} else {
			return null;
		}
	}

	@Deprecated
	public static Socket startTLS(Socket socket) throws IOException {
		return startTLS(socket, new Properties(), "socket");
	}

	@Deprecated
	public static Socket startTLS(Socket socket, Properties props, String prefix) throws IOException {
		InetAddress a = socket.getInetAddress();
		String host = a.getHostName();
		return startTLS(socket, host, props, prefix);
	}

	public static Socket startTLS(Socket socket, String host, Properties props, String prefix) throws IOException {
		int port = socket.getPort();
		if (logger.isLoggable(Level.FINER)) {
			logger.finer("startTLS host " + host + ", port " + port);
		}

		String sfErr = "unknown socket factory";

		try {
			SSLSocketFactory ssf = null;
			SocketFactory sf = null;
			Object sfo = props.get(prefix + ".ssl.socketFactory");
			if (sfo instanceof SocketFactory) {
				sf = (SocketFactory) sfo;
				(new StringBuilder()).append("SSL socket factory instance ").append(sf).toString();
			}

			String trusted;
			if (sf == null) {
				trusted = props.getProperty(prefix + ".ssl.socketFactory.class");
				sf = getSocketFactory(trusted);
				(new StringBuilder()).append("SSL socket factory class ").append(trusted).toString();
			}

			if (sf != null && sf instanceof SSLSocketFactory) {
				ssf = (SSLSocketFactory) sf;
			}

			if (ssf == null) {
				sfo = props.get(prefix + ".socketFactory");
				if (sfo instanceof SocketFactory) {
					sf = (SocketFactory) sfo;
					(new StringBuilder()).append("socket factory instance ").append(sf).toString();
				}

				if (sf == null) {
					trusted = props.getProperty(prefix + ".socketFactory.class");
					sf = getSocketFactory(trusted);
					(new StringBuilder()).append("socket factory class ").append(trusted).toString();
				}

				if (sf != null && sf instanceof SSLSocketFactory) {
					ssf = (SSLSocketFactory) sf;
				}
			}

			if (ssf == null) {
				if ((trusted = props.getProperty(prefix + ".ssl.trust")) != null) {
					try {
						MailSSLSocketFactory msf = new MailSSLSocketFactory();
						if (trusted.equals("*")) {
							msf.setTrustAllHosts(true);
						} else {
							msf.setTrustedHosts(trusted.split("\\s+"));
						}

						ssf = msf;
						sfErr = "mail SSL socket factory";
					} catch (GeneralSecurityException var12) {
						IOException ioex = new IOException("Can't create MailSSLSocketFactory");
						ioex.initCause(var12);
						throw ioex;
					}
				} else {
					ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
					sfErr = "default SSL socket factory";
				}
			}

			socket = ((SSLSocketFactory) ssf).createSocket(socket, host, port, true);
			configureSSLSocket(socket, host, props, prefix, (SocketFactory) ssf);
			return socket;
		} catch (Exception var13) {
			Exception ex = var13;
			if (var13 instanceof InvocationTargetException) {
				Throwable t = ((InvocationTargetException) var13).getTargetException();
				if (t instanceof Exception) {
					ex = (Exception) t;
				}
			}

			if (ex instanceof IOException) {
				throw (IOException) ex;
			} else {
				IOException ioex = new IOException("Exception in startTLS using " + sfErr + ": host, port: " + host
						+ ", " + port + "; Exception: " + ex);
				ioex.initCause(ex);
				throw ioex;
			}
		}
	}

	private static void configureSSLSocket(Socket socket, String host, Properties props, String prefix,
			SocketFactory sf) throws IOException {
		if (socket instanceof SSLSocket) {
			SSLSocket sslsocket = (SSLSocket) socket;
			String protocols = props.getProperty(prefix + ".ssl.protocols", (String) null);
			if (protocols != null) {
				sslsocket.setEnabledProtocols(stringArray(protocols));
			} else {
				String[] prots = sslsocket.getEnabledProtocols();
				if (logger.isLoggable(Level.FINER)) {
					logger.finer("SSL enabled protocols before " + Arrays.asList(prots));
				}

				List<String> eprots = new ArrayList();

				for (int i = 0; i < prots.length; ++i) {
					if (prots[i] != null && !prots[i].startsWith("SSL")) {
						eprots.add(prots[i]);
					}
				}

				sslsocket.setEnabledProtocols((String[]) eprots.toArray(new String[0]));
			}

			String ciphers = props.getProperty(prefix + ".ssl.ciphersuites", (String) null);
			if (ciphers != null) {
				sslsocket.setEnabledCipherSuites(stringArray(ciphers));
			}

			if (logger.isLoggable(Level.FINER)) {
				logger.finer("SSL enabled protocols after " + Arrays.asList(sslsocket.getEnabledProtocols()));
				logger.finer("SSL enabled ciphers after " + Arrays.asList(sslsocket.getEnabledCipherSuites()));
			}

			sslsocket.startHandshake();
			boolean idCheck = PropUtil.getBooleanProperty(props, prefix + ".ssl.checkserveridentity", true);
			if (idCheck) {
				checkServerIdentity(host, sslsocket);
			}

			if (sf instanceof MailSSLSocketFactory) {
				MailSSLSocketFactory msf = (MailSSLSocketFactory) sf;
				if (!msf.isServerTrusted(host, sslsocket)) {
					throw cleanupAndThrow(sslsocket, new IOException("Server is not trusted: " + host));
				}
			}

		}
	}

	private static IOException cleanupAndThrow(Socket socket, IOException ife) {
		try {
			socket.close();
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

	private static boolean isRecoverable(Throwable t) {
		return t instanceof Exception || t instanceof LinkageError;
	}

	private static void checkServerIdentity(String server, SSLSocket sslSocket) throws IOException {
		try {
			Certificate[] certChain = sslSocket.getSession().getPeerCertificates();
			if (certChain != null && certChain.length > 0 && certChain[0] instanceof X509Certificate
					&& matchCert(server, (X509Certificate) certChain[0])) {
				return;
			}
		} catch (SSLPeerUnverifiedException var4) {
			sslSocket.close();
			IOException ioex = new IOException("Can't verify identity of server: " + server);
			ioex.initCause(var4);
			throw ioex;
		}

		sslSocket.close();
		throw new IOException("Can't verify identity of server: " + server);
	}

	private static boolean matchCert(String server, X509Certificate cert) {
		if (logger.isLoggable(Level.FINER)) {
			logger.finer("matchCert server " + server + ", cert " + cert);
		}

		try {
			Class<?> hnc = Class.forName("sun.security.util.HostnameChecker");
			Method getInstance = hnc.getMethod("getInstance", Byte.TYPE);
			Object hostnameChecker = getInstance.invoke(new Object(), 2);
			if (logger.isLoggable(Level.FINER)) {
				logger.finer("using sun.security.util.HostnameChecker");
			}

			Method match = hnc.getMethod("match", String.class, X509Certificate.class);

			try {
				match.invoke(hostnameChecker, server, cert);
				return true;
			} catch (InvocationTargetException var8) {
				logger.log(Level.FINER, "HostnameChecker FAIL", var8);
				return false;
			}
		} catch (Exception var10) {
			logger.log(Level.FINER, "NO sun.security.util.HostnameChecker", var10);

			try {
				Collection<List<?>> names = cert.getSubjectAlternativeNames();
				if (names != null) {
					boolean foundName = false;
					Iterator<List<?>> it = names.iterator();

					while (it.hasNext()) {
						List<?> nameEnt = (List) it.next();
						Integer type = (Integer) nameEnt.get(0);
						if (type == 2) {
							foundName = true;
							String name = (String) nameEnt.get(1);
							if (logger.isLoggable(Level.FINER)) {
								logger.finer("found name: " + name);
							}

							if (matchServer(server, name)) {
								return true;
							}
						}
					}

					if (foundName) {
						return false;
					}
				}
			} catch (CertificateParsingException var9) {
			}

			Pattern p = Pattern.compile("CN=([^,]*)");
			Matcher m = p.matcher(cert.getSubjectX500Principal().getName());
			return m.find() && matchServer(server, m.group(1).trim());
		}
	}

	private static boolean matchServer(String server, String name) {
		if (logger.isLoggable(Level.FINER)) {
			logger.finer("match server " + server + " with " + name);
		}

		if (!name.startsWith("*.")) {
			return server.equalsIgnoreCase(name);
		} else {
			String tail = name.substring(2);
			if (tail.length() == 0) {
				return false;
			} else {
				int off = server.length() - tail.length();
				if (off < 1) {
					return false;
				} else {
					return server.charAt(off - 1) == '.' && server.regionMatches(true, off, tail, 0, tail.length());
				}
			}
		}
	}

	private static void proxyConnect(Socket socket, String proxyHost, int proxyPort, String proxyUser,
			String proxyPassword, String host, int port, int cto) throws IOException {
		if (logger.isLoggable(Level.FINE)) {
			logger.fine("connecting through proxy " + proxyHost + ":" + proxyPort + " to " + host + ":" + port);
		}

		if (cto >= 0) {
			socket.connect(new InetSocketAddress(proxyHost, proxyPort), cto);
		} else {
			socket.connect(new InetSocketAddress(proxyHost, proxyPort));
		}

		PrintStream os = new PrintStream(socket.getOutputStream(), false, StandardCharsets.UTF_8.name());
		StringBuilder requestBuilder = new StringBuilder();
		requestBuilder.append("CONNECT ").append(host).append(":").append(port).append(" HTTP/1.1\r\n");
		requestBuilder.append("Host: ").append(host).append(":").append(port).append("\r\n");
		if (proxyUser != null && proxyPassword != null) {
			byte[] upbytes = (proxyUser + ':' + proxyPassword).getBytes(StandardCharsets.UTF_8);
			String proxyHeaderValue = new String(Base64.getEncoder().encode(upbytes), StandardCharsets.US_ASCII);
			requestBuilder.append("Proxy-Authorization: Basic ").append(proxyHeaderValue).append("\r\n");
		}

		requestBuilder.append("Proxy-Connection: keep-alive\r\n\r\n");
		os.print(requestBuilder.toString());
		os.flush();
		StringBuilder errorLine = new StringBuilder();
		if (!readProxyResponse(socket.getInputStream(), errorLine)) {
			try {
				socket.close();
			} catch (IOException var12) {
			}

			ConnectException ex = new ConnectException("connection through proxy " + proxyHost + ":" + proxyPort
					+ " to " + host + ":" + port + " failed: " + errorLine.toString());
			logger.log(Level.FINE, "connect failed", ex);
			throw ex;
		}
	}

	static boolean readProxyResponse(InputStream input, StringBuilder errorLine) throws IOException {
		LineInputStream r = new LineInputStream(input, true);
		boolean first = true;

		String line;
		while ((line = r.readLine()) != null && line.length() != 0) {
			logger.finest(line);
			if (first) {
				StringTokenizer st = new StringTokenizer(line);
				String http = st.nextToken();
				String code = st.nextToken();
				if (!code.equals("200")) {
					errorLine.append(line);
					return false;
				}

				first = false;
			}
		}

		return true;
	}

	private static String[] stringArray(String s) {
		StringTokenizer st = new StringTokenizer(s);
		List<String> tokens = new ArrayList();

		while (st.hasMoreTokens()) {
			tokens.add(st.nextToken());
		}

		return (String[]) tokens.toArray(new String[0]);
	}

	private static ClassLoader getContextClassLoader() {
		return (ClassLoader) AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
			public ClassLoader run() {
				ClassLoader cl = null;

				try {
					cl = Thread.currentThread().getContextClassLoader();
				} catch (SecurityException var3) {
				}

				return cl;
			}
		});
	}

	static {
		logger = new MailLogger(SocketFetcher.class, "socket", "DEBUG SocketFetcher",
				PropUtil.getBooleanSystemProperty("mail.socket.debug", false), System.out);
	}
}