package org.eclipse.angus.mail.imap;

import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.FolderNotFoundException;
import jakarta.mail.Message;
import jakarta.mail.MessageRemovedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Quota;
import jakarta.mail.ReadOnlyFolderException;
import jakarta.mail.StoreClosedException;
import jakarta.mail.UIDFolder;
import jakarta.mail.FetchProfile.Item;
import jakarta.mail.Flags.Flag;
import jakarta.mail.event.MailEvent;
import jakarta.mail.event.MessageChangedEvent;
import jakarta.mail.event.MessageCountListener;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.SearchException;
import jakarta.mail.search.SearchTerm;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import org.eclipse.angus.mail.iap.BadCommandException;
import org.eclipse.angus.mail.iap.CommandFailedException;
import org.eclipse.angus.mail.iap.ConnectionException;
import org.eclipse.angus.mail.iap.ProtocolException;
import org.eclipse.angus.mail.iap.Response;
import org.eclipse.angus.mail.iap.ResponseHandler;
import org.eclipse.angus.mail.imap.protocol.FLAGS;
import org.eclipse.angus.mail.imap.protocol.FetchItem;
import org.eclipse.angus.mail.imap.protocol.FetchResponse;
import org.eclipse.angus.mail.imap.protocol.IMAPProtocol;
import org.eclipse.angus.mail.imap.protocol.IMAPResponse;
import org.eclipse.angus.mail.imap.protocol.ListInfo;
import org.eclipse.angus.mail.imap.protocol.MODSEQ;
import org.eclipse.angus.mail.imap.protocol.MailboxInfo;
import org.eclipse.angus.mail.imap.protocol.MessageSet;
import org.eclipse.angus.mail.imap.protocol.Status;
import org.eclipse.angus.mail.imap.protocol.UID;
import org.eclipse.angus.mail.imap.protocol.UIDSet;
import org.eclipse.angus.mail.util.MailLogger;

public class IMAPFolder extends Folder implements UIDFolder, ResponseHandler {
	protected volatile String fullName;
	protected String name;
	protected int type;
	protected char separator;
	protected Flags availableFlags;
	protected Flags permanentFlags;
	protected volatile boolean exists;
	protected boolean isNamespace;
	protected volatile String[] attributes;
	protected volatile IMAPProtocol protocol;
	protected MessageCache messageCache;
	protected final Object messageCacheLock;
	protected Hashtable<Long, IMAPMessage> uidTable;
	protected static final char UNKNOWN_SEPARATOR = '￿';
	private volatile boolean opened;
	private boolean reallyClosed;
	private static final int RUNNING = 0;
	private static final int IDLE = 1;
	private static final int ABORTING = 2;
	private int idleState;
	private IdleManager idleManager;
	private volatile int total;
	private volatile int recent;
	private int realTotal;
	private long uidvalidity;
	private long uidnext;
	private boolean uidNotSticky;
	private volatile long highestmodseq;
	private boolean doExpungeNotification;
	private Status cachedStatus;
	private long cachedStatusTime;
	private boolean hasMessageCountListener;
	protected MailLogger logger;
	private MailLogger connectionPoolLogger;

	protected IMAPFolder(String fullName, char separator, IMAPStore store, Boolean isNamespace) {
		super(store);
		this.isNamespace = false;
		this.messageCacheLock = new Object();
		this.opened = false;
		this.reallyClosed = true;
		this.idleState = 0;
		this.total = -1;
		this.recent = -1;
		this.realTotal = -1;
		this.uidvalidity = -1L;
		this.uidnext = -1L;
		this.uidNotSticky = false;
		this.highestmodseq = -1L;
		this.doExpungeNotification = true;
		this.cachedStatus = null;
		this.cachedStatusTime = 0L;
		this.hasMessageCountListener = false;
		if (fullName == null) {
			throw new NullPointerException("Folder name is null");
		} else {
			this.fullName = fullName;
			this.separator = separator;
			this.logger = new MailLogger(this.getClass(), "DEBUG IMAP", store.getSession().getDebug(),
					store.getSession().getDebugOut());
			this.connectionPoolLogger = store.getConnectionPoolLogger();
			this.isNamespace = false;
			if (separator != '￿' && separator != 0) {
				int i = this.fullName.indexOf(separator);
				if (i > 0 && i == this.fullName.length() - 1) {
					this.fullName = this.fullName.substring(0, i);
					this.isNamespace = true;
				}
			}

			if (isNamespace != null) {
				this.isNamespace = isNamespace;
			}

		}
	}

	protected IMAPFolder(ListInfo li, IMAPStore store) {
		this(li.name, li.separator, store, (Boolean) null);
		if (li.hasInferiors) {
			this.type |= 2;
		}

		if (li.canOpen) {
			this.type |= 1;
		}

		this.exists = true;
		this.attributes = li.attrs;
	}

	protected void checkExists() throws MessagingException {
		if (!this.exists && !this.exists()) {
			throw new FolderNotFoundException(this, this.fullName + " not found");
		}
	}

	protected void checkClosed() {
		if (this.opened) {
			throw new IllegalStateException("This operation is not allowed on an open folder");
		}
	}

	protected void checkOpened() throws FolderClosedException {
		assert Thread.holdsLock(this);

		if (!this.opened) {
			if (this.reallyClosed) {
				throw new IllegalStateException("This operation is not allowed on a closed folder");
			} else {
				throw new FolderClosedException(this, "Lost folder connection to server");
			}
		}
	}

	protected void checkRange(int msgno) throws MessagingException {
		if (msgno < 1) {
			throw new IndexOutOfBoundsException("message number < 1");
		} else if (msgno > this.total) {
			synchronized (this.messageCacheLock) {
				try {
					this.keepConnectionAlive(false);
				} catch (ConnectionException var5) {
					throw new FolderClosedException(this, var5.getMessage());
				} catch (ProtocolException var6) {
					throw new MessagingException(var6.getMessage(), var6);
				}
			}

			if (msgno > this.total) {
				throw new IndexOutOfBoundsException(msgno + " > " + this.total);
			}
		}
	}

	private void checkFlags(Flags flags) throws MessagingException {
		assert Thread.holdsLock(this);

		if (this.mode != 2) {
			throw new IllegalStateException("Cannot change flags on READ_ONLY folder: " + this.fullName);
		}
	}

	public synchronized String getName() {
		if (this.name == null) {
			try {
				this.name = this.fullName.substring(this.fullName.lastIndexOf(this.getSeparator()) + 1);
			} catch (MessagingException var2) {
			}
		}

		return this.name;
	}

	public String getFullName() {
		return this.fullName;
	}

	public synchronized Folder getParent() throws MessagingException {
		char c = this.getSeparator();
		int index;
		return (Folder) ((index = this.fullName.lastIndexOf(c)) != -1
				? ((IMAPStore) this.store).newIMAPFolder(this.fullName.substring(0, index), c)
				: new DefaultFolder((IMAPStore) this.store));
	}

