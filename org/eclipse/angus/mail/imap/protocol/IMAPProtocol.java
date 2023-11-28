package org.eclipse.angus.mail.imap.protocol;

import jakarta.mail.Flags;
import jakarta.mail.Quota;
import jakarta.mail.Flags.Flag;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.SearchException;
import jakarta.mail.search.SearchTerm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import org.eclipse.angus.mail.auth.Ntlm;
import org.eclipse.angus.mail.iap.Argument;
import org.eclipse.angus.mail.iap.BadCommandException;
import org.eclipse.angus.mail.iap.ByteArray;
import org.eclipse.angus.mail.iap.CommandFailedException;
import org.eclipse.angus.mail.iap.ConnectionException;
import org.eclipse.angus.mail.iap.Literal;
import org.eclipse.angus.mail.iap.LiteralException;
import org.eclipse.angus.mail.iap.ParsingException;
import org.eclipse.angus.mail.iap.Protocol;
import org.eclipse.angus.mail.iap.ProtocolException;
import org.eclipse.angus.mail.iap.Response;
import org.eclipse.angus.mail.imap.ACL;
import org.eclipse.angus.mail.imap.AppendUID;
import org.eclipse.angus.mail.imap.CopyUID;
import org.eclipse.angus.mail.imap.ResyncData;
import org.eclipse.angus.mail.imap.Rights;
import org.eclipse.angus.mail.imap.SortTerm;
import org.eclipse.angus.mail.imap.Utility;
import org.eclipse.angus.mail.util.ASCIIUtility;
import org.eclipse.angus.mail.util.BASE64EncoderStream;
import org.eclipse.angus.mail.util.MailLogger;
import org.eclipse.angus.mail.util.PropUtil;

public class IMAPProtocol extends Protocol {
	private boolean connected = false;
	private boolean rev1 = false;
	private boolean referralException;
	private boolean noauthdebug = true;
	private boolean authenticated;
	private Map<String, String> capabilities;
	private List<String> authmechs;
	private boolean utf8;
	protected SearchSequence searchSequence;
	protected String[] searchCharsets;
	protected Set<String> enabled;
	private String name;
	private SaslAuthenticator saslAuthenticator;
	private String proxyAuthUser;
	private ByteArray ba;
	private static final byte[] CRLF = new byte[]{13, 10};
	private static final FetchItem[] fetchItems = new FetchItem[0];
	private volatile String idleTag;
	private static final byte[] DONE = new byte[]{68, 79, 78, 69, 13, 10};

	public IMAPProtocol(String name, String host, int port, Properties props, boolean isSSL, MailLogger logger)
			throws IOException, ProtocolException {
		super(host, port, props, "mail." + name, isSSL, logger);

		try {
			this.name = name;
			this.noauthdebug = !PropUtil.getBooleanProperty(props, "mail.debug.auth", false);
			this.referralException = PropUtil.getBooleanProperty(props, this.prefix + ".referralexception", false);
			if (this.capabilities == null) {
				this.capability();
			}

			if (this.hasCapability("IMAP4rev1")) {
				this.rev1 = true;
			}

			this.searchCharsets = new String[2];
			this.searchCharsets[0] = "UTF-8";
			this.searchCharsets[1] = MimeUtility.mimeCharset(MimeUtility.getDefaultJavaCharset());
			this.connected = true;
		} finally {
			if (!this.connected) {
				this.disconnect();
			}

		}

	}

	public IMAPProtocol(InputStream in, PrintStream out, Properties props, boolean debug) throws IOException {
		super(in, out, props, debug);
		this.name = "imap";
		this.noauthdebug = !PropUtil.getBooleanProperty(props, "mail.debug.auth", false);
		if (this.capabilities == null) {
			this.capabilities = new HashMap();
		}

		this.searchCharsets = new String[2];
		this.searchCharsets[0] = "UTF-8";
		this.searchCharsets[1] = MimeUtility.mimeCharset(MimeUtility.getDefaultJavaCharset());
		this.connected = true;
	}

	public FetchItem[] getFetchItems() {
		return fetchItems;
	}

	public void capability() throws ProtocolException {
		Response[] r = this.command("CAPABILITY", (Argument) null);
		Response response = r[r.length - 1];
		if (response.isOK()) {
			this.handleCapabilityResponse(r);
		}

		this.handleResult(response);
	}

	public void handleCapabilityResponse(Response[] r) {
		boolean first = true;
		int i = 0;

		for (int len = r.length; i < len; ++i) {
			if (r[i] instanceof IMAPResponse) {
				IMAPResponse ir = (IMAPResponse) r[i];
				if (ir.keyEquals("CAPABILITY")) {
					if (first) {
						this.capabilities = new HashMap(10);
						this.authmechs = new ArrayList(5);
						first = false;
					}

					this.parseCapabilities(ir);
				}
			}
		}

	}

	protected void setCapabilities(Response r) {
		byte b;
		while ((b = r.readByte()) > 0 && b != 91) {
		}

		if (b != 0) {
			String s = r.readAtom();
			if (s.equalsIgnoreCase("CAPABILITY")) {
				this.capabilities = new HashMap(10);
				this.authmechs = new ArrayList(5);
				this.parseCapabilities(r);
			}
		}
	}

	protected void parseCapabilities(Response r) {
		while (true) {
			String s;
			if ((s = r.readAtom()) != null) {
				if (s.length() != 0) {
					this.capabilities.put(s.toUpperCase(Locale.ENGLISH), s);
					if (!s.regionMatches(true, 0, "AUTH=", 0, 5)) {
						continue;
					}

					this.authmechs.add(s.substring(5));
					if (this.logger.isLoggable(Level.FINE)) {
						this.logger.fine("AUTH: " + s.substring(5));
					}
					continue;
				}

				if (r.peekByte() != 93) {
					r.skipToken();
					continue;
				}
			}

			return;
		}
	}

	protected void processGreeting(Response r) throws ProtocolException {
		if (r.isBYE()) {
			this.checkReferral(r);
			throw new ConnectionException(this, r);
		} else if (r.isOK()) {
			this.referralException = PropUtil.getBooleanProperty(this.props, this.prefix + ".referralexception", false);
			if (this.referralException) {
				this.checkReferral(r);
			}

			this.setCapabilities(r);
		} else {
			assert r instanceof IMAPResponse;

			IMAPResponse ir = (IMAPResponse) r;
			if (ir.keyEquals("PREAUTH")) {
				this.authenticated = true;
				this.setCapabilities(r);
			} else {
				this.disconnect();
				throw new ConnectionException(this, r);
			}
		}
	}

	private void checkReferral(Response r) throws IMAPReferralException {
		String s = r.getRest();
		if (s.startsWith("[")) {
			int i = s.indexOf(32);
			if (i > 0 && s.substring(1, i).equalsIgnoreCase("REFERRAL")) {
				int j = s.indexOf(93);
				String url;
				String msg;
				if (j > 0) {
					url = s.substring(i + 1, j);
					msg = s.substring(j + 1).trim();
				} else {
					url = s.substring(i + 1);
					msg = "";
				}

				if (r.isBYE()) {
					this.disconnect();
				}

				throw new IMAPReferralException(msg, url);
			}
		}

	}

	public boolean isAuthenticated() {
		return this.authenticated;
	}

	public boolean isREV1() {
		return this.rev1;
	}

	protected boolean supportsNonSyncLiterals() {
		return this.hasCapability("LITERAL+");
	}

