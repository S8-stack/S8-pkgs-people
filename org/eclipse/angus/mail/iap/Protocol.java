package org.eclipse.angus.mail.iap;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.net.ssl.SSLSocket;
import org.eclipse.angus.mail.util.MailLogger;
import org.eclipse.angus.mail.util.PropUtil;
import org.eclipse.angus.mail.util.SocketFetcher;
import org.eclipse.angus.mail.util.TraceInputStream;
import org.eclipse.angus.mail.util.TraceOutputStream;

public class Protocol {
	protected String host;
	private Socket socket;
	protected boolean quote;
	protected MailLogger logger;
	protected MailLogger traceLogger;
	protected Properties props;
	protected String prefix;
	private TraceInputStream traceInput;
	private volatile ResponseInputStream input;
	private TraceOutputStream traceOutput;
	private volatile DataOutputStream output;
	private int tagCounter = 0;
	private final String tagPrefix;
	private String localHostName;
	private final List<ResponseHandler> handlers = new CopyOnWriteArrayList();
	private volatile long timestamp;
	static final AtomicInteger tagNum = new AtomicInteger();
	private static final byte[] CRLF = new byte[]{13, 10};

	public Protocol(String host, int port, Properties props, String prefix, boolean isSSL, MailLogger logger)
			throws IOException, ProtocolException {
		boolean connected = false;
		this.tagPrefix = this.computePrefix(props, prefix);

		try {
			this.host = host;
			this.props = props;
			this.prefix = prefix;
			this.logger = logger;
			this.traceLogger = logger.getSubLogger("protocol", (String) null);
			this.socket = SocketFetcher.getSocket(host, port, props, prefix, isSSL);
			this.quote = PropUtil.getBooleanProperty(props, "mail.debug.quote", false);
			this.initStreams();
			this.processGreeting(this.readResponse());
			this.timestamp = System.currentTimeMillis();
			connected = true;
		} finally {
			if (!connected) {
				this.disconnect();
			}

		}

	}

	private void initStreams() throws IOException {
		this.traceInput = new TraceInputStream(this.socket.getInputStream(), this.traceLogger);
		this.traceInput.setQuote(this.quote);
		this.input = new ResponseInputStream(this.traceInput);
		this.traceOutput = new TraceOutputStream(this.socket.getOutputStream(), this.traceLogger);
		this.traceOutput.setQuote(this.quote);
		this.output = new DataOutputStream(new BufferedOutputStream(this.traceOutput));
	}

	private String computePrefix(Properties props, String prefix) {
		if (PropUtil.getBooleanProperty(props, prefix + ".reusetagprefix", false)) {
			return "A";
		} else {
			int n = tagNum.getAndIncrement() % 18278;
			String tagPrefix;
			if (n < 26) {
				tagPrefix = String.valueOf((char) (65 + n));
			} else if (n < 702) {
				n -= 26;
				tagPrefix = new String(new char[]{(char) (65 + n / 26), (char) (65 + n % 26)});
			} else {
				n -= 702;
				tagPrefix = new String(
						new char[]{(char) (65 + n / 676), (char) (65 + n % 676 / 26), (char) (65 + n % 26)});
			}

			return tagPrefix;
		}
	}