	public synchronized boolean exists() throws MessagingException {
		ListInfo[] li = null;
		final String lname;
		if (this.isNamespace && this.separator != 0) {
			lname = this.fullName + this.separator;
		} else {
			lname = this.fullName;
		}

		li = (ListInfo[]) this.doCommand(new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				return p.list("", lname);
			}
		});
		if (li != null) {
			int i = this.findName(li, lname);
			this.fullName = li[i].name;
			this.separator = li[i].separator;
			int len = this.fullName.length();
			if (this.separator != 0 && len > 0 && this.fullName.charAt(len - 1) == this.separator) {
				this.fullName = this.fullName.substring(0, len - 1);
			}

			this.type = 0;
			if (li[i].hasInferiors) {
				this.type |= 2;
			}

			if (li[i].canOpen) {
				this.type |= 1;
			}

			this.exists = true;
			this.attributes = li[i].attrs;
		} else {
			this.exists = this.opened;
			this.attributes = null;
		}

		return this.exists;
	}

	private int findName(ListInfo[] li, String lname) {
		int i;
		for (i = 0; i < li.length && !li[i].name.equals(lname); ++i) {
		}

		if (i >= li.length) {
			i = 0;
		}

		return i;
	}

	public Folder[] list(String pattern) throws MessagingException {
		return this.doList(pattern, false);
	}

	public Folder[] listSubscribed(String pattern) throws MessagingException {
		return this.doList(pattern, true);
	}

	private synchronized Folder[] doList(final String pattern, final boolean subscribed) throws MessagingException {
		this.checkExists();
		if (this.attributes != null && !this.isDirectory()) {
			return new Folder[0];
		} else {
			final char c = this.getSeparator();
			ListInfo[] li = (ListInfo[]) this.doCommandIgnoreFailure(new ProtocolCommand() {
				public Object doCommand(IMAPProtocol p) throws ProtocolException {
					return subscribed
							? p.lsub("", IMAPFolder.this.fullName + c + pattern)
							: p.list("", IMAPFolder.this.fullName + c + pattern);
				}
			});
			if (li == null) {
				return new Folder[0];
			} else {
				int start = 0;
				if (li.length > 0 && li[0].name.equals(this.fullName + c)) {
					start = 1;
				}

				IMAPFolder[] folders = new IMAPFolder[li.length - start];
				IMAPStore st = (IMAPStore) this.store;

				for (int i = start; i < li.length; ++i) {
					folders[i - start] = st.newIMAPFolder(li[i]);
				}

				return folders;
			}
		}
	}

	public synchronized char getSeparator() throws MessagingException {
		if (this.separator == '￿') {
			ListInfo[] li = null;
			li = (ListInfo[]) this.doCommand(new ProtocolCommand() {
				public Object doCommand(IMAPProtocol p) throws ProtocolException {
					return p.isREV1() ? p.list(IMAPFolder.this.fullName, "") : p.list("", IMAPFolder.this.fullName);
				}
			});
			if (li != null) {
				this.separator = li[0].separator;
			} else {
				this.separator = '/';
			}
		}

		return this.separator;
	}

	public synchronized int getType() throws MessagingException {
		if (this.opened) {
			if (this.attributes == null) {
				this.exists();
			}
		} else {
			this.checkExists();
		}

		return this.type;
	}

	public synchronized boolean isSubscribed() {
		ListInfo[] li = null;
		final String lname;
		if (this.isNamespace && this.separator != 0) {
			lname = this.fullName + this.separator;
		} else {
			lname = this.fullName;
		}

		try {
			li = (ListInfo[]) this.doProtocolCommand(new ProtocolCommand() {
				public Object doCommand(IMAPProtocol p) throws ProtocolException {
					return p.lsub("", lname);
				}
			});
		} catch (ProtocolException var4) {
		}

		if (li != null) {
			int i = this.findName(li, lname);
			return li[i].canOpen;
		} else {
			return false;
		}
	}

	public synchronized void setSubscribed(final boolean subscribe) throws MessagingException {
		this.doCommandIgnoreFailure(new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				if (subscribe) {
					p.subscribe(IMAPFolder.this.fullName);
				} else {
					p.unsubscribe(IMAPFolder.this.fullName);
				}

				return null;
			}
		});
	}

	public synchronized boolean create(final int type) throws MessagingException {
		final char c = 0;
		if ((type & 1) == 0) {
			c = this.getSeparator();
		}

		Object ret = this.doCommandIgnoreFailure(new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				if ((type & 1) == 0) {
					p.create(IMAPFolder.this.fullName + c);
				} else {
					p.create(IMAPFolder.this.fullName);
					if ((type & 2) != 0) {
						ListInfo[] li = p.list("", IMAPFolder.this.fullName);
						if (li != null && !li[0].hasInferiors) {
							p.delete(IMAPFolder.this.fullName);
							throw new ProtocolException("Unsupported type");
						}
					}
				}

				return Boolean.TRUE;
			}
		});
		if (ret == null) {
			return false;
		} else {
			boolean retb = this.exists();
			if (retb) {
				this.notifyFolderListeners(1);
			}

			return retb;
		}
	}

	public synchronized boolean hasNewMessages() throws MessagingException {
		synchronized (this.messageCacheLock) {
			if (this.opened) {
				try {
					this.keepConnectionAlive(true);
				} catch (ConnectionException var5) {
					throw new FolderClosedException(this, var5.getMessage());
				} catch (ProtocolException var6) {
					throw new MessagingException(var6.getMessage(), var6);
				}

				return this.recent > 0;
			}
		}

		ListInfo[] li = null;
		final String lname;
		if (this.isNamespace && this.separator != 0) {
			lname = this.fullName + this.separator;
		} else {
			lname = this.fullName;
		}

		li = (ListInfo[]) this.doCommandIgnoreFailure(new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				return p.list("", lname);
			}
		});
		if (li == null) {
			throw new FolderNotFoundException(this, this.fullName + " not found");
		} else {
			int i = this.findName(li, lname);
			if (li[i].changeState == 1) {
				return true;
			} else if (li[i].changeState == 2) {
				return false;
			} else {
				try {
					Status status = this.getStatus();
					return status.recent > 0;
				} catch (BadCommandException var7) {
					return false;
				} catch (ConnectionException var8) {
					throw new StoreClosedException(this.store, var8.getMessage());
				} catch (ProtocolException var9) {
					throw new MessagingException(var9.getMessage(), var9);
				}
			}
		}
	}

	public synchronized Folder getFolder(String name) throws MessagingException {
		if (this.attributes != null && !this.isDirectory()) {
			throw new MessagingException("Cannot contain subfolders");
		} else {
			char c = this.getSeparator();
			return ((IMAPStore) this.store).newIMAPFolder(this.fullName + c + name, c);
		}
	}

	public synchronized boolean delete(boolean recurse) throws MessagingException {
		this.checkClosed();
		if (recurse) {
			Folder[] f = this.list();

			for (int i = 0; i < f.length; ++i) {
				f[i].delete(recurse);
			}
		}

		Object ret = this.doCommandIgnoreFailure(new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				p.delete(IMAPFolder.this.fullName);
				return Boolean.TRUE;
			}
		});
		if (ret == null) {
			return false;
		} else {
			this.exists = false;
			this.attributes = null;
			this.notifyFolderListeners(2);
			return true;
		}
	}

	public synchronized boolean renameTo(final Folder f) throws MessagingException {
		this.checkClosed();
		this.checkExists();
		if (f.getStore() != this.store) {
			throw new MessagingException("Can't rename across Stores");
		} else {
			Object ret = this.doCommandIgnoreFailure(new ProtocolCommand() {
				public Object doCommand(IMAPProtocol p) throws ProtocolException {
					p.rename(IMAPFolder.this.fullName, f.getFullName());
					return Boolean.TRUE;
				}
			});
			if (ret == null) {
				return false;
			} else {
				this.exists = false;
				this.attributes = null;
				this.notifyFolderRenamedListeners(f);
				return true;
			}
		}
	}

	public synchronized void open(int mode) throws MessagingException {
		this.open(mode, (ResyncData) null);
	}

	public synchronized List<MailEvent> open(int mode, ResyncData rd) throws MessagingException {
		this.checkClosed();
		MailboxInfo mi = null;
		this.protocol = ((IMAPStore) this.store).getProtocol(this);
		List<MailEvent> openEvents = null;
		synchronized (this.messageCacheLock) {
			this.protocol.addResponseHandler(this);

			try {
				if (rd != null) {
					if (rd == ResyncData.CONDSTORE) {
						if (!this.protocol.isEnabled("CONDSTORE") && !this.protocol.isEnabled("QRESYNC")) {
							if (this.protocol.hasCapability("CONDSTORE")) {
								this.protocol.enable("CONDSTORE");
							} else {
								this.protocol.enable("QRESYNC");
							}
						}
					} else if (!this.protocol.isEnabled("QRESYNC")) {
						this.protocol.enable("QRESYNC");
					}
				}

				if (mode == 1) {
					mi = this.protocol.examine(this.fullName, rd);
				} else {
					mi = this.protocol.select(this.fullName, rd);
				}
			} catch (CommandFailedException var25) {
				CommandFailedException cex = var25;

				try {
					this.checkExists();
					if ((this.type & 1) == 0) {
						throw new MessagingException("folder cannot contain messages");
					}

					throw new MessagingException(cex.getMessage(), cex);
				} finally {
					this.exists = false;
					this.attributes = null;
					this.type = 0;
					this.releaseProtocol(true);
				}
			} catch (ProtocolException var26) {
				ProtocolException pex = var26;

				try {
					throw this.logoutAndThrow(pex.getMessage(), pex);
				} finally {
					this.releaseProtocol(false);
				}
			}

			if (mi.mode != mode && (mode != 2 || mi.mode != 1 || !((IMAPStore) this.store).allowReadOnlySelect())) {
				ReadOnlyFolderException ife = new ReadOnlyFolderException(this, "Cannot open in desired mode");
				throw this.cleanupAndThrow(ife);
			}

			this.opened = true;
			this.reallyClosed = false;
			this.mode = mi.mode;
			this.availableFlags = mi.availableFlags;
			this.permanentFlags = mi.permanentFlags;
			this.total = this.realTotal = mi.total;
			this.recent = mi.recent;
			this.uidvalidity = mi.uidvalidity;
			this.uidnext = mi.uidnext;
			this.uidNotSticky = mi.uidNotSticky;
			this.highestmodseq = mi.highestmodseq;
			this.messageCache = new MessageCache(this, (IMAPStore) this.store, this.total);
			if (mi.responses != null) {
				openEvents = new ArrayList();
				Iterator var29 = mi.responses.iterator();

				while (var29.hasNext()) {
					IMAPResponse ir = (IMAPResponse) var29.next();
					if (ir.keyEquals("VANISHED")) {
						String[] s = ir.readAtomStringList();
						if (s != null && s.length == 1 && s[0].equalsIgnoreCase("EARLIER")) {
							String uids = ir.readAtom();
							UIDSet[] uidset = UIDSet.parseUIDSets(uids);
							long[] luid = UIDSet.toArray(uidset, this.uidnext);
							if (luid != null && luid.length > 0) {
								openEvents.add(new MessageVanishedEvent(this, luid));
							}
						}
					} else if (ir.keyEquals("FETCH")) {
						assert ir instanceof FetchResponse : "!ir instanceof FetchResponse";

						Message msg = this.processFetchResponse((FetchResponse) ir);
						if (msg != null) {
							openEvents.add(new MessageChangedEvent(this, 1, msg));
						}
					}
				}
			}
		}

		this.exists = true;
		this.attributes = null;
		this.type = 1;
		this.notifyConnectionListeners(1);
		return openEvents;
	}

	private MessagingException cleanupAndThrow(MessagingException ife) {
		try {
			try {
				this.protocol.close();
				this.releaseProtocol(true);
			} catch (ProtocolException var8) {
				ProtocolException pex = var8;

				try {
					this.addSuppressed(ife, this.logoutAndThrow(pex.getMessage(), pex));
				} finally {
					this.releaseProtocol(false);
				}
			}
		} catch (Throwable var9) {
			this.addSuppressed(ife, var9);
		}

		return ife;
	}

	private MessagingException logoutAndThrow(String why, ProtocolException t) {
		MessagingException ife = new MessagingException(why, t);

		try {
			this.protocol.logout();
		} catch (Throwable var5) {
			this.addSuppressed(ife, var5);
		}

		return ife;
	}

	private void addSuppressed(Throwable ife, Throwable thr) {
		if (this.isRecoverable(thr)) {
			ife.addSuppressed(thr);
		} else {
			thr.addSuppressed(ife);
			if (thr instanceof Error) {
				throw (Error) thr;
			} else if (thr instanceof RuntimeException) {
				throw (RuntimeException) thr;
			} else {
				throw new RuntimeException("unexpected exception", thr);
			}
		}
	}

	private boolean isRecoverable(Throwable t) {
		return t instanceof Exception || t instanceof LinkageError;
	}

	public synchronized void fetch(Message[] msgs, FetchProfile fp) throws MessagingException {
		boolean isRev1;
		FetchItem[] fitems;
		synchronized (this.messageCacheLock) {
			this.checkOpened();
			isRev1 = this.protocol.isREV1();
			fitems = this.protocol.getFetchItems();
		}

		StringBuilder command = new StringBuilder();
		boolean first = true;
		boolean allHeaders = false;
		if (fp.contains(Item.ENVELOPE)) {
			command.append(this.getEnvelopeCommand());
			first = false;
		}

		if (fp.contains(Item.FLAGS)) {
			command.append(first ? "FLAGS" : " FLAGS");
			first = false;
		}

		if (fp.contains(Item.CONTENT_INFO)) {
			command.append(first ? "BODYSTRUCTURE" : " BODYSTRUCTURE");
			first = false;
		}

		if (fp.contains(jakarta.mail.UIDFolder.FetchProfileItem.UID)) {
			command.append(first ? "UID" : " UID");
			first = false;
		}

		if (fp.contains(IMAPFolder.FetchProfileItem.HEADERS)) {
			allHeaders = true;
			if (isRev1) {
				command.append(first ? "BODY.PEEK[HEADER]" : " BODY.PEEK[HEADER]");
			} else {
				command.append(first ? "RFC822.HEADER" : " RFC822.HEADER");
			}

			first = false;
		}

		if (fp.contains(IMAPFolder.FetchProfileItem.MESSAGE)) {
			allHeaders = true;
			if (isRev1) {
				command.append(first ? "BODY.PEEK[]" : " BODY.PEEK[]");
			} else {
				command.append(first ? "RFC822" : " RFC822");
			}

			first = false;
		}

		if (fp.contains(Item.SIZE) || fp.contains(IMAPFolder.FetchProfileItem.SIZE)) {
			command.append(first ? "RFC822.SIZE" : " RFC822.SIZE");
			first = false;
		}

		if (fp.contains(IMAPFolder.FetchProfileItem.INTERNALDATE)) {
			command.append(first ? "INTERNALDATE" : " INTERNALDATE");
			first = false;
		}

		String[] hdrs = null;
		if (!allHeaders) {
			hdrs = fp.getHeaderNames();
			if (hdrs.length > 0) {
				if (!first) {
					command.append(" ");
				}

				command.append(this.createHeaderCommand(hdrs, isRev1));
			}
		}

		for (int i = 0; i < fitems.length; ++i) {
			if (fp.contains(fitems[i].getFetchProfileItem())) {
				if (command.length() != 0) {
					command.append(" ");
				}

				command.append(fitems[i].getName());
			}
		}

		Utility.Condition condition = new IMAPMessage.FetchProfileCondition(fp, fitems);
		synchronized (this.messageCacheLock) {
			this.checkOpened();
			MessageSet[] msgsets = Utility.toMessageSetSorted(msgs, condition);
			if (msgsets != null) {
				Response[] r = null;
				List<Response> v = new ArrayList();

				try {
					r = this.getProtocol().fetch(msgsets, command.toString());
				} catch (ConnectionException var22) {
					throw new FolderClosedException(this, var22.getMessage());
				} catch (CommandFailedException var23) {
				} catch (ProtocolException var24) {
					throw new MessagingException(var24.getMessage(), var24);
				}

				if (r != null) {
					for (int i = 0; i < r.length; ++i) {
						if (r[i] != null) {
							if (!(r[i] instanceof FetchResponse)) {
								v.add(r[i]);
							} else {
								FetchResponse f = (FetchResponse) r[i];
								IMAPMessage msg = this.getMessageBySeqNumber(f.getNumber());
								int count = f.getItemCount();
								boolean unsolicitedFlags = false;

								for (int j = 0; j < count; ++j) {
									org.eclipse.angus.mail.imap.protocol.Item item = f.getItem(j);
									if (!(item instanceof Flags) || fp.contains(Item.FLAGS) && msg != null) {
										if (msg != null) {
											msg.handleFetchItem(item, hdrs, allHeaders);
										}
									} else {
										unsolicitedFlags = true;
									}
								}

								if (msg != null) {
									msg.handleExtensionFetchItems(f.getExtensionItems());
								}

								if (unsolicitedFlags) {
									v.add(f);
								}
							}
						}
					}

					if (!v.isEmpty()) {
						Response[] responses = new Response[v.size()];
						v.toArray(responses);
						this.handleResponses(responses);
					}

				}
			}
		}
	}

	protected String getEnvelopeCommand() {
		return "ENVELOPE INTERNALDATE RFC822.SIZE";
	}

	protected IMAPMessage newIMAPMessage(int msgnum) {
		return new IMAPMessage(this, msgnum);
	}

	private String createHeaderCommand(String[] hdrs, boolean isRev1) {
		StringBuilder sb;
		if (isRev1) {
			sb = new StringBuilder("BODY.PEEK[HEADER.FIELDS (");
		} else {
			sb = new StringBuilder("RFC822.HEADER.LINES (");
		}

		for (int i = 0; i < hdrs.length; ++i) {
			if (i > 0) {
				sb.append(" ");
			}

			sb.append(hdrs[i]);
		}

		if (isRev1) {
			sb.append(")]");
		} else {
			sb.append(")");
		}

		return sb.toString();
	}

	public synchronized void setFlags(Message[] msgs, Flags flag, boolean value) throws MessagingException {
		this.checkOpened();
		this.checkFlags(flag);
		if (msgs.length != 0) {
			synchronized (this.messageCacheLock) {
				try {
					IMAPProtocol p = this.getProtocol();
					MessageSet[] ms = Utility.toMessageSetSorted(msgs, (Utility.Condition) null);
					if (ms == null) {
						throw new MessageRemovedException("Messages have been removed");
					}

					p.storeFlags(ms, flag, value);
				} catch (ConnectionException var8) {
					throw new FolderClosedException(this, var8.getMessage());
				} catch (ProtocolException var9) {
					throw new MessagingException(var9.getMessage(), var9);
				}

			}
		}
	}

	public synchronized void setFlags(int start, int end, Flags flag, boolean value) throws MessagingException {
		this.checkOpened();
		Message[] msgs = new Message[end - start + 1];
		int i = 0;

		for (int n = start; n <= end; ++n) {
			msgs[i++] = this.getMessage(n);
		}

		this.setFlags(msgs, flag, value);
	}

	public synchronized void setFlags(int[] msgnums, Flags flag, boolean value) throws MessagingException {
		this.checkOpened();
		Message[] msgs = new Message[msgnums.length];

		for (int i = 0; i < msgnums.length; ++i) {
			msgs[i] = this.getMessage(msgnums[i]);
		}

		this.setFlags(msgs, flag, value);
	}

	public synchronized void close(boolean expunge) throws MessagingException {
		this.close(expunge, false);
	}

	public synchronized void forceClose() throws MessagingException {
		this.close(false, true);
	}

	private void close(boolean expunge, boolean force) throws MessagingException {
		assert Thread.holdsLock(this);

		synchronized (this.messageCacheLock) {
			if (!this.opened && this.reallyClosed) {
				throw new IllegalStateException("This operation is not allowed on a closed folder");
			} else {
				this.reallyClosed = true;
				if (this.opened) {
					boolean reuseProtocol = true;

					try {
						this.waitIfIdle();
						if (force) {
							this.logger.log(Level.FINE, "forcing folder {0} to close", this.fullName);
							if (this.protocol != null) {
								this.protocol.disconnect();
							}
						} else if (((IMAPStore) this.store).isConnectionPoolFull()) {
							this.logger.fine("pool is full, not adding an Authenticated connection");
							if (expunge && this.protocol != null) {
								this.protocol.close();
							}

							if (this.protocol != null) {
								this.protocol.logout();
							}
						} else if (!expunge && this.mode == 2) {
							try {
								if (this.protocol != null && this.protocol.hasCapability("UNSELECT")) {
									this.protocol.unselect();
								} else if (this.protocol != null) {
									boolean selected = true;

									try {
										this.protocol.examine(this.fullName);
									} catch (CommandFailedException var14) {
										selected = false;
									}

									if (selected && this.protocol != null) {
										this.protocol.close();
									}
								}
							} catch (ProtocolException var15) {
								reuseProtocol = false;
							}
						} else if (this.protocol != null) {
							this.protocol.close();
						}
					} catch (ProtocolException var16) {
						throw new MessagingException(var16.getMessage(), var16);
					} finally {
						if (this.opened) {
							this.cleanup(reuseProtocol);
						}

					}

				}
			}
		}
	}

	private void cleanup(boolean returnToPool) {
		assert Thread.holdsLock(this.messageCacheLock);

		this.releaseProtocol(returnToPool);
		this.messageCache = null;
		this.uidTable = null;
		this.exists = false;
		this.attributes = null;
		this.opened = false;
		this.idleState = 0;
		this.messageCacheLock.notifyAll();
		this.notifyConnectionListeners(3);
	}

	public synchronized boolean isOpen() {
		synchronized (this.messageCacheLock) {
			if (this.opened) {
				try {
					this.keepConnectionAlive(false);
				} catch (ProtocolException var4) {
				}
			}
		}

		return this.opened;
	}

	public synchronized Flags getPermanentFlags() {
		return this.permanentFlags == null ? null : (Flags) this.permanentFlags.clone();
	}

	public synchronized int getMessageCount() throws MessagingException {
		synchronized (this.messageCacheLock) {
			if (this.opened) {
				int var10000;
				try {
					this.keepConnectionAlive(true);
					var10000 = this.total;
				} catch (ConnectionException var16) {
					throw new FolderClosedException(this, var16.getMessage());
				} catch (ProtocolException var17) {
					throw new MessagingException(var17.getMessage(), var17);
				}

				return var10000;
			}
		}

		this.checkExists();

		try {
			Status status = this.getStatus();
			return status.total;
		} catch (BadCommandException var18) {
			IMAPProtocol p = null;

			int var4;
			try {
				p = this.getStoreProtocol();
				MailboxInfo minfo = p.examine(this.fullName);
				p.close();
				var4 = minfo.total;
			} catch (ProtocolException var14) {
				throw new MessagingException(var14.getMessage(), var14);
			} finally {
				this.releaseStoreProtocol(p);
			}

			return var4;
		} catch (ConnectionException var19) {
			throw new StoreClosedException(this.store, var19.getMessage());
		} catch (ProtocolException var20) {
			throw new MessagingException(var20.getMessage(), var20);
		}
	}

	public synchronized int getNewMessageCount() throws MessagingException {
		synchronized (this.messageCacheLock) {
			if (this.opened) {
				int var10000;
				try {
					this.keepConnectionAlive(true);
					var10000 = this.recent;
				} catch (ConnectionException var16) {
					throw new FolderClosedException(this, var16.getMessage());
				} catch (ProtocolException var17) {
					throw new MessagingException(var17.getMessage(), var17);
				}

				return var10000;
			}
		}

		this.checkExists();

		try {
			Status status = this.getStatus();
			return status.recent;
		} catch (BadCommandException var18) {
			IMAPProtocol p = null;

			int var4;
			try {
				p = this.getStoreProtocol();
				MailboxInfo minfo = p.examine(this.fullName);
				p.close();
				var4 = minfo.recent;
			} catch (ProtocolException var14) {
				throw new MessagingException(var14.getMessage(), var14);
			} finally {
				this.releaseStoreProtocol(p);
			}

			return var4;
		} catch (ConnectionException var19) {
			throw new StoreClosedException(this.store, var19.getMessage());
		} catch (ProtocolException var20) {
			throw new MessagingException(var20.getMessage(), var20);
		}
	}

	public synchronized int getUnreadMessageCount() throws MessagingException {
		if (!this.opened) {
			this.checkExists();

			try {
				Status status = this.getStatus();
				return status.unseen;
			} catch (BadCommandException var5) {
				return -1;
			} catch (ConnectionException var6) {
				throw new StoreClosedException(this.store, var6.getMessage());
			} catch (ProtocolException var7) {
				throw new MessagingException(var7.getMessage(), var7);
			}
		} else {
			Flags f = new Flags();
			f.add(Flag.SEEN);

			try {
				synchronized (this.messageCacheLock) {
					int[] matches = this.getProtocol().search(new FlagTerm(f, false));
					return matches.length;
				}
			} catch (ConnectionException var9) {
				throw new FolderClosedException(this, var9.getMessage());
			} catch (ProtocolException var10) {
				throw new MessagingException(var10.getMessage(), var10);
			}
		}
	}

	public synchronized int getDeletedMessageCount() throws MessagingException {
		if (!this.opened) {
			this.checkExists();
			return -1;
		} else {
			Flags f = new Flags();
			f.add(Flag.DELETED);

			try {
				synchronized (this.messageCacheLock) {
					int[] matches = this.getProtocol().search(new FlagTerm(f, true));
					return matches.length;
				}
			} catch (ConnectionException var6) {
				throw new FolderClosedException(this, var6.getMessage());
			} catch (ProtocolException var7) {
				throw new MessagingException(var7.getMessage(), var7);
			}
		}
	}

	private Status getStatus() throws ProtocolException {
		int statusCacheTimeout = ((IMAPStore) this.store).getStatusCacheTimeout();
		if (statusCacheTimeout > 0 && this.cachedStatus != null
				&& System.currentTimeMillis() - this.cachedStatusTime < (long) statusCacheTimeout) {
			return this.cachedStatus;
		} else {
			IMAPProtocol p = null;

			Status var4;
			try {
				p = this.getStoreProtocol();
				Status s = p.status(this.fullName, (String[]) null);
				if (statusCacheTimeout > 0) {
					this.cachedStatus = s;
					this.cachedStatusTime = System.currentTimeMillis();
				}

				var4 = s;
			} finally {
				this.releaseStoreProtocol(p);
			}

			return var4;
		}
	}

	public synchronized Message getMessage(int msgnum) throws MessagingException {
		this.checkOpened();
		this.checkRange(msgnum);
		return this.messageCache.getMessage(msgnum);
	}

	public synchronized Message[] getMessages() throws MessagingException {
		this.checkOpened();
		int total = this.getMessageCount();
		Message[] msgs = new Message[total];

		for (int i = 1; i <= total; ++i) {
			msgs[i - 1] = this.messageCache.getMessage(i);
		}

		return msgs;
	}

	public synchronized void appendMessages(Message[] msgs) throws MessagingException {
		this.checkExists();
		int maxsize = ((IMAPStore) this.store).getAppendBufferSize();

		for (int i = 0; i < msgs.length; ++i) {
			Message m = msgs[i];
			final Date d = m.getReceivedDate();
			if (d == null) {
				d = m.getSentDate();
			}

			final Flags f = m.getFlags();

			final MessageLiteral mos;
			try {
				mos = new MessageLiteral(m, m.getSize() > maxsize ? 0 : maxsize);
			} catch (IOException var10) {
				throw new MessagingException("IOException while appending messages", var10);
			} catch (MessageRemovedException var11) {
				continue;
			}

			this.doCommand(new ProtocolCommand() {
				public Object doCommand(IMAPProtocol p) throws ProtocolException {
					p.append(IMAPFolder.this.fullName, f, d, mos);
					return null;
				}
			});
		}

	}

	public synchronized AppendUID[] appendUIDMessages(Message[] msgs) throws MessagingException {
		this.checkExists();
		int maxsize = ((IMAPStore) this.store).getAppendBufferSize();
		AppendUID[] uids = new AppendUID[msgs.length];

		for (int i = 0; i < msgs.length; ++i) {
			Message m = msgs[i];

			final MessageLiteral mos;
			try {
				mos = new MessageLiteral(m, m.getSize() > maxsize ? 0 : maxsize);
			} catch (IOException var11) {
				throw new MessagingException("IOException while appending messages", var11);
			} catch (MessageRemovedException var12) {
				continue;
			}

			final Date d = m.getReceivedDate();
			if (d == null) {
				d = m.getSentDate();
			}

			final Flags f = m.getFlags();
			AppendUID auid = (AppendUID) this.doCommand(new ProtocolCommand() {
				public Object doCommand(IMAPProtocol p) throws ProtocolException {
					return p.appenduid(IMAPFolder.this.fullName, f, d, mos);
				}
			});
			uids[i] = auid;
		}

		return uids;
	}

	public synchronized Message[] addMessages(Message[] msgs) throws MessagingException {
		this.checkOpened();
		Message[] rmsgs = new MimeMessage[msgs.length];
		AppendUID[] uids = this.appendUIDMessages(msgs);

		for (int i = 0; i < uids.length; ++i) {
			AppendUID auid = uids[i];
			if (auid != null && auid.uidvalidity == this.uidvalidity) {
				try {
					rmsgs[i] = this.getMessageByUID(auid.uid);
				} catch (MessagingException var7) {
				}
			}
		}

		return rmsgs;
	}

	public synchronized void copyMessages(Message[] msgs, Folder folder) throws MessagingException {
		this.copymoveMessages(msgs, folder, false);
	}

	public synchronized AppendUID[] copyUIDMessages(Message[] msgs, Folder folder) throws MessagingException {
		return this.copymoveUIDMessages(msgs, folder, false);
	}

	public synchronized void moveMessages(Message[] msgs, Folder folder) throws MessagingException {
		this.copymoveMessages(msgs, folder, true);
	}

	public synchronized AppendUID[] moveUIDMessages(Message[] msgs, Folder folder) throws MessagingException {
		return this.copymoveUIDMessages(msgs, folder, true);
	}

	private synchronized void copymoveMessages(Message[] msgs, Folder folder, boolean move) throws MessagingException {
		this.checkOpened();
		if (msgs.length != 0) {
			if (folder.getStore() == this.store) {
				synchronized (this.messageCacheLock) {
					try {
						IMAPProtocol p = this.getProtocol();
						MessageSet[] ms = Utility.toMessageSet(msgs, (Utility.Condition) null);
						if (ms == null) {
							throw new MessageRemovedException("Messages have been removed");
						}

						if (move) {
							p.move(ms, folder.getFullName());
						} else {
							p.copy(ms, folder.getFullName());
						}
					} catch (CommandFailedException var8) {
						if (var8.getMessage().contains("TRYCREATE")) {
							throw new FolderNotFoundException(folder, folder.getFullName() + " does not exist");
						}

						throw new MessagingException(var8.getMessage(), var8);
					} catch (ConnectionException var9) {
						throw new FolderClosedException(this, var9.getMessage());
					} catch (ProtocolException var10) {
						throw new MessagingException(var10.getMessage(), var10);
					}
				}
			} else {
				if (move) {
					throw new MessagingException("Move between stores not supported");
				}

				super.copyMessages(msgs, folder);
			}

		}
	}

	private synchronized AppendUID[] copymoveUIDMessages(Message[] msgs, Folder folder, boolean move)
			throws MessagingException {
		this.checkOpened();
		if (msgs.length == 0) {
			return null;
		} else if (folder.getStore() != this.store) {
			throw new MessagingException(
					move ? "can't moveUIDMessages to a different store" : "can't copyUIDMessages to a different store");
		} else {
			FetchProfile fp = new FetchProfile();
			fp.add(jakarta.mail.UIDFolder.FetchProfileItem.UID);
			this.fetch(msgs, fp);
			synchronized (this.messageCacheLock) {
				try {
					IMAPProtocol p = this.getProtocol();
					MessageSet[] ms = Utility.toMessageSet(msgs, (Utility.Condition) null);
					if (ms == null) {
						throw new MessageRemovedException("Messages have been removed");
					} else {
						CopyUID cuid;
						if (move) {
							cuid = p.moveuid(ms, folder.getFullName());
						} else {
							cuid = p.copyuid(ms, folder.getFullName());
						}

						long[] srcuids = UIDSet.toArray(cuid.src);
						long[] dstuids = UIDSet.toArray(cuid.dst);
						Message[] srcmsgs = this.getMessagesByUID(srcuids);
						AppendUID[] result = new AppendUID[msgs.length];

						for (int i = 0; i < msgs.length; ++i) {
							int j = i;

							do {
								if (msgs[i] == srcmsgs[j]) {
									result[i] = new AppendUID(cuid.uidvalidity, dstuids[j]);
									break;
								}

								++j;
								if (j >= srcmsgs.length) {
									j = 0;
								}
							} while (j != i);
						}

						AppendUID[] var10000 = result;
						return var10000;
					}
				} catch (CommandFailedException var16) {
					if (var16.getMessage().contains("TRYCREATE")) {
						throw new FolderNotFoundException(folder, folder.getFullName() + " does not exist");
					} else {
						throw new MessagingException(var16.getMessage(), var16);
					}
				} catch (ConnectionException var17) {
					throw new FolderClosedException(this, var17.getMessage());
				} catch (ProtocolException var18) {
					throw new MessagingException(var18.getMessage(), var18);
				}
			}
		}
	}

	public synchronized Message[] expunge() throws MessagingException {
		return this.expunge((Message[]) null);
	}

	public synchronized Message[] expunge(Message[] msgs) throws MessagingException {
		this.checkOpened();
		if (msgs != null) {
			FetchProfile fp = new FetchProfile();
			fp.add(jakarta.mail.UIDFolder.FetchProfileItem.UID);
			this.fetch(msgs, fp);
		}

		IMAPMessage[] rmsgs;
		synchronized (this.messageCacheLock) {
			this.doExpungeNotification = false;

			try {
				IMAPProtocol p = this.getProtocol();
				if (msgs != null) {
					p.uidexpunge(Utility.toUIDSet(msgs));
				} else {
					p.expunge();
				}
			} catch (CommandFailedException var14) {
				if (this.mode != 2) {
					throw new IllegalStateException("Cannot expunge READ_ONLY folder: " + this.fullName);
				}

				throw new MessagingException(var14.getMessage(), var14);
			} catch (ConnectionException var15) {
				throw new FolderClosedException(this, var15.getMessage());
			} catch (ProtocolException var16) {
				throw new MessagingException(var16.getMessage(), var16);
			} finally {
				this.doExpungeNotification = true;
			}

			if (msgs != null) {
				rmsgs = this.messageCache.removeExpungedMessages(msgs);
			} else {
				rmsgs = this.messageCache.removeExpungedMessages();
			}

			if (this.uidTable != null) {
				for (int i = 0; i < rmsgs.length; ++i) {
					IMAPMessage m = rmsgs[i];
					long uid = m.getUID();
					if (uid != -1L) {
						this.uidTable.remove(uid);
					}
				}
			}

			this.total = this.messageCache.size();
		}

		if (rmsgs.length > 0) {
			this.notifyMessageRemovedListeners(true, rmsgs);
		}

		return rmsgs;
	}

	public synchronized Message[] search(SearchTerm term) throws MessagingException {
		this.checkOpened();

		try {
			Message[] matchMsgs = null;
			synchronized (this.messageCacheLock) {
				int[] matches = this.getProtocol().search(term);
				if (matches != null) {
					matchMsgs = this.getMessagesBySeqNumbers(matches);
				}
			}

			return matchMsgs;
		} catch (CommandFailedException var7) {
			return super.search(term);
		} catch (SearchException var8) {
			if (((IMAPStore) this.store).throwSearchException()) {
				throw var8;
			} else {
				return super.search(term);
			}
		} catch (ConnectionException var9) {
			throw new FolderClosedException(this, var9.getMessage());
		} catch (ProtocolException var10) {
			throw new MessagingException(var10.getMessage(), var10);
		}
	}

	public synchronized Message[] search(SearchTerm term, Message[] msgs) throws MessagingException {
		this.checkOpened();
		if (msgs.length == 0) {
			return msgs;
		} else {
			try {
				Message[] matchMsgs = null;
				synchronized (this.messageCacheLock) {
					IMAPProtocol p = this.getProtocol();
					MessageSet[] ms = Utility.toMessageSetSorted(msgs, (Utility.Condition) null);
					if (ms == null) {
						throw new MessageRemovedException("Messages have been removed");
					}

					int[] matches = p.search(ms, term);
					if (matches != null) {
						matchMsgs = this.getMessagesBySeqNumbers(matches);
					}
				}

				return matchMsgs;
			} catch (CommandFailedException var10) {
				return super.search(term, msgs);
			} catch (SearchException var11) {
				return super.search(term, msgs);
			} catch (ConnectionException var12) {
				throw new FolderClosedException(this, var12.getMessage());
			} catch (ProtocolException var13) {
				throw new MessagingException(var13.getMessage(), var13);
			}
		}
	}

	public synchronized Message[] getSortedMessages(SortTerm[] term) throws MessagingException {
		return this.getSortedMessages(term, (SearchTerm) null);
	}

	public synchronized Message[] getSortedMessages(SortTerm[] term, SearchTerm sterm) throws MessagingException {
		this.checkOpened();

		try {
			Message[] matchMsgs = null;
			synchronized (this.messageCacheLock) {
				int[] matches = this.getProtocol().sort(term, sterm);
				if (matches != null) {
					matchMsgs = this.getMessagesBySeqNumbers(matches);
				}
			}

			return matchMsgs;
		} catch (CommandFailedException var8) {
			throw new MessagingException(var8.getMessage(), var8);
		} catch (SearchException var9) {
			throw new MessagingException(var9.getMessage(), var9);
		} catch (ConnectionException var10) {
			throw new FolderClosedException(this, var10.getMessage());
		} catch (ProtocolException var11) {
			throw new MessagingException(var11.getMessage(), var11);
		}
	}

	public synchronized void addMessageCountListener(MessageCountListener l) {
		super.addMessageCountListener(l);
		this.hasMessageCountListener = true;
	}

	public synchronized long getUIDValidity() throws MessagingException {
		if (this.opened) {
			return this.uidvalidity;
		} else {
			IMAPProtocol p = null;
			Status status = null;

			try {
				p = this.getStoreProtocol();
				String[] item = new String[]{"UIDVALIDITY"};
				status = p.status(this.fullName, item);
			} catch (BadCommandException var9) {
				throw new MessagingException("Cannot obtain UIDValidity", var9);
			} catch (ConnectionException var10) {
				this.throwClosedException(var10);
			} catch (ProtocolException var11) {
				throw new MessagingException(var11.getMessage(), var11);
			} finally {
				this.releaseStoreProtocol(p);
			}

			if (status == null) {
				throw new MessagingException("Cannot obtain UIDValidity");
			} else {
				return status.uidvalidity;
			}
		}
	}

	public synchronized long getUIDNext() throws MessagingException {
		if (this.opened) {
			return this.uidnext;
		} else {
			IMAPProtocol p = null;
			Status status = null;

			try {
				p = this.getStoreProtocol();
				String[] item = new String[]{"UIDNEXT"};
				status = p.status(this.fullName, item);
			} catch (BadCommandException var9) {
				throw new MessagingException("Cannot obtain UIDNext", var9);
			} catch (ConnectionException var10) {
				this.throwClosedException(var10);
			} catch (ProtocolException var11) {
				throw new MessagingException(var11.getMessage(), var11);
			} finally {
				this.releaseStoreProtocol(p);
			}

			if (status == null) {
				throw new MessagingException("Cannot obtain UIDNext");
			} else {
				return status.uidnext;
			}
		}
	}

	public synchronized Message getMessageByUID(long uid) throws MessagingException {
		this.checkOpened();
		IMAPMessage m = null;

		try {
			synchronized (this.messageCacheLock) {
				Long l = uid;
				if (this.uidTable != null) {
					m = (IMAPMessage) this.uidTable.get(l);
					if (m != null) {
						return m;
					}
				} else {
					this.uidTable = new Hashtable();
				}

				this.getProtocol().fetchSequenceNumber(uid);
				if (this.uidTable != null) {
					m = (IMAPMessage) this.uidTable.get(l);
					if (m != null) {
						return m;
					}
				}

				return m;
			}
		} catch (ConnectionException var8) {
			throw new FolderClosedException(this, var8.getMessage());
		} catch (ProtocolException var9) {
			throw new MessagingException(var9.getMessage(), var9);
		}
	}

	public synchronized Message[] getMessagesByUID(long start, long end) throws MessagingException {
		this.checkOpened();

		try {
			synchronized (this.messageCacheLock) {
				if (this.uidTable == null) {
					this.uidTable = new Hashtable();
				}

				long[] ua = this.getProtocol().fetchSequenceNumbers(start, end);
				List<Message> ma = new ArrayList();

				for (int i = 0; i < ua.length; ++i) {
					Message m = (Message) this.uidTable.get(ua[i]);
					if (m != null) {
						ma.add(m);
					}
				}

				Message[] msgs = (Message[]) ma.toArray(new Message[0]);
				return msgs;
			}
		} catch (ConnectionException var13) {
			throw new FolderClosedException(this, var13.getMessage());
		} catch (ProtocolException var14) {
			throw new MessagingException(var14.getMessage(), var14);
		}
	}

	public synchronized Message[] getMessagesByUID(long[] uids) throws MessagingException {
		this.checkOpened();

		try {
			synchronized (this.messageCacheLock) {
				long[] unavailUids = uids;
				int i;
				if (this.uidTable != null) {
					List<Long> v = new ArrayList();
					long[] var5 = uids;
					int i = uids.length;

					for (int var7 = 0; var7 < i; ++var7) {
						long uid = var5[var7];
						if (!this.uidTable.containsKey(uid)) {
							v.add(uid);
						}
					}

					i = v.size();
					unavailUids = new long[i];

					for (i = 0; i < i; ++i) {
						unavailUids[i] = (Long) v.get(i);
					}
				} else {
					this.uidTable = new Hashtable();
				}

				if (unavailUids.length > 0) {
					this.getProtocol().fetchSequenceNumbers(unavailUids);
				}

				Message[] msgs = new Message[uids.length];

				for (i = 0; i < uids.length; ++i) {
					msgs[i] = (Message) this.uidTable.get(uids[i]);
				}

				return msgs;
			}
		} catch (ConnectionException var12) {
			throw new FolderClosedException(this, var12.getMessage());
		} catch (ProtocolException var13) {
			throw new MessagingException(var13.getMessage(), var13);
		}
	}

	public synchronized long getUID(Message message) throws MessagingException {
		if (message.getFolder() != this) {
			throw new NoSuchElementException("Message does not belong to this folder");
		} else {
			this.checkOpened();
			if (!(message instanceof IMAPMessage)) {
				throw new MessagingException("message is not an IMAPMessage");
			} else {
				IMAPMessage m = (IMAPMessage) message;
				long uid;
				if ((uid = m.getUID()) != -1L) {
					return uid;
				} else {
					synchronized (this.messageCacheLock) {
						try {
							IMAPProtocol p = this.getProtocol();
							m.checkExpunged();
							UID u = p.fetchUID(m.getSequenceNumber());
							if (u != null) {
								uid = u.uid;
								m.setUID(uid);
								if (this.uidTable == null) {
									this.uidTable = new Hashtable();
								}

								this.uidTable.put(uid, m);
							}
						} catch (ConnectionException var9) {
							throw new FolderClosedException(this, var9.getMessage());
						} catch (ProtocolException var10) {
							throw new MessagingException(var10.getMessage(), var10);
						}

						return uid;
					}
				}
			}
		}
	}

	public synchronized boolean getUIDNotSticky() throws MessagingException {
		this.checkOpened();
		return this.uidNotSticky;
	}

	private Message[] createMessagesForUIDs(long[] uids) {
		IMAPMessage[] msgs = new IMAPMessage[uids.length];

		for (int i = 0; i < uids.length; ++i) {
			IMAPMessage m = null;
			if (this.uidTable != null) {
				m = (IMAPMessage) this.uidTable.get(uids[i]);
			}

			if (m == null) {
				m = this.newIMAPMessage(-1);
				m.setUID(uids[i]);
				m.setExpunged(true);
			}

			msgs[i++] = m;
		}

		return msgs;
	}

	public synchronized long getHighestModSeq() throws MessagingException {
		if (this.opened) {
			return this.highestmodseq;
		} else {
			IMAPProtocol p = null;
			Status status = null;

			try {
				p = this.getStoreProtocol();
				if (!p.hasCapability("CONDSTORE")) {
					throw new BadCommandException("CONDSTORE not supported");
				}

				String[] item = new String[]{"HIGHESTMODSEQ"};
				status = p.status(this.fullName, item);
			} catch (BadCommandException var9) {
				throw new MessagingException("Cannot obtain HIGHESTMODSEQ", var9);
			} catch (ConnectionException var10) {
				this.throwClosedException(var10);
			} catch (ProtocolException var11) {
				throw new MessagingException(var11.getMessage(), var11);
			} finally {
				this.releaseStoreProtocol(p);
			}

			if (status == null) {
				throw new MessagingException("Cannot obtain HIGHESTMODSEQ");
			} else {
				return status.highestmodseq;
			}
		}
	}

	public synchronized Message[] getMessagesByUIDChangedSince(long start, long end, long modseq)
			throws MessagingException {
		this.checkOpened();

		try {
			synchronized (this.messageCacheLock) {
				IMAPProtocol p = this.getProtocol();
				if (!p.hasCapability("CONDSTORE")) {
					throw new BadCommandException("CONDSTORE not supported");
				} else {
					int[] nums = p.uidfetchChangedSince(start, end, modseq);
					return this.getMessagesBySeqNumbers(nums);
				}
			}
		} catch (ConnectionException var12) {
			throw new FolderClosedException(this, var12.getMessage());
		} catch (ProtocolException var13) {
			throw new MessagingException(var13.getMessage(), var13);
		}
	}

	public Quota[] getQuota() throws MessagingException {
		return (Quota[]) this.doOptionalCommand("QUOTA not supported", new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				return p.getQuotaRoot(IMAPFolder.this.fullName);
			}
		});
	}

	public void setQuota(final Quota quota) throws MessagingException {
		this.doOptionalCommand("QUOTA not supported", new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				p.setQuota(quota);
				return null;
			}
		});
	}

	public ACL[] getACL() throws MessagingException {
		return (ACL[]) this.doOptionalCommand("ACL not supported", new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				return p.getACL(IMAPFolder.this.fullName);
			}
		});
	}

	public void addACL(ACL acl) throws MessagingException {
		this.setACL(acl, ' ');
	}

	public void removeACL(final String name) throws MessagingException {
		this.doOptionalCommand("ACL not supported", new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				p.deleteACL(IMAPFolder.this.fullName, name);
				return null;
			}
		});
	}

	public void addRights(ACL acl) throws MessagingException {
		this.setACL(acl, '+');
	}

	public void removeRights(ACL acl) throws MessagingException {
		this.setACL(acl, '-');
	}

	public Rights[] listRights(final String name) throws MessagingException {
		return (Rights[]) this.doOptionalCommand("ACL not supported", new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				return p.listRights(IMAPFolder.this.fullName, name);
			}
		});
	}

	public Rights myRights() throws MessagingException {
		return (Rights) this.doOptionalCommand("ACL not supported", new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				return p.myRights(IMAPFolder.this.fullName);
			}
		});
	}

	private void setACL(final ACL acl, final char mod) throws MessagingException {
		this.doOptionalCommand("ACL not supported", new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				p.setACL(IMAPFolder.this.fullName, mod, acl);
				return null;
			}
		});
	}

	public synchronized String[] getAttributes() throws MessagingException {
		this.checkExists();
		if (this.attributes == null) {
			this.exists();
		}

		return this.attributes == null ? new String[0] : (String[]) this.attributes.clone();
	}

	public void idle() throws MessagingException {
		this.idle(false);
	}

	public void idle(boolean once) throws MessagingException {
		synchronized (this) {
			if (this.protocol != null && this.protocol.getChannel() != null) {
				throw new MessagingException("idle method not supported with SocketChannels");
			}
		}

		if (this.startIdle((IdleManager) null)) {
			while (this.handleIdle(once)) {
			}

			int minidle = ((IMAPStore) this.store).getMinIdleTime();
			if (minidle > 0) {
				try {
					Thread.sleep((long) minidle);
				} catch (InterruptedException var4) {
					Thread.currentThread().interrupt();
				}
			}

		}
	}

	boolean startIdle(final IdleManager im) throws MessagingException {
		assert !Thread.holdsLock(this);

		synchronized (this) {
			this.checkOpened();
			if (im != null && this.idleManager != null && im != this.idleManager) {
				throw new MessagingException("Folder already being watched by another IdleManager");
			} else {
				Boolean started = (Boolean) this.doOptionalCommand("IDLE not supported", new ProtocolCommand() {
					public Object doCommand(IMAPProtocol p) throws ProtocolException {
						if (IMAPFolder.this.idleState == 1 && im != null && im == IMAPFolder.this.idleManager) {
							return Boolean.TRUE;
						} else if (IMAPFolder.this.idleState == 0) {
							p.idleStart();
							IMAPFolder.this.logger.finest("startIdle: set to IDLE");
							IMAPFolder.this.idleState = 1;
							IMAPFolder.this.idleManager = im;
							return Boolean.TRUE;
						} else {
							try {
								IMAPFolder.this.messageCacheLock.wait();
							} catch (InterruptedException var3) {
								Thread.currentThread().interrupt();
							}

							return Boolean.FALSE;
						}
					}
				});
				this.logger.log(Level.FINEST, "startIdle: return {0}", started);
				return started;
			}
		}
	}

	boolean handleIdle(boolean once) throws MessagingException {
		Response r = null;

		do {
			r = this.protocol.readIdleResponse();

			try {
				synchronized (this.messageCacheLock) {
					if (r.isBYE() && r.isSynthetic() && this.idleState == 1) {
						Exception ex = r.getException();
						if (ex instanceof InterruptedIOException
								&& ((InterruptedIOException) ex).bytesTransferred == 0) {
							if (ex instanceof SocketTimeoutException) {
								this.logger.finest("handleIdle: ignoring socket timeout");
								r = null;
							} else {
								this.logger.finest("handleIdle: interrupting IDLE");
								IdleManager im = this.idleManager;
								if (im != null) {
									this.logger.finest("handleIdle: request IdleManager to abort");
									im.requestAbort(this);
								} else {
									this.logger.finest("handleIdle: abort IDLE");
									this.protocol.idleAbort();
									this.idleState = 2;
								}
							}
							continue;
						}
					}

					boolean done = true;

					label179 : {
						boolean var5;
						try {
							if (this.protocol != null && this.protocol.processIdleResponse(r)) {
								done = false;
								break label179;
							}

							var5 = false;
						} finally {
							if (done) {
								this.logger.finest("handleIdle: set to RUNNING");
								this.idleState = 0;
								this.idleManager = null;
								this.messageCacheLock.notifyAll();
							}

						}

						return var5;
					}

					if (once && this.idleState == 1) {
						try {
							this.protocol.idleAbort();
						} catch (Exception var13) {
						}

						this.idleState = 2;
					}
				}
			} catch (ConnectionException var16) {
				throw new FolderClosedException(this, var16.getMessage());
			} catch (ProtocolException var17) {
				throw new MessagingException(var17.getMessage(), var17);
			}
		} while (r == null || this.protocol.hasResponse());

		return true;
	}

	void waitIfIdle() throws ProtocolException {
		assert Thread.holdsLock(this.messageCacheLock);

		while (this.idleState != 0) {
			if (this.idleState == 1) {
				IdleManager im = this.idleManager;
				if (im != null) {
					this.logger.finest("waitIfIdle: request IdleManager to abort");
					im.requestAbort(this);
				} else {
					this.logger.finest("waitIfIdle: abort IDLE");
					this.protocol.idleAbort();
					this.idleState = 2;
				}
			} else {
				this.logger.log(Level.FINEST, "waitIfIdle: idleState {0}", this.idleState);
			}

			try {
				if (this.logger.isLoggable(Level.FINEST)) {
					this.logger.finest("waitIfIdle: wait to be not idle: " + Thread.currentThread());
				}

				this.messageCacheLock.wait();
				if (this.logger.isLoggable(Level.FINEST)) {
					this.logger.finest(
							"waitIfIdle: wait done, idleState " + this.idleState + ": " + Thread.currentThread());
				}
			} catch (InterruptedException var2) {
				Thread.currentThread().interrupt();
				throw new ProtocolException("Interrupted waitIfIdle", var2);
			}
		}

	}

	void idleAbort() {
		synchronized (this.messageCacheLock) {
			if (this.idleState == 1 && this.protocol != null) {
				this.protocol.idleAbort();
				this.idleState = 2;
			}

		}
	}

	void idleAbortWait() {
		synchronized (this.messageCacheLock) {
			if (this.idleState == 1 && this.protocol != null) {
				this.protocol.idleAbort();
				this.idleState = 2;

				try {
					while (this.handleIdle(false)) {
					}
				} catch (Exception var4) {
					this.logger.log(Level.FINEST, "Exception in idleAbortWait", var4);
				}

				this.logger.finest("IDLE aborted");
			}

		}
	}

	SocketChannel getChannel() {
		return this.protocol != null ? this.protocol.getChannel() : null;
	}

	public Map<String, String> id(final Map<String, String> clientParams) throws MessagingException {
		this.checkOpened();
		return (Map) this.doOptionalCommand("ID not supported", new ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				return p.id(clientParams);
			}
		});
	}

	public synchronized long getStatusItem(String item) throws MessagingException {
		if (!this.opened) {
			this.checkExists();
			IMAPProtocol p = null;
			Status status = null;

			long var5;
			try {
				p = this.getStoreProtocol();
				String[] items = new String[]{item};
				status = p.status(this.fullName, items);
				var5 = status != null ? status.getItem(item) : -1L;
				return var5;
			} catch (BadCommandException var12) {
				var5 = -1L;
			} catch (ConnectionException var13) {
				throw new StoreClosedException(this.store, var13.getMessage());
			} catch (ProtocolException var14) {
				throw new MessagingException(var14.getMessage(), var14);
			} finally {
				this.releaseStoreProtocol(p);
			}

			return var5;
		} else {
			return -1L;
		}
	}

	public void handleResponse(Response r) {
		assert Thread.holdsLock(this.messageCacheLock);

		if (r.isOK() || r.isNO() || r.isBAD() || r.isBYE()) {
			((IMAPStore) this.store).handleResponseCode(r);
		}

		if (r.isBYE()) {
			if (this.opened) {
				this.cleanup(false);
			}

		} else if (r.isOK()) {
			r.skipSpaces();
			if (r.readByte() == 91) {
				String s = r.readAtom();
				if (s.equalsIgnoreCase("HIGHESTMODSEQ")) {
					this.highestmodseq = r.readLong();
				}
			}

			r.reset();
		} else if (r.isUnTagged()) {
			if (!(r instanceof IMAPResponse)) {
				this.logger.fine("UNEXPECTED RESPONSE : " + r.toString());
			} else {
				IMAPResponse ir = (IMAPResponse) r;
				int seqnum;
				if (ir.keyEquals("EXISTS")) {
					seqnum = ir.getNumber();
					if (seqnum <= this.realTotal) {
						return;
					}

					int count = seqnum - this.realTotal;
					Message[] msgs = new Message[count];
					this.messageCache.addMessages(count, this.realTotal + 1);
					int oldtotal = this.total;
					this.realTotal += count;
					this.total += count;
					if (this.hasMessageCountListener) {
						for (int i = 0; i < count; ++i) {
							++oldtotal;
							msgs[i] = this.messageCache.getMessage(oldtotal);
						}

						this.notifyMessageAddedListeners(msgs);
					}
				} else if (ir.keyEquals("EXPUNGE")) {
					seqnum = ir.getNumber();
					if (seqnum > this.realTotal) {
						return;
					}

					Message[] msgs = null;
					if (this.doExpungeNotification && this.hasMessageCountListener) {
						msgs = new Message[]{this.getMessageBySeqNumber(seqnum)};
						if (msgs[0] == null) {
							msgs = null;
						}
					}

					this.messageCache.expungeMessage(seqnum);
					--this.realTotal;
					if (msgs != null) {
						this.notifyMessageRemovedListeners(false, msgs);
					}
				} else if (ir.keyEquals("VANISHED")) {
					String[] s = ir.readAtomStringList();
					if (s == null) {
						String uids = ir.readAtom();
						UIDSet[] uidset = UIDSet.parseUIDSets(uids);
						this.realTotal = (int) ((long) this.realTotal - UIDSet.size(uidset));
						long[] luid = UIDSet.toArray(uidset);
						Message[] msgs = this.createMessagesForUIDs(luid);
						Message[] var8 = msgs;
						int var9 = msgs.length;

						for (int var10 = 0; var10 < var9; ++var10) {
							Message m = var8[var10];
							if (m.getMessageNumber() > 0) {
								this.messageCache.expungeMessage(m.getMessageNumber());
							}
						}

						if (this.doExpungeNotification && this.hasMessageCountListener) {
							this.notifyMessageRemovedListeners(true, msgs);
						}
					}
				} else if (ir.keyEquals("FETCH")) {
					assert ir instanceof FetchResponse : "!ir instanceof FetchResponse";

					Message msg = this.processFetchResponse((FetchResponse) ir);
					if (msg != null) {
						this.notifyMessageChangedListeners(1, msg);
					}
				} else if (ir.keyEquals("RECENT")) {
					this.recent = ir.getNumber();
				}

			}
		}
	}

	private Message processFetchResponse(FetchResponse fr) {
		IMAPMessage msg = this.getMessageBySeqNumber(fr.getNumber());
		if (msg != null) {
			boolean notify = false;
			UID uid = (UID) fr.getItem(UID.class);
			if (uid != null && msg.getUID() != uid.uid) {
				msg.setUID(uid.uid);
				if (this.uidTable == null) {
					this.uidTable = new Hashtable();
				}

				this.uidTable.put(uid.uid, msg);
				notify = true;
			}

			MODSEQ modseq = (MODSEQ) fr.getItem(MODSEQ.class);
			if (modseq != null && msg._getModSeq() != modseq.modseq) {
				msg.setModSeq(modseq.modseq);
				notify = true;
			}

			FLAGS flags = (FLAGS) fr.getItem(FLAGS.class);
			if (flags != null) {
				msg._setFlags(flags);
				notify = true;
			}

			msg.handleExtensionFetchItems(fr.getExtensionItems());
			if (!notify) {
				msg = null;
			}
		}

		return msg;
	}

	void handleResponses(Response[] r) {
		for (int i = 0; i < r.length; ++i) {
			if (r[i] != null) {
				this.handleResponse(r[i]);
			}
		}

	}

	protected synchronized IMAPProtocol getStoreProtocol() throws ProtocolException {
		this.connectionPoolLogger.fine("getStoreProtocol() borrowing a connection");
		return ((IMAPStore) this.store).getFolderStoreProtocol();
	}

	protected synchronized void throwClosedException(ConnectionException cex)
			throws FolderClosedException, StoreClosedException {
		if ((this.protocol == null || cex.getProtocol() != this.protocol)
				&& (this.protocol != null || this.reallyClosed)) {
			throw new StoreClosedException(this.store, cex.getMessage());
		} else {
			throw new FolderClosedException(this, cex.getMessage());
		}
	}

	protected IMAPProtocol getProtocol() throws ProtocolException {
		assert Thread.holdsLock(this.messageCacheLock);

		this.waitIfIdle();
		if (this.protocol == null) {
			throw new ConnectionException("Connection closed");
		} else {
			return this.protocol;
		}
	}

	public Object doCommand(ProtocolCommand cmd) throws MessagingException {
		try {
			return this.doProtocolCommand(cmd);
		} catch (ConnectionException var3) {
			this.throwClosedException(var3);
			return null;
		} catch (ProtocolException var4) {
			throw new MessagingException(var4.getMessage(), var4);
		}
	}

	public Object doOptionalCommand(String err, ProtocolCommand cmd) throws MessagingException {
		try {
			return this.doProtocolCommand(cmd);
		} catch (BadCommandException var4) {
			throw new MessagingException(err, var4);
		} catch (ConnectionException var5) {
			this.throwClosedException(var5);
			return null;
		} catch (ProtocolException var6) {
			throw new MessagingException(var6.getMessage(), var6);
		}
	}

	public Object doCommandIgnoreFailure(ProtocolCommand cmd) throws MessagingException {
		try {
			return this.doProtocolCommand(cmd);
		} catch (CommandFailedException var3) {
			return null;
		} catch (ConnectionException var4) {
			this.throwClosedException(var4);
			return null;
		} catch (ProtocolException var5) {
			throw new MessagingException(var5.getMessage(), var5);
		}
	}

	protected synchronized Object doProtocolCommand(ProtocolCommand cmd) throws ProtocolException {
		if (this.protocol != null) {
			synchronized (this.messageCacheLock) {
				return cmd.doCommand(this.getProtocol());
			}
		} else {
			IMAPProtocol p = null;

			Object var3;
			try {
				p = this.getStoreProtocol();
				var3 = cmd.doCommand(p);
			} finally {
				this.releaseStoreProtocol(p);
			}

			return var3;
		}
	}

	protected synchronized void releaseStoreProtocol(IMAPProtocol p) {
		if (p != this.protocol) {
			((IMAPStore) this.store).releaseFolderStoreProtocol(p);
		} else {
			this.logger.fine("releasing our protocol as store protocol?");
		}

	}

	protected void releaseProtocol(boolean returnToPool) {
		if (this.protocol != null) {
			this.protocol.removeResponseHandler(this);
			if (returnToPool) {
				((IMAPStore) this.store).releaseProtocol(this, this.protocol);
			} else {
				this.protocol.disconnect();
				((IMAPStore) this.store).releaseProtocol(this, (IMAPProtocol) null);
			}

			this.protocol = null;
		}

	}

	protected void keepConnectionAlive(boolean keepStoreAlive) throws ProtocolException {
		assert Thread.holdsLock(this.messageCacheLock);

		if (this.protocol != null) {
			if (System.currentTimeMillis() - this.protocol.getTimestamp() > 1000L) {
				this.waitIfIdle();
				if (this.protocol != null) {
					this.protocol.noop();
				}
			}

			if (keepStoreAlive && ((IMAPStore) this.store).hasSeparateStoreConnection()) {
				IMAPProtocol p = null;

				try {
					p = ((IMAPStore) this.store).getFolderStoreProtocol();
					if (System.currentTimeMillis() - p.getTimestamp() > 1000L) {
						p.noop();
					}
				} finally {
					((IMAPStore) this.store).releaseFolderStoreProtocol(p);
				}
			}

		}
	}

	protected IMAPMessage getMessageBySeqNumber(int seqnum) {
		if (seqnum > this.messageCache.size()) {
			if (this.logger.isLoggable(Level.FINE)) {
				this.logger.fine("ignoring message number " + seqnum + " outside range " + this.messageCache.size());
			}

			return null;
		} else {
			return this.messageCache.getMessageBySeqnum(seqnum);
		}
	}

	protected IMAPMessage[] getMessagesBySeqNumbers(int[] seqnums) {
		IMAPMessage[] msgs = new IMAPMessage[seqnums.length];
		int nulls = 0;

		for (int i = 0; i < seqnums.length; ++i) {
			msgs[i] = this.getMessageBySeqNumber(seqnums[i]);
			if (msgs[i] == null) {
				++nulls;
			}
		}

		if (nulls > 0) {
			IMAPMessage[] nmsgs = new IMAPMessage[seqnums.length - nulls];
			int i = 0;

			for (int j = 0; i < msgs.length; ++i) {
				if (msgs[i] != null) {
					nmsgs[j++] = msgs[i];
				}
			}

			msgs = nmsgs;
		}

		return msgs;
	}

	private boolean isDirectory() {
		return (this.type & 2) != 0;
	}

	public interface ProtocolCommand {
		Object doCommand(IMAPProtocol var1) throws ProtocolException;
	}

	public static class FetchProfileItem extends FetchProfile.Item {
		public static final FetchProfileItem HEADERS = new FetchProfileItem("HEADERS");

		@Deprecated
		public static final FetchProfileItem SIZE = new FetchProfileItem("SIZE");
		public static final FetchProfileItem MESSAGE = new FetchProfileItem("MESSAGE");
		public static final FetchProfileItem INTERNALDATE = new FetchProfileItem("INTERNALDATE");

		protected FetchProfileItem(String name) {
			super(name);
		}
	}
}