	public Response readResponse() throws IOException, ProtocolException {
		IMAPResponse r = new IMAPResponse(this);
		if (((IMAPResponse) r).keyEquals("FETCH")) {
			r = new FetchResponse((IMAPResponse) r, this.getFetchItems());
		}

		return (Response) r;
	}

	public boolean hasCapability(String c) {
		if (c.endsWith("*")) {
			c = c.substring(0, c.length() - 1).toUpperCase(Locale.ENGLISH);
			Iterator<String> it = this.capabilities.keySet().iterator();

			do {
				if (!it.hasNext()) {
					return false;
				}
			} while (!((String) it.next()).startsWith(c));

			return true;
		} else {
			return this.capabilities.containsKey(c.toUpperCase(Locale.ENGLISH));
		}
	}

	public Map<String, String> getCapabilities() {
		return this.capabilities;
	}

	public boolean supportsUtf8() {
		return this.utf8;
	}

	public void disconnect() {
		super.disconnect();
		this.authenticated = false;
	}

	public void noop() throws ProtocolException {
		this.logger.fine("IMAPProtocol noop");
		this.simpleCommand("NOOP", (Argument) null);
	}

	public void logout() throws ProtocolException {
		try {
			Response[] r = this.command("LOGOUT", (Argument) null);
			this.authenticated = false;
			this.notifyResponseHandlers(r);
		} finally {
			this.disconnect();
		}

	}

	public void login(String u, String p) throws ProtocolException {
		Argument args = new Argument();
		args.writeString(u);
		args.writeString(p);
		Response[] r = null;

		try {
			if (this.noauthdebug && this.isTracing()) {
				this.logger.fine("LOGIN command trace suppressed");
				this.suspendTracing();
			}

			r = this.command("LOGIN", args);
		} finally {
			this.resumeTracing();
		}

		this.handleCapabilityResponse(r);
		this.notifyResponseHandlers(r);
		if (this.noauthdebug && this.isTracing()) {
			this.logger.fine("LOGIN command result: " + r[r.length - 1]);
		}

		this.handleLoginResult(r[r.length - 1]);
		this.setCapabilities(r[r.length - 1]);
		this.authenticated = true;
	}

	public synchronized void authlogin(String u, String p) throws ProtocolException {
		List<Response> v = new ArrayList();
		String tag = null;
		Response r = null;
		boolean done = false;

		try {
			if (this.noauthdebug && this.isTracing()) {
				this.logger.fine("AUTHENTICATE LOGIN command trace suppressed");
				this.suspendTracing();
			}

			try {
				tag = this.writeCommand("AUTHENTICATE LOGIN", (Argument) null);
			} catch (Exception var16) {
				r = Response.byeResponse(var16);
				done = true;
			}

			OutputStream os = this.getOutputStream();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			OutputStream b64os = new BASE64EncoderStream(bos, Integer.MAX_VALUE);

			for (boolean first = true; !done; v.add(r)) {
				try {
					r = this.readResponse();
					if (r.isContinuation()) {
						String s;
						if (first) {
							s = u;
							first = false;
						} else {
							s = p;
						}

						b64os.write(s.getBytes(StandardCharsets.UTF_8));
						b64os.flush();
						bos.write(CRLF);
						os.write(bos.toByteArray());
						os.flush();
						bos.reset();
					} else if (r.isTagged() && r.getTag().equals(tag)) {
						done = true;
					} else if (r.isBYE()) {
						done = true;
					}
				} catch (Exception var17) {
					r = Response.byeResponse(var17);
					done = true;
				}
			}
		} finally {
			this.resumeTracing();
		}

		Response[] responses = (Response[]) v.toArray(new Response[0]);
		this.handleCapabilityResponse(responses);
		this.notifyResponseHandlers(responses);
		if (this.noauthdebug && this.isTracing()) {
			this.logger.fine("AUTHENTICATE LOGIN command result: " + r);
		}

		this.handleLoginResult(r);
		this.setCapabilities(r);
		this.authenticated = true;
	}

	public synchronized void authplain(String authzid, String u, String p) throws ProtocolException {
		List<Response> v = new ArrayList();
		String tag = null;
		Response r = null;
		boolean done = false;

		try {
			if (this.noauthdebug && this.isTracing()) {
				this.logger.fine("AUTHENTICATE PLAIN command trace suppressed");
				this.suspendTracing();
			}

			try {
				tag = this.writeCommand("AUTHENTICATE PLAIN", (Argument) null);
			} catch (Exception var17) {
				r = Response.byeResponse(var17);
				done = true;
			}

			OutputStream os = this.getOutputStream();
			ByteArrayOutputStream bos = new ByteArrayOutputStream();

			for (OutputStream b64os = new BASE64EncoderStream(bos, Integer.MAX_VALUE); !done; v.add(r)) {
				try {
					r = this.readResponse();
					if (r.isContinuation()) {
						String nullByte = " ";
						String s = (authzid == null ? "" : authzid) + " " + u + " " + p;
						b64os.write(s.getBytes(StandardCharsets.UTF_8));
						b64os.flush();
						bos.write(CRLF);
						os.write(bos.toByteArray());
						os.flush();
						bos.reset();
					} else if (r.isTagged() && r.getTag().equals(tag)) {
						done = true;
					} else if (r.isBYE()) {
						done = true;
					}
				} catch (Exception var18) {
					r = Response.byeResponse(var18);
					done = true;
				}
			}
		} finally {
			this.resumeTracing();
		}

		Response[] responses = (Response[]) v.toArray(new Response[0]);
		this.handleCapabilityResponse(responses);
		this.notifyResponseHandlers(responses);
		if (this.noauthdebug && this.isTracing()) {
			this.logger.fine("AUTHENTICATE PLAIN command result: " + r);
		}

		this.handleLoginResult(r);
		this.setCapabilities(r);
		this.authenticated = true;
	}

	public synchronized void authntlm(String authzid, String u, String p) throws ProtocolException {
		List<Response> v = new ArrayList();
		String tag = null;
		Response r = null;
		boolean done = false;
		int flags = PropUtil.getIntProperty(this.props, "mail." + this.name + ".auth.ntlm.flags", 0);
		boolean v2 = PropUtil.getBooleanProperty(this.props, "mail." + this.name + ".auth.ntlm.v2", true);
		String domain = this.props.getProperty("mail." + this.name + ".auth.ntlm.domain", "");
		Ntlm ntlm = new Ntlm(domain, this.getLocalHost(), u, p, this.logger);

		try {
			if (this.noauthdebug && this.isTracing()) {
				this.logger.fine("AUTHENTICATE NTLM command trace suppressed");
				this.suspendTracing();
			}

			try {
				tag = this.writeCommand("AUTHENTICATE NTLM", (Argument) null);
			} catch (Exception var19) {
				r = Response.byeResponse(var19);
				done = true;
			}

			OutputStream os = this.getOutputStream();

			for (boolean first = true; !done; v.add(r)) {
				try {
					r = this.readResponse();
					if (r.isContinuation()) {
						String s;
						if (first) {
							s = ntlm.generateType1Msg(flags, v2);
							first = false;
						} else {
							s = ntlm.generateType3Msg(r.getRest());
						}

						os.write(s.getBytes(StandardCharsets.UTF_8));
						os.write(CRLF);
						os.flush();
					} else if (r.isTagged() && r.getTag().equals(tag)) {
						done = true;
					} else if (r.isBYE()) {
						done = true;
					}
				} catch (Exception var20) {
					r = Response.byeResponse(var20);
					done = true;
				}
			}
		} finally {
			this.resumeTracing();
		}

		Response[] responses = (Response[]) v.toArray(new Response[0]);
		this.handleCapabilityResponse(responses);
		this.notifyResponseHandlers(responses);
		if (this.noauthdebug && this.isTracing()) {
			this.logger.fine("AUTHENTICATE NTLM command result: " + r);
		}

		this.handleLoginResult(r);
		this.setCapabilities(r);
		this.authenticated = true;
	}

