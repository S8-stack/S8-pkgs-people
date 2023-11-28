package org.eclipse.angus.mail.pop3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import javax.net.ssl.SSLSocket;
import org.eclipse.angus.mail.auth.Ntlm;
import org.eclipse.angus.mail.util.ASCIIUtility;
import org.eclipse.angus.mail.util.BASE64EncoderStream;
import org.eclipse.angus.mail.util.LineInputStream;
import org.eclipse.angus.mail.util.MailLogger;
import org.eclipse.angus.mail.util.PropUtil;
import org.eclipse.angus.mail.util.SharedByteArrayOutputStream;
import org.eclipse.angus.mail.util.SocketFetcher;
import org.eclipse.angus.mail.util.TraceInputStream;
import org.eclipse.angus.mail.util.TraceOutputStream;

class Protocol {
	private Socket socket;
	private String host;
	private Properties props;
	private String prefix;
	private BufferedReader input;
	private PrintWriter output;
	private TraceInputStream traceInput;
	private TraceOutputStream traceOutput;
	private MailLogger logger;
	private MailLogger traceLogger;
	private String apopChallenge = null;
	private Map<String, String> capabilities = null;
	private boolean pipelining;
	private boolean noauthdebug = true;
	private boolean traceSuspended;
	private Map<String, Authenticator> authenticators = new HashMap();
	private String defaultAuthenticationMechanisms;
	private String localHostName;
	private static final int POP3_PORT = 110;
	private static final String CRLF = "\r\n";
	private static final int SLOP = 128;
	private static char[] digits = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
			'f'};

	Protocol(String host, int port, MailLogger logger, Properties props, String prefix, boolean isSSL)
			throws IOException {
		this.host = host;
		this.props = props;
		this.prefix = prefix;
		this.logger = logger;
		this.traceLogger = logger.getSubLogger("protocol", (String) null);
		this.noauthdebug = !PropUtil.getBooleanProperty(props, "mail.debug.auth", false);
		boolean enableAPOP = this.getBoolProp(props, prefix + ".apop.enable");
		boolean disableCapa = this.getBoolProp(props, prefix + ".disablecapa");

		Response r;
		try {
			if (port == -1) {
				port = 110;
			}

			if (logger.isLoggable(Level.FINE)) {
				logger.fine("connecting to host \"" + host + "\", port " + port + ", isSSL " + isSSL);
			}

			this.socket = SocketFetcher.getSocket(host, port, props, prefix, isSSL);
			this.initStreams();
			r = this.simpleCommand((String) null);
		} catch (IOException var13) {
			throw cleanupAndThrow(this.socket, var13);
		}

		if (!r.ok) {
			throw cleanupAndThrow(this.socket, new IOException("Connect failed"));
		} else {
			if (enableAPOP && r.data != null) {
				int challStart = r.data.indexOf(60);
				int challEnd = r.data.indexOf(62, challStart);
				if (challStart != -1 && challEnd != -1) {
					this.apopChallenge = r.data.substring(challStart, challEnd + 1);
				}

				logger.log(Level.FINE, "APOP challenge: {0}", this.apopChallenge);
			}

			if (!disableCapa) {
				this.setCapabilities(this.capa());
			}

			this.pipelining = this.hasCapability("PIPELINING")
					|| PropUtil.getBooleanProperty(props, prefix + ".pipelining", false);
			if (this.pipelining) {
				logger.config("PIPELINING enabled");
			}

			Authenticator[] a = new Authenticator[]{new LoginAuthenticator(), new PlainAuthenticator(),
					new NtlmAuthenticator(), new OAuth2Authenticator()};
			StringBuilder sb = new StringBuilder();

			for (int i = 0; i < a.length; ++i) {
				this.authenticators.put(a[i].getMechanism(), a[i]);
				sb.append(a[i].getMechanism()).append(' ');
			}

			this.defaultAuthenticationMechanisms = sb.toString();
		}
	}

	private static IOException cleanupAndThrow(Socket socket, IOException ife) {
		if (socket != null) {
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
		}

		return ife;
	}

	private static boolean isRecoverable(Throwable t) {
		return t instanceof Exception || t instanceof LinkageError;
	}

	private final synchronized boolean getBoolProp(Properties props, String prop) {
		boolean val = PropUtil.getBooleanProperty(props, prop, false);
		if (this.logger.isLoggable(Level.CONFIG)) {
			this.logger.config(prop + ": " + val);
		}

		return val;
	}

	private void initStreams() throws IOException {
		boolean quote = PropUtil.getBooleanProperty(this.props, "mail.debug.quote", false);
		this.traceInput = new TraceInputStream(this.socket.getInputStream(), this.traceLogger);
		this.traceInput.setQuote(quote);
		this.traceOutput = new TraceOutputStream(this.socket.getOutputStream(), this.traceLogger);
		this.traceOutput.setQuote(quote);
		this.input = new BufferedReader(new InputStreamReader(this.traceInput, StandardCharsets.ISO_8859_1));
		this.output = new PrintWriter(
				new BufferedWriter(new OutputStreamWriter(this.traceOutput, StandardCharsets.ISO_8859_1)));
	}

	protected void finalize() throws Throwable {
		try {
			if (this.socket != null) {
				this.quit();
			}
		} finally {
			super.finalize();
		}

	}

	synchronized void setCapabilities(InputStream in) {
		if (in == null) {
			this.capabilities = null;
		} else {
			this.capabilities = new HashMap(10);
			BufferedReader r = null;
			r = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));

			try {
				String s;
				String cap;
				try {
					for (; (s = r.readLine()) != null; this.capabilities.put(cap.toUpperCase(Locale.ENGLISH), s)) {
						cap = s;
						int i = s.indexOf(32);
						if (i > 0) {
							cap = s.substring(0, i);
						}
					}
				} catch (IOException var14) {
				}
			} finally {
				try {
					in.close();
				} catch (IOException var13) {
				}

			}

		}
	}

	synchronized boolean hasCapability(String c) {
		return this.capabilities != null && this.capabilities.containsKey(c.toUpperCase(Locale.ENGLISH));
	}

	synchronized Map<String, String> getCapabilities() {
		return this.capabilities;
	}

	boolean supportsMechanism(String mech) {
		return this.authenticators.containsKey(mech.toUpperCase(Locale.ENGLISH));
	}

	String getDefaultMechanisms() {
		return this.defaultAuthenticationMechanisms;
	}

	boolean isMechanismEnabled(String mech) {
		Authenticator a = (Authenticator) this.authenticators.get(mech.toUpperCase(Locale.ENGLISH));
		return a != null && a.enabled();
	}

	synchronized String authenticate(String mech, String host, String authzid, String user, String passwd) {
		Authenticator a = (Authenticator) this.authenticators.get(mech.toUpperCase(Locale.ENGLISH));
		if (a == null) {
			return "No such authentication mechanism: " + mech;
		} else {
			try {
				return !a.authenticate(host, authzid, user, passwd) ? "login failed" : null;
			} catch (IOException var8) {
				return var8.getMessage();
			}
		}
	}

	synchronized boolean supportsAuthentication(String auth) {
		assert Thread.holdsLock(this);

		if (auth.equals("LOGIN")) {
			return true;
		} else if (this.capabilities == null) {
			return false;
		} else {
			String a = (String) this.capabilities.get("SASL");
			if (a == null) {
				return false;
			} else {
				StringTokenizer st = new StringTokenizer(a);

				String tok;
				do {
					if (!st.hasMoreTokens()) {
						return false;
					}

					tok = st.nextToken();
				} while (!tok.equalsIgnoreCase(auth));

				return true;
			}
		}
	}

	synchronized String login(String user, String password) throws IOException {
		boolean batch = this.pipelining && this.socket instanceof SSLSocket;

		try {
			if (this.noauthdebug && this.isTracing()) {
				this.logger.fine("authentication command trace suppressed");
				this.suspendTracing();
			}

			String dpw = null;
			if (this.apopChallenge != null) {
				dpw = this.getDigest(password);
			}

			Response r;
			String cmd;
			if (this.apopChallenge != null && dpw != null) {
				r = this.simpleCommand("APOP " + user + " " + dpw);
			} else if (batch) {
				cmd = "USER " + user;
				this.batchCommandStart(cmd);
				this.issueCommand(cmd);
				cmd = "PASS " + password;
				this.batchCommandContinue(cmd);
				this.issueCommand(cmd);
				r = this.readResponse();
				if (!r.ok) {
					String err = r.data != null ? r.data : "USER command failed";
					this.readResponse();
					this.batchCommandEnd();
					String var8 = err;
					return var8;
				}

				r = this.readResponse();
				this.batchCommandEnd();
			} else {
				r = this.simpleCommand("USER " + user);
				if (!r.ok) {
					cmd = r.data != null ? r.data : "USER command failed";
					return cmd;
				}

				r = this.simpleCommand("PASS " + password);
			}

			if (this.noauthdebug && this.isTracing()) {
				this.logger.log(Level.FINE, "authentication command {0}", r.ok ? "succeeded" : "failed");
			}

			if (!r.ok) {
				cmd = r.data != null ? r.data : "login failed";
				return cmd;
			} else {
				cmd = null;
				return cmd;
			}
		} finally {
			this.resumeTracing();
		}
	}

	private String getDigest(String password) {
		String key = this.apopChallenge + password;

		byte[] digest;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			digest = md.digest(key.getBytes(StandardCharsets.ISO_8859_1));
		} catch (NoSuchAlgorithmException var5) {
			return null;
		}

		return toHex(digest);
	}

	private synchronized String getLocalHost() {
		InetAddress localHost;
		try {
			if (this.localHostName == null || this.localHostName.length() == 0) {
				localHost = InetAddress.getLocalHost();
				this.localHostName = localHost.getCanonicalHostName();
				if (this.localHostName == null) {
					this.localHostName = "[" + localHost.getHostAddress() + "]";
				}
			}
		} catch (UnknownHostException var2) {
		}

		if ((this.localHostName == null || this.localHostName.length() <= 0) && this.socket != null
				&& this.socket.isBound()) {
			localHost = this.socket.getLocalAddress();
			this.localHostName = localHost.getCanonicalHostName();
			if (this.localHostName == null) {
				this.localHostName = "[" + localHost.getHostAddress() + "]";
			}
		}

		return this.localHostName;
	}

	private static String toHex(byte[] bytes) {
		char[] result = new char[bytes.length * 2];
		int index = 0;

		for (int i = 0; index < bytes.length; ++index) {
			int temp = bytes[index] & 255;
			result[i++] = digits[temp >> 4];
			result[i++] = digits[temp & 15];
		}

		return new String(result);
	}

	synchronized boolean quit() throws IOException {
		boolean ok = false;

		try {
			Response r = this.simpleCommand("QUIT");
			ok = r.ok;
		} finally {
			this.close();
		}

		return ok;
	}

	void close() {
		try {
			if (this.socket != null) {
				this.socket.close();
			}
		} catch (IOException var5) {
		} finally {
			this.socket = null;
			this.input = null;
			this.output = null;
		}

	}

	synchronized Status stat() throws IOException {
		Response r = this.simpleCommand("STAT");
		Status s = new Status();
		if (!r.ok) {
			throw new IOException("STAT command failed: " + r.data);
		} else {
			if (r.data != null) {
				try {
					StringTokenizer st = new StringTokenizer(r.data);
					s.total = Integer.parseInt(st.nextToken());
					s.size = Integer.parseInt(st.nextToken());
				} catch (RuntimeException var4) {
				}
			}

			return s;
		}
	}

	synchronized int list(int msg) throws IOException {
		Response r = this.simpleCommand("LIST " + msg);
		int size = -1;
		if (r.ok && r.data != null) {
			try {
				StringTokenizer st = new StringTokenizer(r.data);
				st.nextToken();
				size = Integer.parseInt(st.nextToken());
			} catch (RuntimeException var5) {
			}
		}

		return size;
	}

	synchronized InputStream list() throws IOException {
		Response r = this.multilineCommand("LIST", 128);
		return r.bytes;
	}

	synchronized InputStream retr(int msg, int size) throws IOException {
		boolean batch = size == 0 && this.pipelining;
		Response r;
		String cmd;
		StringTokenizer st;
		if (batch) {
			cmd = "LIST " + msg;
			this.batchCommandStart(cmd);
			this.issueCommand(cmd);
			cmd = "RETR " + msg;
			this.batchCommandContinue(cmd);
			this.issueCommand(cmd);
			r = this.readResponse();
			if (r.ok && r.data != null) {
				try {
					st = new StringTokenizer(r.data);
					st.nextToken();
					size = Integer.parseInt(st.nextToken());
					if (size <= 1073741824 && size >= 0) {
						if (this.logger.isLoggable(Level.FINE)) {
							this.logger.fine("pipeline message size " + size);
						}

						size += 128;
					} else {
						size = 0;
					}
				} catch (RuntimeException var9) {
				}
			}

			r = this.readResponse();
			if (r.ok) {
				r.bytes = this.readMultilineResponse(size + 128);
			}

			this.batchCommandEnd();
		} else {
			cmd = "RETR " + msg;
			this.multilineCommandStart(cmd);
			this.issueCommand(cmd);
			r = this.readResponse();
			if (!r.ok) {
				this.multilineCommandEnd();
				return null;
			}

			if (size <= 0 && r.data != null) {
				try {
					st = new StringTokenizer(r.data);
					String s = st.nextToken();
					String octets = st.nextToken();
					if (octets.equals("octets")) {
						size = Integer.parseInt(s);
						if (size <= 1073741824 && size >= 0) {
							if (this.logger.isLoggable(Level.FINE)) {
								this.logger.fine("guessing message size: " + size);
							}

							size += 128;
						} else {
							size = 0;
						}
					}
				} catch (RuntimeException var10) {
				}
			}

			r.bytes = this.readMultilineResponse(size);
			this.multilineCommandEnd();
		}

		if (r.ok && size > 0 && this.logger.isLoggable(Level.FINE)) {
			this.logger.fine("got message size " + r.bytes.available());
		}

		return r.bytes;
	}

	synchronized boolean retr(int msg, OutputStream os) throws IOException {
		String cmd = "RETR " + msg;
		this.multilineCommandStart(cmd);
		this.issueCommand(cmd);
		Response r = this.readResponse();
		if (!r.ok) {
			this.multilineCommandEnd();
			return false;
		} else {
			Throwable terr = null;
			int lastb = 10;

			int b;
			try {
				for (; (b = this.input.read()) >= 0; lastb = b) {
					if (lastb == 10 && b == 46) {
						b = this.input.read();
						if (b == 13) {
							b = this.input.read();
							break;
						}
					}

					if (terr == null) {
						try {
							os.write(b);
						} catch (RuntimeException | IOException var11) {
							this.logger.log(Level.FINE, "exception while streaming", var11);
							terr = var11;
						}
					}
				}
			} catch (InterruptedIOException var12) {
				try {
					this.socket.close();
				} catch (IOException var10) {
				}

				throw var12;
			}

			if (b < 0) {
				throw new EOFException("EOF on socket");
			} else {
				if (terr != null) {
					if (terr instanceof IOException) {
						throw (IOException) terr;
					}

					if (terr instanceof RuntimeException) {
						throw (RuntimeException) terr;
					}

					assert false;
				}

				this.multilineCommandEnd();
				return true;
			}
		}
	}

	synchronized InputStream top(int msg, int n) throws IOException {
		Response r = this.multilineCommand("TOP " + msg + " " + n, 0);
		return r.bytes;
	}

	synchronized boolean dele(int msg) throws IOException {
		Response r = this.simpleCommand("DELE " + msg);
		return r.ok;
	}

	synchronized String uidl(int msg) throws IOException {
		Response r = this.simpleCommand("UIDL " + msg);
		if (!r.ok) {
			return null;
		} else {
			int i = r.data.indexOf(32);
			return i > 0 ? r.data.substring(i + 1) : null;
		}
	}

	synchronized boolean uidl(String[] uids) throws IOException {
		Response r = this.multilineCommand("UIDL", 15 * uids.length);
		if (!r.ok) {
			return false;
		} else {
			LineInputStream lis = new LineInputStream(r.bytes);
			String line = null;

			while ((line = lis.readLine()) != null) {
				int i = line.indexOf(32);
				if (i >= 1 && i < line.length()) {
					int n = Integer.parseInt(line.substring(0, i));
					if (n > 0 && n <= uids.length) {
						uids[n - 1] = line.substring(i + 1);
					}
				}
			}

			try {
				r.bytes.close();
			} catch (IOException var7) {
			}

			return true;
		}
	}

	synchronized boolean noop() throws IOException {
		Response r = this.simpleCommand("NOOP");
		return r.ok;
	}

	synchronized boolean rset() throws IOException {
		Response r = this.simpleCommand("RSET");
		return r.ok;
	}

	synchronized boolean stls() throws IOException {
		if (this.socket instanceof SSLSocket) {
			return true;
		} else {
			Response r = this.simpleCommand("STLS");
			if (r.ok) {
				try {
					this.socket = SocketFetcher.startTLS(this.socket, this.host, this.props, this.prefix);
					this.initStreams();
				} catch (IOException var7) {
					try {
						this.socket.close();
					} finally {
						this.socket = null;
						this.input = null;
						this.output = null;
					}

					IOException sioex = new IOException("Could not convert socket to TLS");
					sioex.initCause(var7);
					throw sioex;
				}
			}

			return r.ok;
		}
	}

	synchronized boolean isSSL() {
		return this.socket instanceof SSLSocket;
	}

	synchronized InputStream capa() throws IOException {
		Response r = this.multilineCommand("CAPA", 128);
		return !r.ok ? null : r.bytes;
	}

	private Response simpleCommand(String cmd) throws IOException {
		this.simpleCommandStart(cmd);
		this.issueCommand(cmd);
		Response r = this.readResponse();
		this.simpleCommandEnd();
		return r;
	}

	private Response twoLinesCommand(String firstCommand, String secondCommand) throws IOException {
		String cmd = firstCommand + " " + secondCommand;
		this.batchCommandStart(cmd);
		this.simpleCommand(firstCommand);
		this.batchCommandContinue(cmd);
		Response r = this.simpleCommand(secondCommand);
		this.batchCommandEnd();
		return r;
	}

	private void issueCommand(String cmd) throws IOException {
		if (this.socket == null) {
			throw new IOException("Folder is closed");
		} else {
			if (cmd != null) {
				cmd = cmd + "\r\n";
				this.output.print(cmd);
				this.output.flush();
			}

		}
	}

	private Response readResponse() throws IOException {
		String line = null;

		try {
			line = this.input.readLine();
		} catch (InterruptedIOException var6) {
			try {
				this.socket.close();
			} catch (IOException var5) {
			}

			throw new EOFException(var6.getMessage());
		} catch (SocketException var7) {
			try {
				this.socket.close();
			} catch (IOException var4) {
			}

			throw new EOFException(var7.getMessage());
		}

		if (line == null) {
			this.traceLogger.finest("<EOF>");
			throw new EOFException("EOF on socket");
		} else {
			Response r = new Response();
			if (line.startsWith("+OK")) {
				r.ok = true;
			} else if (line.startsWith("+ ")) {
				r.ok = true;
				r.cont = true;
			} else {
				if (!line.startsWith("-ERR")) {
					throw new IOException("Unexpected response: " + line);
				}

				r.ok = false;
			}

			int i;
			if ((i = line.indexOf(32)) >= 0) {
				r.data = line.substring(i + 1);
			}

			return r;
		}
	}

	private Response multilineCommand(String cmd, int size) throws IOException {
		this.multilineCommandStart(cmd);
		this.issueCommand(cmd);
		Response r = this.readResponse();
		if (!r.ok) {
			this.multilineCommandEnd();
			return r;
		} else {
			r.bytes = this.readMultilineResponse(size);
			this.multilineCommandEnd();
			return r;
		}
	}

	private InputStream readMultilineResponse(int size) throws IOException {
		SharedByteArrayOutputStream buf = new SharedByteArrayOutputStream(size);
		int lastb = 10;

		int b;
		try {
			while ((b = this.input.read()) >= 0) {
				if (lastb == 10 && b == 46) {
					b = this.input.read();
					if (b == 13) {
						b = this.input.read();
						break;
					}
				}

				buf.write(b);
				lastb = b;
			}
		} catch (InterruptedIOException var8) {
			try {
				this.socket.close();
			} catch (IOException var7) {
			}

			throw var8;
		}

		if (b < 0) {
			throw new EOFException("EOF on socket");
		} else {
			return buf.toStream();
		}
	}

	protected boolean isTracing() {
		return this.traceLogger.isLoggable(Level.FINEST);
	}

	private void suspendTracing() {
		if (this.traceLogger.isLoggable(Level.FINEST)) {
			this.traceInput.setTrace(false);
			this.traceOutput.setTrace(false);
		}

	}

	private void resumeTracing() {
		if (this.traceLogger.isLoggable(Level.FINEST)) {
			this.traceInput.setTrace(true);
			this.traceOutput.setTrace(true);
		}

	}

	private void simpleCommandStart(String command) {
	}

	private void simpleCommandEnd() {
	}

	private void multilineCommandStart(String command) {
	}

	private void multilineCommandEnd() {
	}

	private void batchCommandStart(String command) {
	}

	private void batchCommandContinue(String command) {
	}

	private void batchCommandEnd() {
	}

	private class OAuth2Authenticator extends Authenticator {
		OAuth2Authenticator() {
			super("XOAUTH2", false);
		}

		String getInitialResponse(String host, String authzid, String user, String passwd) throws IOException {
			String resp = "user=" + user + "auth=Bearer " + passwd + "";
			byte[] b = Base64.getEncoder().encode(resp.getBytes(StandardCharsets.UTF_8));
			return ASCIIUtility.toString(b);
		}

		protected void runAuthenticationCommand(String command, String ir) throws IOException {
			Boolean isTwoLineAuthenticationFormat = Protocol.this.getBoolProp(Protocol.this.props,
					Protocol.this.prefix + ".auth.xoauth2.two.line.authentication.format");
			if (isTwoLineAuthenticationFormat) {
				if (Protocol.this.logger.isLoggable(Level.FINE)) {
					Protocol.this.logger.fine(command + " using two line authentication format");
				}

				this.resp = Protocol.this.twoLinesCommand(command, ir.length() == 0 ? "=" : ir);
			} else {
				super.runAuthenticationCommand(command, ir);
			}

		}

		void doAuth(String host, String authzid, String user, String passwd) throws IOException {
			String err = "";
			if (this.resp.data != null) {
				byte[] b = this.resp.data.getBytes(StandardCharsets.UTF_8);
				b = Base64.getDecoder().decode(b);
				err = new String(b, StandardCharsets.UTF_8);
			}

			throw new EOFException("OAUTH2 authentication failed: " + err);
		}
	}

	private class NtlmAuthenticator extends Authenticator {
		private Ntlm ntlm;

		NtlmAuthenticator() {
			super("NTLM");
		}

		String getInitialResponse(String host, String authzid, String user, String passwd) throws IOException {
			this.ntlm = new Ntlm(Protocol.this.props.getProperty(Protocol.this.prefix + ".auth.ntlm.domain"),
					Protocol.this.getLocalHost(), user, passwd, Protocol.this.logger);
			int flags = PropUtil.getIntProperty(Protocol.this.props, Protocol.this.prefix + ".auth.ntlm.flags", 0);
			boolean v2 = PropUtil.getBooleanProperty(Protocol.this.props, Protocol.this.prefix + ".auth.ntlm.v2", true);
			String type1 = this.ntlm.generateType1Msg(flags, v2);
			return type1;
		}

		void doAuth(String host, String authzid, String user, String passwd) throws IOException {
			assert this.ntlm != null;

			String type3 = this.ntlm.generateType3Msg(this.resp.data.substring(4).trim());
			this.resp = Protocol.this.simpleCommand(type3);
		}
	}

	private class PlainAuthenticator extends Authenticator {
		PlainAuthenticator() {
			super("PLAIN");
		}

		String getInitialResponse(String host, String authzid, String user, String passwd) throws IOException {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			OutputStream b64os = new BASE64EncoderStream(bos, Integer.MAX_VALUE);
			if (authzid != null) {
				b64os.write(authzid.getBytes(StandardCharsets.UTF_8));
			}

			b64os.write(0);
			b64os.write(user.getBytes(StandardCharsets.UTF_8));
			b64os.write(0);
			b64os.write(passwd.getBytes(StandardCharsets.UTF_8));
			b64os.flush();
			return ASCIIUtility.toString(bos.toByteArray());
		}

		void doAuth(String host, String authzid, String user, String passwd) throws IOException {
			throw new EOFException("PLAIN asked for more");
		}
	}

	private class LoginAuthenticator extends Authenticator {
		LoginAuthenticator() {
			super("LOGIN");
		}

		boolean authenticate(String host, String authzid, String user, String passwd) throws IOException {
			String msg = null;
			if ((msg = Protocol.this.login(user, passwd)) != null) {
				throw new EOFException(msg);
			} else {
				return true;
			}
		}

		void doAuth(String host, String authzid, String user, String passwd) throws IOException {
			throw new EOFException("LOGIN asked for more");
		}
	}

	private abstract class Authenticator {
		protected Response resp;
		private final String mech;
		private final boolean enabled;

		Authenticator(String mech) {
			this(mech, true);
		}

		Authenticator(String mech, boolean enabled) {
			this.mech = mech.toUpperCase(Locale.ENGLISH);
			this.enabled = enabled;
		}

		String getMechanism() {
			return this.mech;
		}

		boolean enabled() {
			return this.enabled;
		}

		protected void runAuthenticationCommand(String command, String ir) throws IOException {
			if (Protocol.this.logger.isLoggable(Level.FINE)) {
				Protocol.this.logger.fine(command + " using one line authentication format");
			}

			if (ir != null) {
				this.resp = Protocol.this.simpleCommand(command + " " + (ir.length() == 0 ? "=" : ir));
			} else {
				this.resp = Protocol.this.simpleCommand(command);
			}

		}

		boolean authenticate(String host, String authzid, String user, String passwd) throws IOException {
			Throwable thrown = null;
			boolean var12 = false;

			EOFException ex;
			label439 : {
				label440 : {
					try {
						var12 = true;
						String ir = this.getInitialResponse(host, authzid, user, passwd);
						if (Protocol.this.noauthdebug && Protocol.this.isTracing()) {
							Protocol.this.logger.fine("AUTH " + this.mech + " command trace suppressed");
							Protocol.this.suspendTracing();
						}

						this.runAuthenticationCommand("AUTH " + this.mech, ir);
						if (this.resp.cont) {
							this.doAuth(host, authzid, user, passwd);
							var12 = false;
						} else {
							var12 = false;
						}
						break label439;
					} catch (IOException var13) {
						Protocol.this.logger.log(Level.FINE, "AUTH " + this.mech + " failed", var13);
						var12 = false;
					} catch (Throwable var14) {
						Protocol.this.logger.log(Level.FINE, "AUTH " + this.mech + " failed", var14);
						thrown = var14;
						var12 = false;
						break label440;
					} finally {
						if (var12) {
							if (Protocol.this.noauthdebug && Protocol.this.isTracing()) {
								Protocol.this.logger
										.fine("AUTH " + this.mech + " " + (this.resp.ok ? "succeeded" : "failed"));
							}

							Protocol.this.resumeTracing();
							if (!this.resp.ok) {
								Protocol.this.close();
								if (thrown != null) {
									if (thrown instanceof Error) {
										throw (Error) thrown;
									}

									if (thrown instanceof Exception) {
										EOFException exx = new EOFException(
												this.resp.data != null ? this.resp.data : "authentication failed");
										exx.initCause(thrown);
										throw exx;
									}

									assert false : "unknown Throwable";
								}

								throw new EOFException(
										this.resp.data != null ? this.resp.data : "authentication failed");
							}

						}
					}

					if (Protocol.this.noauthdebug && Protocol.this.isTracing()) {
						Protocol.this.logger.fine("AUTH " + this.mech + " " + (this.resp.ok ? "succeeded" : "failed"));
					}

					Protocol.this.resumeTracing();
					if (!this.resp.ok) {
						Protocol.this.close();
						if (thrown != null) {
							if (thrown instanceof Error) {
								throw (Error) thrown;
							}

							if (thrown instanceof Exception) {
								ex = new EOFException(
										this.resp.data != null ? this.resp.data : "authentication failed");
								ex.initCause(thrown);
								throw ex;
							}

							assert false : "unknown Throwable";
						}

						throw new EOFException(this.resp.data != null ? this.resp.data : "authentication failed");
					}

					return true;
				}

				if (Protocol.this.noauthdebug && Protocol.this.isTracing()) {
					Protocol.this.logger.fine("AUTH " + this.mech + " " + (this.resp.ok ? "succeeded" : "failed"));
				}

				Protocol.this.resumeTracing();
				if (!this.resp.ok) {
					Protocol.this.close();
					if (thrown != null) {
						if (thrown instanceof Error) {
							throw (Error) thrown;
						}

						if (thrown instanceof Exception) {
							ex = new EOFException(this.resp.data != null ? this.resp.data : "authentication failed");
							ex.initCause(thrown);
							throw ex;
						}

						assert false : "unknown Throwable";
					}

					throw new EOFException(this.resp.data != null ? this.resp.data : "authentication failed");
				}

				return true;
			}

			if (Protocol.this.noauthdebug && Protocol.this.isTracing()) {
				Protocol.this.logger.fine("AUTH " + this.mech + " " + (this.resp.ok ? "succeeded" : "failed"));
			}

			Protocol.this.resumeTracing();
			if (!this.resp.ok) {
				Protocol.this.close();
				if (thrown != null) {
					if (thrown instanceof Error) {
						throw (Error) thrown;
					}

					if (thrown instanceof Exception) {
						ex = new EOFException(this.resp.data != null ? this.resp.data : "authentication failed");
						ex.initCause(thrown);
						throw ex;
					}

					assert false : "unknown Throwable";
				}

				throw new EOFException(this.resp.data != null ? this.resp.data : "authentication failed");
			} else {
				return true;
			}
		}

		String getInitialResponse(String host, String authzid, String user, String passwd) throws IOException {
			return null;
		}

		abstract void doAuth(String var1, String var2, String var3, String var4) throws IOException;
	}
}