	public Protocol(InputStream in, PrintStream out, Properties props, boolean debug) throws IOException {
		this.host = "localhost";
		this.props = props;
		this.quote = false;
		this.tagPrefix = this.computePrefix(props, "mail.imap");
		this.logger = new MailLogger(this.getClass(), "DEBUG", debug, System.out);
		this.traceLogger = this.logger.getSubLogger("protocol", (String) null);
		this.traceInput = new TraceInputStream(in, this.traceLogger);
		this.traceInput.setQuote(this.quote);
		this.input = new ResponseInputStream(this.traceInput);
		this.traceOutput = new TraceOutputStream(out, this.traceLogger);
		this.traceOutput.setQuote(this.quote);
		this.output = new DataOutputStream(new BufferedOutputStream(this.traceOutput));
		this.timestamp = System.currentTimeMillis();
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public void addResponseHandler(ResponseHandler h) {
		this.handlers.add(h);
	}

	public void removeResponseHandler(ResponseHandler h) {
		this.handlers.remove(h);
	}

	public void notifyResponseHandlers(Response[] responses) {
		if (!this.handlers.isEmpty()) {
			Response[] var2 = responses;
			int var3 = responses.length;

			for (int var4 = 0; var4 < var3; ++var4) {
				Response r = var2[var4];
				if (r != null) {
					Iterator var6 = this.handlers.iterator();

					while (var6.hasNext()) {
						ResponseHandler rh = (ResponseHandler) var6.next();
						if (rh != null) {
							rh.handleResponse(r);
						}
					}
				}
			}

		}
	}

	protected void processGreeting(Response r) throws ProtocolException {
		if (r.isBYE()) {
			throw new ConnectionException(this, r);
		}
	}

	protected ResponseInputStream getInputStream() {
		return this.input;
	}

	protected OutputStream getOutputStream() {
		return this.output;
	}

	protected synchronized boolean supportsNonSyncLiterals() {
		return false;
	}

	public Response readResponse() throws IOException, ProtocolException {
		return new Response(this);
	}

	public boolean hasResponse() {
		try {
			return this.input.available() > 0;
		} catch (IOException var2) {
			return false;
		}
	}

	protected ByteArray getResponseBuffer() {
		return null;
	}

	public String writeCommand(String command, Argument args) throws IOException, ProtocolException {
		String tag = this.tagPrefix + Integer.toString(this.tagCounter++);
		this.output.writeBytes(tag + " " + command);
		if (args != null) {
			this.output.write(32);
			args.write(this);
		}

		this.output.write(CRLF);
		this.output.flush();
		return tag;
	}

	public synchronized Response[] command(String command, Argument args) {
		this.commandStart(command);
		List<Response> v = new ArrayList();
		boolean done = false;
		String tag = null;

		try {
			tag = this.writeCommand(command, args);
		} catch (LiteralException var9) {
			v.add(var9.getResponse());
			done = true;
		} catch (Exception var10) {
			v.add(Response.byeResponse(var10));
			done = true;
		}

		Response byeResp = null;

		while (!done) {
			Response r = null;

			try {
				r = this.readResponse();
			} catch (IOException var11) {
				if (byeResp == null) {
					byeResp = Response.byeResponse(var11);
				}
				break;
			} catch (ProtocolException var12) {
				this.logger.log(Level.FINE, "ignoring bad response", var12);
				continue;
			}

			if (r.isBYE()) {
				byeResp = r;
			} else {
				v.add(r);
				if (r.isTagged() && r.getTag().equals(tag)) {
					done = true;
				}
			}
		}

		if (byeResp != null) {
			v.add(byeResp);
		}

		Response[] responses = new Response[v.size()];
		v.toArray(responses);
		this.timestamp = System.currentTimeMillis();
		this.commandEnd();
		return responses;
	}

	public void handleResult(Response response) throws ProtocolException {
		if (!response.isOK()) {
			if (response.isNO()) {
				throw new CommandFailedException(response);
			} else if (response.isBAD()) {
				throw new BadCommandException(response);
			} else if (response.isBYE()) {
				this.disconnect();
				throw new ConnectionException(this, response);
			}
		}
	}

	public void simpleCommand(String cmd, Argument args) throws ProtocolException {
		Response[] r = this.command(cmd, args);
		this.notifyResponseHandlers(r);
		this.handleResult(r[r.length - 1]);
	}

	public synchronized void startTLS(String cmd) throws IOException, ProtocolException {
		if (!(this.socket instanceof SSLSocket)) {
			this.simpleCommand(cmd, (Argument) null);
			this.socket = SocketFetcher.startTLS(this.socket, this.host, this.props, this.prefix);
			this.initStreams();
		}
	}

	public synchronized void startCompression(String cmd) throws IOException, ProtocolException {
		this.simpleCommand(cmd, (Argument) null);
		Inflater inf = new Inflater(true);
		this.traceInput = new TraceInputStream(new InflaterInputStream(this.socket.getInputStream(), inf),
				this.traceLogger);
		this.traceInput.setQuote(this.quote);
		this.input = new ResponseInputStream(this.traceInput);
		int level = PropUtil.getIntProperty(this.props, this.prefix + ".compress.level", -1);
		int strategy = PropUtil.getIntProperty(this.props, this.prefix + ".compress.strategy", 0);
		if (this.logger.isLoggable(Level.FINE)) {
			this.logger.log(Level.FINE, "Creating Deflater with compression level {0} and strategy {1}",
					new Object[]{level, strategy});
		}

		Deflater def = new Deflater(-1, true);

		try {
			def.setLevel(level);
		} catch (IllegalArgumentException var8) {
			this.logger.log(Level.FINE, "Ignoring bad compression level", var8);
		}

		try {
			def.setStrategy(strategy);
		} catch (IllegalArgumentException var7) {
			this.logger.log(Level.FINE, "Ignoring bad compression strategy", var7);
		}

		this.traceOutput = new TraceOutputStream(new DeflaterOutputStream(this.socket.getOutputStream(), def, true),
				this.traceLogger);
		this.traceOutput.setQuote(this.quote);
		this.output = new DataOutputStream(new BufferedOutputStream(this.traceOutput));
	}

	public boolean isSSL() {
		return this.socket instanceof SSLSocket;
	}

	public InetAddress getInetAddress() {
		return this.socket.getInetAddress();
	}

	public SocketChannel getChannel() {
		if (PropUtil.getBooleanProperty(this.props, this.prefix + ".usesocketchannels", false)) {
			SocketChannel ret = this.socket.getChannel();
			if (ret == null && this.socket instanceof SSLSocket) {
				ret = findSocketChannel(this.socket);
			}

			return ret;
		} else {
			return null;
		}
	}

	private static SocketChannel findSocketChannel(Socket socket) {
		Class k;
		for (k = socket.getClass(); k != Object.class; k = k.getSuperclass()) {
			try {
				Field f = k.getDeclaredField("socket");
				f.setAccessible(true);
				Socket s = (Socket) f.get(socket);
				if (s != socket) {
					SocketChannel ret = s.getChannel();
					if (ret != null) {
						return ret;
					}
				}
			} catch (Exception var9) {
			}
		}

		for (k = socket.getClass(); k != Object.class; k = k.getSuperclass()) {
			try {
				Field[] var11 = k.getDeclaredFields();
				int var12 = var11.length;

				for (int var13 = 0; var13 < var12; ++var13) {
					Field f = var11[var13];
					if (Socket.class.isAssignableFrom(f.getType()) && !f.isSynthetic()) {
						try {
							f.setAccessible(true);
							Socket s = (Socket) f.get(socket);
							if (s != socket) {
								SocketChannel ret = s.getChannel();
								if (ret != null) {
									return ret;
								}
							}
						} catch (Exception var8) {
						}
					}
				}
			} catch (Exception var10) {
			}
		}

		return null;
	}

	public SocketAddress getLocalSocketAddress() {
		return this.socket.getLocalSocketAddress();
	}

	public boolean supportsUtf8() {
		return false;
	}

	protected synchronized void disconnect() {
		if (this.socket != null) {
			try {
				this.socket.close();
			} catch (IOException var2) {
			}

			this.socket = null;
		}

	}

	protected synchronized String getLocalHost() {
		if (this.localHostName == null || this.localHostName.length() <= 0) {
			this.localHostName = this.props.getProperty(this.prefix + ".localhost");
		}

		if (this.localHostName == null || this.localHostName.length() <= 0) {
			this.localHostName = this.props.getProperty(this.prefix + ".localaddress");
		}

		InetAddress localHost;
		try {
			if (this.localHostName == null || this.localHostName.length() <= 0) {
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

	protected boolean isTracing() {
		return this.traceLogger.isLoggable(Level.FINEST);
	}

	protected void suspendTracing() {
		if (this.traceLogger.isLoggable(Level.FINEST)) {
			this.traceInput.setTrace(false);
			this.traceOutput.setTrace(false);
		}

	}

	protected void resumeTracing() {
		if (this.traceLogger.isLoggable(Level.FINEST)) {
			this.traceInput.setTrace(true);
			this.traceOutput.setTrace(true);
		}

	}

	protected void finalize() throws Throwable {
		try {
			this.disconnect();
		} finally {
			super.finalize();
		}

	}

	private void commandStart(String command) {
	}

	private void commandEnd() {
	}
}