	public synchronized void authoauth2(String u, String p) throws ProtocolException {
		List<Response> v = new ArrayList();
		String tag = null;
		Response r = null;
		boolean done = false;

		try {
			if (this.noauthdebug && this.isTracing()) {
				this.logger.fine("AUTHENTICATE XOAUTH2 command trace suppressed");
				this.suspendTracing();
			}

			String resp;
			byte[] b;
			try {
				Argument args = new Argument();
				args.writeAtom("XOAUTH2");
				if (this.hasCapability("SASL-IR")) {
					resp = "user=" + u + "auth=Bearer " + p + "";
					b = Base64.getEncoder().encode(resp.getBytes(StandardCharsets.UTF_8));
					String irs = ASCIIUtility.toString(b, 0, b.length);
					args.writeAtom(irs);
				}

				tag = this.writeCommand("AUTHENTICATE", args);
			} catch (Exception var15) {
				r = Response.byeResponse(var15);
				done = true;
			}

			for (OutputStream os = this.getOutputStream(); !done; v.add(r)) {
				try {
					r = this.readResponse();
					if (r.isContinuation()) {
						resp = "user=" + u + "auth=Bearer " + p + "";
						b = Base64.getEncoder().encode(resp.getBytes(StandardCharsets.UTF_8));
						os.write(b);
						os.write(CRLF);
						os.flush();
					} else if (r.isTagged() && r.getTag().equals(tag)) {
						done = true;
					} else if (r.isBYE()) {
						done = true;
					}
				} catch (Exception var16) {
					r = Response.byeResponse(var16);
					done = true;
				}
			}
		} finally {
			this.resumeTracing();
		}

		Response[] responses = (Response[]) v.toArray(new Response[0]);
		this.handleCapabilityResponse(responses);
		this.notifyResponseHandlers(responses);
		if (this.noauthdebug && this.isTracing()) {
			this.logger.fine("AUTHENTICATE XOAUTH2 command result: " + r);
		}

		this.handleLoginResult(r);
		this.setCapabilities(r);
		this.authenticated = true;
	}

	public void sasllogin(String[] allowed, String realm, String authzid, String u, String p) throws ProtocolException {
		boolean useCanonicalHostName = PropUtil.getBooleanProperty(this.props,
				"mail." + this.name + ".sasl.usecanonicalhostname", false);
		String serviceHost;
		if (useCanonicalHostName) {
			serviceHost = this.getInetAddress().getCanonicalHostName();
		} else {
			serviceHost = this.host;
		}

		if (this.saslAuthenticator == null) {
			try {
				Class<?> sac = Class.forName("org.eclipse.angus.mail.imap.protocol.IMAPSaslAuthenticator");
				Constructor<?> c = sac.getConstructor(IMAPProtocol.class, String.class, Properties.class,
						MailLogger.class, String.class);
				this.saslAuthenticator = (SaslAuthenticator) c.newInstance(this, this.name, this.props, this.logger,
						serviceHost);
			} catch (Exception var13) {
				this.logger.log(Level.FINE, "Can't load SASL authenticator", var13);
				return;
			}
		}

		Object v;
		if (allowed != null && allowed.length > 0) {
			v = new ArrayList(allowed.length);

			for (int i = 0; i < allowed.length; ++i) {
				if (this.authmechs.contains(allowed[i])) {
					((List) v).add(allowed[i]);
				}
			}
		} else {
			v = this.authmechs;
		}

		String[] mechs = (String[]) ((List) v).toArray(new String[0]);

		try {
			if (this.noauthdebug && this.isTracing()) {
				this.logger.fine("SASL authentication command trace suppressed");
				this.suspendTracing();
			}

			if (this.saslAuthenticator.authenticate(mechs, realm, authzid, u, p)) {
				if (this.noauthdebug && this.isTracing()) {
					this.logger.fine("SASL authentication succeeded");
				}

				this.authenticated = true;
			} else if (this.noauthdebug && this.isTracing()) {
				this.logger.fine("SASL authentication failed");
			}
		} finally {
			this.resumeTracing();
		}

	}

	OutputStream getIMAPOutputStream() {
		return this.getOutputStream();
	}

	protected void handleLoginResult(Response r) throws ProtocolException {
		if (this.hasCapability("LOGIN-REFERRALS") && (!r.isOK() || this.referralException)) {
			this.checkReferral(r);
		}

		this.handleResult(r);
	}

	public void proxyauth(String u) throws ProtocolException {
		Argument args = new Argument();
		args.writeString(u);
		this.simpleCommand("PROXYAUTH", args);
		this.proxyAuthUser = u;
	}

	public String getProxyAuthUser() {
		return this.proxyAuthUser;
	}

	public void unauthenticate() throws ProtocolException {
		if (!this.hasCapability("X-UNAUTHENTICATE")) {
			throw new BadCommandException("UNAUTHENTICATE not supported");
		} else {
			this.simpleCommand("UNAUTHENTICATE", (Argument) null);
			this.authenticated = false;
		}
	}

	@Deprecated
	public void id(String guid) throws ProtocolException {
		Map<String, String> gmap = new HashMap();
		gmap.put("GUID", guid);
		this.id((Map) gmap);
	}

	public void startTLS() throws ProtocolException {
		try {
			super.startTLS("STARTTLS");
		} catch (ProtocolException var3) {
			this.logger.log(Level.FINE, "STARTTLS ProtocolException", var3);
			throw var3;
		} catch (Exception var4) {
			this.logger.log(Level.FINE, "STARTTLS Exception", var4);
			Response[] r = new Response[]{Response.byeResponse(var4)};
			this.notifyResponseHandlers(r);
			this.disconnect();
			throw new ProtocolException("STARTTLS failure", var4);
		}
	}

	public void compress() throws ProtocolException {
		try {
			super.startCompression("COMPRESS DEFLATE");
		} catch (ProtocolException var3) {
			this.logger.log(Level.FINE, "COMPRESS ProtocolException", var3);
			throw var3;
		} catch (Exception var4) {
			this.logger.log(Level.FINE, "COMPRESS Exception", var4);
			Response[] r = new Response[]{Response.byeResponse(var4)};
			this.notifyResponseHandlers(r);
			this.disconnect();
			throw new ProtocolException("COMPRESS failure", var4);
		}
	}

	protected void writeMailboxName(Argument args, String name) {
		if (this.utf8) {
			args.writeString(name, StandardCharsets.UTF_8);
		} else {
			args.writeString(BASE64MailboxEncoder.encode(name));
		}

	}

	public MailboxInfo select(String mbox) throws ProtocolException {
		return this.select(mbox, (ResyncData) null);
	}

	public MailboxInfo select(String mbox, ResyncData rd) throws ProtocolException {
		Argument args = new Argument();
		this.writeMailboxName(args, mbox);
		if (rd != null) {
			if (rd == ResyncData.CONDSTORE) {
				if (!this.hasCapability("CONDSTORE")) {
					throw new BadCommandException("CONDSTORE not supported");
				}

				args.writeArgument((new Argument()).writeAtom("CONDSTORE"));
			} else {
				if (!this.hasCapability("QRESYNC")) {
					throw new BadCommandException("QRESYNC not supported");
				}

				args.writeArgument(resyncArgs(rd));
			}
		}

		Response[] r = this.command("SELECT", args);
		MailboxInfo minfo = new MailboxInfo(r);
		this.notifyResponseHandlers(r);
		Response response = r[r.length - 1];
		if (response.isOK()) {
			if (response.toString().contains("READ-ONLY")) {
				minfo.mode = 1;
			} else {
				minfo.mode = 2;
			}
		}

		this.handleResult(response);
		return minfo;
	}

	public MailboxInfo examine(String mbox) throws ProtocolException {
		return this.examine(mbox, (ResyncData) null);
	}

	public MailboxInfo examine(String mbox, ResyncData rd) throws ProtocolException {
		Argument args = new Argument();
		this.writeMailboxName(args, mbox);
		if (rd != null) {
			if (rd == ResyncData.CONDSTORE) {
				if (!this.hasCapability("CONDSTORE")) {
					throw new BadCommandException("CONDSTORE not supported");
				}

				args.writeArgument((new Argument()).writeAtom("CONDSTORE"));
			} else {
				if (!this.hasCapability("QRESYNC")) {
					throw new BadCommandException("QRESYNC not supported");
				}

				args.writeArgument(resyncArgs(rd));
			}
		}

		Response[] r = this.command("EXAMINE", args);
		MailboxInfo minfo = new MailboxInfo(r);
		minfo.mode = 1;
		this.notifyResponseHandlers(r);
		this.handleResult(r[r.length - 1]);
		return minfo;
	}

	private static Argument resyncArgs(ResyncData rd) {
		Argument cmd = new Argument();
		cmd.writeAtom("QRESYNC");
		Argument args = new Argument();
		args.writeNumber(rd.getUIDValidity());
		args.writeNumber(rd.getModSeq());
		UIDSet[] uids = Utility.getResyncUIDSet(rd);
		if (uids != null) {
			args.writeString(UIDSet.toString(uids));
		}

		cmd.writeArgument(args);
		return cmd;
	}

	public void enable(String cap) throws ProtocolException {
		if (!this.hasCapability("ENABLE")) {
			throw new BadCommandException("ENABLE not supported");
		} else {
			Argument args = new Argument();
			args.writeAtom(cap);
			this.simpleCommand("ENABLE", args);
			if (this.enabled == null) {
				this.enabled = new HashSet();
			}

			this.enabled.add(cap.toUpperCase(Locale.ENGLISH));
			this.utf8 = this.isEnabled("UTF8=ACCEPT");
		}
	}

	public boolean isEnabled(String cap) {
		return this.enabled == null ? false : this.enabled.contains(cap.toUpperCase(Locale.ENGLISH));
	}

	public void unselect() throws ProtocolException {
		if (!this.hasCapability("UNSELECT")) {
			throw new BadCommandException("UNSELECT not supported");
		} else {
			this.simpleCommand("UNSELECT", (Argument) null);
		}
	}

	public Status status(String mbox, String[] items) throws ProtocolException {
		if (!this.isREV1() && !this.hasCapability("IMAP4SUNVERSION")) {
			throw new BadCommandException("STATUS not supported");
		} else {
			Argument args = new Argument();
			this.writeMailboxName(args, mbox);
			Argument itemArgs = new Argument();
			if (items == null) {
				items = Status.standardItems;
			}

			int i = 0;

			for (int len = items.length; i < len; ++i) {
				itemArgs.writeAtom(items[i]);
			}

			args.writeArgument(itemArgs);
			Response[] r = this.command("STATUS", args);
			Status status = null;
			Response response = r[r.length - 1];
			if (response.isOK()) {
				int i = 0;

				for (int len = r.length; i < len; ++i) {
					if (r[i] instanceof IMAPResponse) {
						IMAPResponse ir = (IMAPResponse) r[i];
						if (ir.keyEquals("STATUS")) {
							if (status == null) {
								status = new Status(ir);
							} else {
								Status.add(status, new Status(ir));
							}

							r[i] = null;
						}
					}
				}
			}

			this.notifyResponseHandlers(r);
			this.handleResult(response);
			return status;
		}
	}

	public void create(String mbox) throws ProtocolException {
		Argument args = new Argument();
		this.writeMailboxName(args, mbox);
		this.simpleCommand("CREATE", args);
	}

	public void delete(String mbox) throws ProtocolException {
		Argument args = new Argument();
		this.writeMailboxName(args, mbox);
		this.simpleCommand("DELETE", args);
	}

	public void rename(String o, String n) throws ProtocolException {
		Argument args = new Argument();
		this.writeMailboxName(args, o);
		this.writeMailboxName(args, n);
		this.simpleCommand("RENAME", args);
	}

	public void subscribe(String mbox) throws ProtocolException {
		Argument args = new Argument();
		this.writeMailboxName(args, mbox);
		this.simpleCommand("SUBSCRIBE", args);
	}

	public void unsubscribe(String mbox) throws ProtocolException {
		Argument args = new Argument();
		this.writeMailboxName(args, mbox);
		this.simpleCommand("UNSUBSCRIBE", args);
	}

	public ListInfo[] list(String ref, String pattern) throws ProtocolException {
		return this.doList("LIST", ref, pattern);
	}

	public ListInfo[] lsub(String ref, String pattern) throws ProtocolException {
		return this.doList("LSUB", ref, pattern);
	}

	protected ListInfo[] doList(String cmd, String ref, String pat) throws ProtocolException {
		Argument args = new Argument();
		this.writeMailboxName(args, ref);
		this.writeMailboxName(args, pat);
		Response[] r = this.command(cmd, args);
		ListInfo[] linfo = null;
		Response response = r[r.length - 1];
		if (response.isOK()) {
			List<ListInfo> v = new ArrayList(1);
			int i = 0;

			for (int len = r.length; i < len; ++i) {
				if (r[i] instanceof IMAPResponse) {
					IMAPResponse ir = (IMAPResponse) r[i];
					if (ir.keyEquals(cmd)) {
						v.add(new ListInfo(ir));
						r[i] = null;
					}
				}
			}

			if (v.size() > 0) {
				linfo = (ListInfo[]) v.toArray(new ListInfo[0]);
			}
		}

		this.notifyResponseHandlers(r);
		this.handleResult(response);
		return linfo;
	}

	public void append(String mbox, Flags f, Date d, Literal data) throws ProtocolException {
		this.appenduid(mbox, f, d, data, false);
	}

	public AppendUID appenduid(String mbox, Flags f, Date d, Literal data) throws ProtocolException {
		return this.appenduid(mbox, f, d, data, true);
	}

	public AppendUID appenduid(String mbox, Flags f, Date d, Literal data, boolean uid) throws ProtocolException {
		Argument args = new Argument();
		this.writeMailboxName(args, mbox);
		if (f != null) {
			if (f.contains(Flag.RECENT)) {
				f = new Flags(f);
				f.remove(Flag.RECENT);
			}

			args.writeAtom(this.createFlagList(f));
		}

		if (d != null) {
			args.writeString(INTERNALDATE.format(d));
		}

		args.writeBytes(data);
		Response[] r = this.command("APPEND", args);
		this.notifyResponseHandlers(r);
		this.handleResult(r[r.length - 1]);
		return uid ? this.getAppendUID(r[r.length - 1]) : null;
	}

	private AppendUID getAppendUID(Response r) {
		if (!r.isOK()) {
			return null;
		} else {
			byte b;
			while ((b = r.readByte()) > 0 && b != 91) {
			}

			if (b == 0) {
				return null;
			} else {
				String s = r.readAtom();
				if (!s.equalsIgnoreCase("APPENDUID")) {
					return null;
				} else {
					long uidvalidity = r.readLong();
					long uid = r.readLong();
					return new AppendUID(uidvalidity, uid);
				}
			}
		}
	}

	public void check() throws ProtocolException {
		this.simpleCommand("CHECK", (Argument) null);
	}

	public void close() throws ProtocolException {
		this.simpleCommand("CLOSE", (Argument) null);
	}

	public void expunge() throws ProtocolException {
		this.simpleCommand("EXPUNGE", (Argument) null);
	}

	public void uidexpunge(UIDSet[] set) throws ProtocolException {
		if (!this.hasCapability("UIDPLUS")) {
			throw new BadCommandException("UID EXPUNGE not supported");
		} else {
			this.simpleCommand("UID EXPUNGE " + UIDSet.toString(set), (Argument) null);
		}
	}

	public BODYSTRUCTURE fetchBodyStructure(int msgno) throws ProtocolException {
		Response[] r = this.fetch(msgno, "BODYSTRUCTURE");
		this.notifyResponseHandlers(r);
		Response response = r[r.length - 1];
		if (response.isOK()) {
			return (BODYSTRUCTURE) FetchResponse.getItem(r, msgno, BODYSTRUCTURE.class);
		} else if (response.isNO()) {
			return null;
		} else {
			this.handleResult(response);
			return null;
		}
	}

	public BODY peekBody(int msgno, String section) throws ProtocolException {
		return this.fetchBody(msgno, section, true);
	}

	public BODY fetchBody(int msgno, String section) throws ProtocolException {
		return this.fetchBody(msgno, section, false);
	}

	protected BODY fetchBody(int msgno, String section, boolean peek) throws ProtocolException {
		if (section == null) {
			section = "";
		}

		String body = (peek ? "BODY.PEEK[" : "BODY[") + section + "]";
		return this.fetchSectionBody(msgno, section, body);
	}

	public BODY peekBody(int msgno, String section, int start, int size) throws ProtocolException {
		return this.fetchBody(msgno, section, start, size, true, (ByteArray) null);
	}

	public BODY fetchBody(int msgno, String section, int start, int size) throws ProtocolException {
		return this.fetchBody(msgno, section, start, size, false, (ByteArray) null);
	}

	public BODY peekBody(int msgno, String section, int start, int size, ByteArray ba) throws ProtocolException {
		return this.fetchBody(msgno, section, start, size, true, ba);
	}

	public BODY fetchBody(int msgno, String section, int start, int size, ByteArray ba) throws ProtocolException {
		return this.fetchBody(msgno, section, start, size, false, ba);
	}

	protected BODY fetchBody(int msgno, String section, int start, int size, boolean peek, ByteArray ba)
			throws ProtocolException {
		this.ba = ba;
		if (section == null) {
			section = "";
		}

		String body = (peek ? "BODY.PEEK[" : "BODY[") + section + "]<" + start + "." + size + ">";
		return this.fetchSectionBody(msgno, section, body);
	}

	protected BODY fetchSectionBody(int msgno, String section, String body) throws ProtocolException {
		Response[] r = this.fetch(msgno, body);
		this.notifyResponseHandlers(r);
		Response response = r[r.length - 1];
		if (response.isOK()) {
			List<BODY> bl = FetchResponse.getItems(r, msgno, BODY.class);
			if (bl.size() == 1) {
				return (BODY) bl.get(0);
			} else {
				if (this.logger.isLoggable(Level.FINEST)) {
					this.logger.finest("got " + bl.size() + " BODY responses for section " + section);
				}

				Iterator var7 = bl.iterator();

				BODY br;
				do {
					if (!var7.hasNext()) {
						return null;
					}

					br = (BODY) var7.next();
					if (this.logger.isLoggable(Level.FINEST)) {
						this.logger.finest("got BODY section " + br.getSection());
					}
				} while (!br.getSection().equalsIgnoreCase(section));

				return br;
			}
		} else if (response.isNO()) {
			return null;
		} else {
			this.handleResult(response);
			return null;
		}
	}

	protected ByteArray getResponseBuffer() {
		ByteArray ret = this.ba;
		this.ba = null;
		return ret;
	}

	public RFC822DATA fetchRFC822(int msgno, String what) throws ProtocolException {
		Response[] r = this.fetch(msgno, what == null ? "RFC822" : "RFC822." + what);
		this.notifyResponseHandlers(r);
		Response response = r[r.length - 1];
		if (response.isOK()) {
			return (RFC822DATA) FetchResponse.getItem(r, msgno, RFC822DATA.class);
		} else if (response.isNO()) {
			return null;
		} else {
			this.handleResult(response);
			return null;
		}
	}

	public Flags fetchFlags(int msgno) throws ProtocolException {
		Flags flags = null;
		Response[] r = this.fetch(msgno, "FLAGS");
		int i = 0;

		for (int len = r.length; i < len; ++i) {
			if (r[i] != null && r[i] instanceof FetchResponse && ((FetchResponse) r[i]).getNumber() == msgno) {
				FetchResponse fr = (FetchResponse) r[i];
				if ((flags = (Flags) fr.getItem(FLAGS.class)) != null) {
					r[i] = null;
					break;
				}
			}
		}

		this.notifyResponseHandlers(r);
		this.handleResult(r[r.length - 1]);
		return flags;
	}

	public UID fetchUID(int msgno) throws ProtocolException {
		Response[] r = this.fetch(msgno, "UID");
		this.notifyResponseHandlers(r);
		Response response = r[r.length - 1];
		if (response.isOK()) {
			return (UID) FetchResponse.getItem(r, msgno, UID.class);
		} else if (response.isNO()) {
			return null;
		} else {
			this.handleResult(response);
			return null;
		}
	}

	public MODSEQ fetchMODSEQ(int msgno) throws ProtocolException {
		Response[] r = this.fetch(msgno, "MODSEQ");
		this.notifyResponseHandlers(r);
		Response response = r[r.length - 1];
		if (response.isOK()) {
			return (MODSEQ) FetchResponse.getItem(r, msgno, MODSEQ.class);
		} else if (response.isNO()) {
			return null;
		} else {
			this.handleResult(response);
			return null;
		}
	}

	public void fetchSequenceNumber(long uid) throws ProtocolException {
		Response[] r = this.fetch(String.valueOf(uid), "UID", true);
		this.notifyResponseHandlers(r);
		this.handleResult(r[r.length - 1]);
	}

	public long[] fetchSequenceNumbers(long start, long end) throws ProtocolException {
		Response[] r = this.fetch(start + ":" + (end == -1L ? "*" : String.valueOf(end)), "UID", true);
		List<UID> v = new ArrayList();
		int i = 0;

		int i;
		for (i = r.length; i < i; ++i) {
			if (r[i] != null && r[i] instanceof FetchResponse) {
				FetchResponse fr = (FetchResponse) r[i];
				UID u;
				if ((u = (UID) fr.getItem(UID.class)) != null) {
					v.add(u);
				}
			}
		}

		this.notifyResponseHandlers(r);
		this.handleResult(r[r.length - 1]);
		long[] lv = new long[v.size()];

		for (i = 0; i < v.size(); ++i) {
			lv[i] = ((UID) v.get(i)).uid;
		}

		return lv;
	}

	public void fetchSequenceNumbers(long[] uids) throws ProtocolException {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < uids.length; ++i) {
			if (i > 0) {
				sb.append(",");
			}

			sb.append(String.valueOf(uids[i]));
		}

		Response[] r = this.fetch(sb.toString(), "UID", true);
		this.notifyResponseHandlers(r);
		this.handleResult(r[r.length - 1]);
	}

	public int[] uidfetchChangedSince(long start, long end, long modseq) throws ProtocolException {
		String msgSequence = start + ":" + (end == -1L ? "*" : String.valueOf(end));
		Response[] r = this.command("UID FETCH " + msgSequence + " (FLAGS) (CHANGEDSINCE " + modseq + ")",
				(Argument) null);
		List<Integer> v = new ArrayList();
		int vsize = 0;

		for (int len = r.length; vsize < len; ++vsize) {
			if (r[vsize] != null && r[vsize] instanceof FetchResponse) {
				FetchResponse fr = (FetchResponse) r[vsize];
				v.add(fr.getNumber());
			}
		}

		this.notifyResponseHandlers(r);
		this.handleResult(r[r.length - 1]);
		vsize = v.size();
		int[] matches = new int[vsize];

		for (int i = 0; i < vsize; ++i) {
			matches[i] = (Integer) v.get(i);
		}

		return matches;
	}

	public Response[] fetch(MessageSet[] msgsets, String what) throws ProtocolException {
		return this.fetch(MessageSet.toString(msgsets), what, false);
	}

	public Response[] fetch(int start, int end, String what) throws ProtocolException {
		return this.fetch(start + ":" + end, what, false);
	}

	public Response[] fetch(int msg, String what) throws ProtocolException {
		return this.fetch(String.valueOf(msg), what, false);
	}

	private Response[] fetch(String msgSequence, String what, boolean uid) throws ProtocolException {
		return uid
				? this.command("UID FETCH " + msgSequence + " (" + what + ")", (Argument) null)
				: this.command("FETCH " + msgSequence + " (" + what + ")", (Argument) null);
	}

	public void copy(MessageSet[] msgsets, String mbox) throws ProtocolException {
		this.copyuid(MessageSet.toString(msgsets), mbox, false);
	}

	public void copy(int start, int end, String mbox) throws ProtocolException {
		this.copyuid(start + ":" + end, mbox, false);
	}

	public CopyUID copyuid(MessageSet[] msgsets, String mbox) throws ProtocolException {
		return this.copyuid(MessageSet.toString(msgsets), mbox, true);
	}

	public CopyUID copyuid(int start, int end, String mbox) throws ProtocolException {
		return this.copyuid(start + ":" + end, mbox, true);
	}

	private CopyUID copyuid(String msgSequence, String mbox, boolean uid) throws ProtocolException {
		if (uid && !this.hasCapability("UIDPLUS")) {
			throw new BadCommandException("UIDPLUS not supported");
		} else {
			Argument args = new Argument();
			args.writeAtom(msgSequence);
			this.writeMailboxName(args, mbox);
			Response[] r = this.command("COPY", args);
			this.notifyResponseHandlers(r);
			this.handleResult(r[r.length - 1]);
			return uid ? this.getCopyUID(r) : null;
		}
	}

	public void move(MessageSet[] msgsets, String mbox) throws ProtocolException {
		this.moveuid(MessageSet.toString(msgsets), mbox, false);
	}

	public void move(int start, int end, String mbox) throws ProtocolException {
		this.moveuid(start + ":" + end, mbox, false);
	}

	public CopyUID moveuid(MessageSet[] msgsets, String mbox) throws ProtocolException {
		return this.moveuid(MessageSet.toString(msgsets), mbox, true);
	}

	public CopyUID moveuid(int start, int end, String mbox) throws ProtocolException {
		return this.moveuid(start + ":" + end, mbox, true);
	}

	private CopyUID moveuid(String msgSequence, String mbox, boolean uid) throws ProtocolException {
		if (!this.hasCapability("MOVE")) {
			throw new BadCommandException("MOVE not supported");
		} else if (uid && !this.hasCapability("UIDPLUS")) {
			throw new BadCommandException("UIDPLUS not supported");
		} else {
			Argument args = new Argument();
			args.writeAtom(msgSequence);
			this.writeMailboxName(args, mbox);
			Response[] r = this.command("MOVE", args);
			this.notifyResponseHandlers(r);
			this.handleResult(r[r.length - 1]);
			return uid ? this.getCopyUID(r) : null;
		}
	}

	protected CopyUID getCopyUID(Response[] rr) {
		for (int i = rr.length - 1; i >= 0; --i) {
			Response r = rr[i];
			if (r != null && r.isOK()) {
				byte b;
				while ((b = r.readByte()) > 0 && b != 91) {
				}

				if (b != 0) {
					String s = r.readAtom();
					if (s.equalsIgnoreCase("COPYUID")) {
						long uidvalidity = r.readLong();
						String src = r.readAtom();
						String dst = r.readAtom();
						return new CopyUID(uidvalidity, UIDSet.parseUIDSets(src), UIDSet.parseUIDSets(dst));
					}
				}
			}
		}

		return null;
	}

	public void storeFlags(MessageSet[] msgsets, Flags flags, boolean set) throws ProtocolException {
		this.storeFlags(MessageSet.toString(msgsets), flags, set);
	}

	public void storeFlags(int start, int end, Flags flags, boolean set) throws ProtocolException {
		this.storeFlags(start + ":" + end, flags, set);
	}

	public void storeFlags(int msg, Flags flags, boolean set) throws ProtocolException {
		this.storeFlags(String.valueOf(msg), flags, set);
	}

	private void storeFlags(String msgset, Flags flags, boolean set) throws ProtocolException {
		Response[] r;
		if (set) {
			r = this.command("STORE " + msgset + " +FLAGS " + this.createFlagList(flags), (Argument) null);
		} else {
			r = this.command("STORE " + msgset + " -FLAGS " + this.createFlagList(flags), (Argument) null);
		}

		this.notifyResponseHandlers(r);
		this.handleResult(r[r.length - 1]);
	}

	protected String createFlagList(Flags flags) {
		StringBuilder sb = new StringBuilder("(");
		Flags.Flag[] sf = flags.getSystemFlags();
		boolean first = true;

		for (int i = 0; i < sf.length; ++i) {
			Flags.Flag f = sf[i];
			String s;
			if (f == Flag.ANSWERED) {
				s = "\\Answered";
			} else if (f == Flag.DELETED) {
				s = "\\Deleted";
			} else if (f == Flag.DRAFT) {
				s = "\\Draft";
			} else if (f == Flag.FLAGGED) {
				s = "\\Flagged";
			} else if (f == Flag.RECENT) {
				s = "\\Recent";
			} else {
				if (f != Flag.SEEN) {
					continue;
				}

				s = "\\Seen";
			}

			if (first) {
				first = false;
			} else {
				sb.append(' ');
			}

			sb.append(s);
		}

		String[] uf = flags.getUserFlags();

		for (int i = 0; i < uf.length; ++i) {
			if (first) {
				first = false;
			} else {
				sb.append(' ');
			}

			sb.append(uf[i]);
		}

		sb.append(")");
		return sb.toString();
	}

	public int[] search(MessageSet[] msgsets, SearchTerm term) throws ProtocolException, SearchException {
		return this.search(MessageSet.toString(msgsets), term);
	}

	public int[] search(SearchTerm term) throws ProtocolException, SearchException {
		return this.search("ALL", term);
	}

	private int[] search(String msgSequence, SearchTerm term) throws ProtocolException, SearchException {
		if (this.supportsUtf8() || SearchSequence.isAscii(term)) {
			try {
				return this.issueSearch(msgSequence, term, (String) null);
			} catch (IOException var8) {
			}
		}

		for (int i = 0; i < this.searchCharsets.length; ++i) {
			if (this.searchCharsets[i] != null) {
				try {
					return this.issueSearch(msgSequence, term, this.searchCharsets[i]);
				} catch (CommandFailedException var5) {
					this.searchCharsets[i] = null;
				} catch (IOException var6) {
				} catch (SearchException | ProtocolException var7) {
					throw var7;
				}
			}
		}

		throw new SearchException("Search failed");
	}

	private int[] issueSearch(String msgSequence, SearchTerm term, String charset)
			throws ProtocolException, SearchException, IOException {
		Argument args = this.getSearchSequence().generateSequence(term,
				charset == null ? null : MimeUtility.javaCharset(charset));
		args.writeAtom(msgSequence);
		Response[] r;
		if (charset == null) {
			r = this.command("SEARCH", args);
		} else {
			r = this.command("SEARCH CHARSET " + charset, args);
		}

		Response response = r[r.length - 1];
		int[] matches = null;
		if (response.isOK()) {
			List<Integer> v = new ArrayList();
			int i = 0;

			int i;
			for (i = r.length; i < i; ++i) {
				if (r[i] instanceof IMAPResponse) {
					IMAPResponse ir = (IMAPResponse) r[i];
					if (ir.keyEquals("SEARCH")) {
						int num;
						while ((num = ir.readNumber()) != -1) {
							v.add(num);
						}

						r[i] = null;
					}
				}
			}

			i = v.size();
			matches = new int[i];

			for (i = 0; i < i; ++i) {
				matches[i] = (Integer) v.get(i);
			}
		}

		this.notifyResponseHandlers(r);
		this.handleResult(response);
		return matches;
	}

	protected SearchSequence getSearchSequence() {
		if (this.searchSequence == null) {
			this.searchSequence = new SearchSequence(this);
		}

		return this.searchSequence;
	}

	public int[] sort(SortTerm[] term, SearchTerm sterm) throws ProtocolException, SearchException {
		if (!this.hasCapability("SORT*")) {
			throw new BadCommandException("SORT not supported");
		} else if (term != null && term.length != 0) {
			Argument args = new Argument();
			Argument sargs = new Argument();

			for (int i = 0; i < term.length; ++i) {
				sargs.writeAtom(term[i].toString());
			}

			args.writeArgument(sargs);
			args.writeAtom("UTF-8");
			if (sterm != null) {
				try {
					args.append(this.getSearchSequence().generateSequence(sterm, "UTF-8"));
				} catch (IOException var13) {
					throw new SearchException(var13.toString());
				}
			} else {
				args.writeAtom("ALL");
			}

			Response[] r = this.command("SORT", args);
			Response response = r[r.length - 1];
			int[] matches = null;
			if (response.isOK()) {
				List<Integer> v = new ArrayList();
				int i = 0;

				int i;
				for (i = r.length; i < i; ++i) {
					if (r[i] instanceof IMAPResponse) {
						IMAPResponse ir = (IMAPResponse) r[i];
						if (ir.keyEquals("SORT")) {
							int num;
							while ((num = ir.readNumber()) != -1) {
								v.add(num);
							}

							r[i] = null;
						}
					}
				}

				i = v.size();
				matches = new int[i];

				for (i = 0; i < i; ++i) {
					matches[i] = (Integer) v.get(i);
				}
			}

			this.notifyResponseHandlers(r);
			this.handleResult(response);
			return matches;
		} else {
			throw new BadCommandException("Must have at least one sort term");
		}
	}

	public Namespaces namespace() throws ProtocolException {
		if (!this.hasCapability("NAMESPACE")) {
			throw new BadCommandException("NAMESPACE not supported");
		} else {
			Response[] r = this.command("NAMESPACE", (Argument) null);
			Namespaces namespace = null;
			Response response = r[r.length - 1];
			if (response.isOK()) {
				int i = 0;

				for (int len = r.length; i < len; ++i) {
					if (r[i] instanceof IMAPResponse) {
						IMAPResponse ir = (IMAPResponse) r[i];
						if (ir.keyEquals("NAMESPACE")) {
							if (namespace == null) {
								namespace = new Namespaces(ir);
							}

							r[i] = null;
						}
					}
				}
			}

			this.notifyResponseHandlers(r);
			this.handleResult(response);
			return namespace;
		}
	}

	public Quota[] getQuotaRoot(String mbox) throws ProtocolException {
		if (!this.hasCapability("QUOTA")) {
			throw new BadCommandException("GETQUOTAROOT not supported");
		} else {
			Argument args = new Argument();
			this.writeMailboxName(args, mbox);
			Response[] r = this.command("GETQUOTAROOT", args);
			Response response = r[r.length - 1];
			Map<String, Quota> tab = new HashMap();
			if (response.isOK()) {
				int i = 0;

				for (int len = r.length; i < len; ++i) {
					if (r[i] instanceof IMAPResponse) {
						IMAPResponse ir = (IMAPResponse) r[i];
						if (!ir.keyEquals("QUOTAROOT")) {
							if (ir.keyEquals("QUOTA")) {
								Quota quota = this.parseQuota(ir);
								Quota q = (Quota) tab.get(quota.quotaRoot);
								if (q != null && q.resources != null) {
									int newl = q.resources.length + quota.resources.length;
									Quota.Resource[] newr = new Quota.Resource[newl];
									System.arraycopy(q.resources, 0, newr, 0, q.resources.length);
									System.arraycopy(quota.resources, 0, newr, q.resources.length,
											quota.resources.length);
									quota.resources = newr;
								}

								tab.put(quota.quotaRoot, quota);
								r[i] = null;
							}
						} else {
							ir.readAtomString();
							String root = null;

							while ((root = ir.readAtomString()) != null && root.length() > 0) {
								tab.put(root, new Quota(root));
							}

							r[i] = null;
						}
					}
				}
			}

			this.notifyResponseHandlers(r);
			this.handleResult(response);
			return (Quota[]) tab.values().toArray(new Quota[0]);
		}
	}

	public Quota[] getQuota(String root) throws ProtocolException {
		if (!this.hasCapability("QUOTA")) {
			throw new BadCommandException("QUOTA not supported");
		} else {
			Argument args = new Argument();
			args.writeString(root);
			Response[] r = this.command("GETQUOTA", args);
			Quota quota = null;
			List<Quota> v = new ArrayList();
			Response response = r[r.length - 1];
			if (response.isOK()) {
				int i = 0;

				for (int len = r.length; i < len; ++i) {
					if (r[i] instanceof IMAPResponse) {
						IMAPResponse ir = (IMAPResponse) r[i];
						if (ir.keyEquals("QUOTA")) {
							quota = this.parseQuota(ir);
							v.add(quota);
							r[i] = null;
						}
					}
				}
			}

			this.notifyResponseHandlers(r);
			this.handleResult(response);
			return (Quota[]) v.toArray(new Quota[0]);
		}
	}

	public void setQuota(Quota quota) throws ProtocolException {
		if (!this.hasCapability("QUOTA")) {
			throw new BadCommandException("QUOTA not supported");
		} else {
			Argument args = new Argument();
			args.writeString(quota.quotaRoot);
			Argument qargs = new Argument();
			if (quota.resources != null) {
				for (int i = 0; i < quota.resources.length; ++i) {
					qargs.writeAtom(quota.resources[i].name);
					qargs.writeNumber(quota.resources[i].limit);
				}
			}

			args.writeArgument(qargs);
			Response[] r = this.command("SETQUOTA", args);
			Response response = r[r.length - 1];
			this.notifyResponseHandlers(r);
			this.handleResult(response);
		}
	}

	private Quota parseQuota(Response r) throws ParsingException {
		String quotaRoot = r.readAtomString();
		Quota q = new Quota(quotaRoot);
		r.skipSpaces();
		if (r.readByte() != 40) {
			throw new ParsingException("parse error in QUOTA");
		} else {
			List<Quota.Resource> v = new ArrayList();

			while (!r.isNextNonSpace(')')) {
				String name = r.readAtom();
				if (name != null) {
					long usage = r.readLong();
					long limit = r.readLong();
					Quota.Resource res = new Quota.Resource(name, usage, limit);
					v.add(res);
				}
			}

			q.resources = (Quota.Resource[]) v.toArray(new Quota.Resource[0]);
			return q;
		}
	}

	public void setACL(String mbox, char modifier, ACL acl) throws ProtocolException {
		if (!this.hasCapability("ACL")) {
			throw new BadCommandException("ACL not supported");
		} else {
			Argument args = new Argument();
			this.writeMailboxName(args, mbox);
			args.writeString(acl.getName());
			String rights = acl.getRights().toString();
			if (modifier == '+' || modifier == '-') {
				rights = modifier + rights;
			}

			args.writeString(rights);
			Response[] r = this.command("SETACL", args);
			Response response = r[r.length - 1];
			this.notifyResponseHandlers(r);
			this.handleResult(response);
		}
	}

	public void deleteACL(String mbox, String user) throws ProtocolException {
		if (!this.hasCapability("ACL")) {
			throw new BadCommandException("ACL not supported");
		} else {
			Argument args = new Argument();
			this.writeMailboxName(args, mbox);
			args.writeString(user);
			Response[] r = this.command("DELETEACL", args);
			Response response = r[r.length - 1];
			this.notifyResponseHandlers(r);
			this.handleResult(response);
		}
	}

	public ACL[] getACL(String mbox) throws ProtocolException {
		if (!this.hasCapability("ACL")) {
			throw new BadCommandException("ACL not supported");
		} else {
			Argument args = new Argument();
			this.writeMailboxName(args, mbox);
			Response[] r = this.command("GETACL", args);
			Response response = r[r.length - 1];
			List<ACL> v = new ArrayList();
			if (response.isOK()) {
				int i = 0;

				for (int len = r.length; i < len; ++i) {
					if (r[i] instanceof IMAPResponse) {
						IMAPResponse ir = (IMAPResponse) r[i];
						if (ir.keyEquals("ACL")) {
							ir.readAtomString();
							String name = null;

							while ((name = ir.readAtomString()) != null) {
								String rights = ir.readAtomString();
								if (rights == null) {
									break;
								}

								ACL acl = new ACL(name, new Rights(rights));
								v.add(acl);
							}

							r[i] = null;
						}
					}
				}
			}

			this.notifyResponseHandlers(r);
			this.handleResult(response);
			return (ACL[]) v.toArray(new ACL[0]);
		}
	}

	public Rights[] listRights(String mbox, String user) throws ProtocolException {
		if (!this.hasCapability("ACL")) {
			throw new BadCommandException("ACL not supported");
		} else {
			Argument args = new Argument();
			this.writeMailboxName(args, mbox);
			args.writeString(user);
			Response[] r = this.command("LISTRIGHTS", args);
			Response response = r[r.length - 1];
			List<Rights> v = new ArrayList();
			if (response.isOK()) {
				int i = 0;

				for (int len = r.length; i < len; ++i) {
					if (r[i] instanceof IMAPResponse) {
						IMAPResponse ir = (IMAPResponse) r[i];
						if (ir.keyEquals("LISTRIGHTS")) {
							ir.readAtomString();
							ir.readAtomString();

							String rights;
							while ((rights = ir.readAtomString()) != null) {
								v.add(new Rights(rights));
							}

							r[i] = null;
						}
					}
				}
			}

			this.notifyResponseHandlers(r);
			this.handleResult(response);
			return (Rights[]) v.toArray(new Rights[0]);
		}
	}

	public Rights myRights(String mbox) throws ProtocolException {
		if (!this.hasCapability("ACL")) {
			throw new BadCommandException("ACL not supported");
		} else {
			Argument args = new Argument();
			this.writeMailboxName(args, mbox);
			Response[] r = this.command("MYRIGHTS", args);
			Response response = r[r.length - 1];
			Rights rights = null;
			if (response.isOK()) {
				int i = 0;

				for (int len = r.length; i < len; ++i) {
					if (r[i] instanceof IMAPResponse) {
						IMAPResponse ir = (IMAPResponse) r[i];
						if (ir.keyEquals("MYRIGHTS")) {
							ir.readAtomString();
							String rs = ir.readAtomString();
							if (rights == null) {
								rights = new Rights(rs);
							}

							r[i] = null;
						}
					}
				}
			}

			this.notifyResponseHandlers(r);
			this.handleResult(response);
			return rights;
		}
	}

	public synchronized void idleStart() throws ProtocolException {
		if (!this.hasCapability("IDLE")) {
			throw new BadCommandException("IDLE not supported");
		} else {
			List<Response> v = new ArrayList();
			boolean done = false;
			Response r = null;

			try {
				this.idleTag = this.writeCommand("IDLE", (Argument) null);
			} catch (LiteralException var5) {
				v.add(var5.getResponse());
				done = true;
			} catch (Exception var6) {
				v.add(Response.byeResponse(var6));
				done = true;
			}

			while (!done) {
				try {
					r = this.readResponse();
				} catch (IOException var7) {
					r = Response.byeResponse(var7);
				} catch (ProtocolException var8) {
					continue;
				}

				v.add(r);
				if (r.isContinuation() || r.isBYE()) {
					done = true;
				}
			}

			Response[] responses = (Response[]) v.toArray(new Response[0]);
			r = responses[responses.length - 1];
			this.notifyResponseHandlers(responses);
			if (!r.isContinuation()) {
				this.handleResult(r);
			}

		}
	}

	public synchronized Response readIdleResponse() {
		if (this.idleTag == null) {
			return null;
		} else {
			Response r = null;

			try {
				r = this.readResponse();
			} catch (ProtocolException | IOException var3) {
				r = Response.byeResponse(var3);
			}

			return r;
		}
	}

	public boolean processIdleResponse(Response r) throws ProtocolException {
		Response[] responses = new Response[]{r};
		boolean done = false;
		this.notifyResponseHandlers(responses);
		if (r.isBYE()) {
			done = true;
		}

		if (r.isTagged() && r.getTag().equals(this.idleTag)) {
			done = true;
		}

		if (done) {
			this.idleTag = null;
		}

		this.handleResult(r);
		return !done;
	}

	public void idleAbort() {
		OutputStream os = this.getOutputStream();

		try {
			os.write(DONE);
			os.flush();
		} catch (Exception var3) {
			this.logger.log(Level.FINEST, "Exception aborting IDLE", var3);
		}

	}

	public Map<String, String> id(Map<String, String> clientParams) throws ProtocolException {
		if (!this.hasCapability("ID")) {
			throw new BadCommandException("ID not supported");
		} else {
			Response[] r = this.command("ID", ID.getArgumentList(clientParams));
			ID id = null;
			Response response = r[r.length - 1];
			if (response.isOK()) {
				int i = 0;

				for (int len = r.length; i < len; ++i) {
					if (r[i] instanceof IMAPResponse) {
						IMAPResponse ir = (IMAPResponse) r[i];
						if (ir.keyEquals("ID")) {
							if (id == null) {
								id = new ID(ir);
							}

							r[i] = null;
						}
					}
				}
			}

			this.notifyResponseHandlers(r);
			this.handleResult(response);
			return id == null ? null : id.getServerParams();
		}
	}
}