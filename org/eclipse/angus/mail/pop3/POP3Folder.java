package org.eclipse.angus.mail.pop3;

import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.FolderNotFoundException;
import jakarta.mail.Message;
import jakarta.mail.MessageRemovedException;
import jakarta.mail.MessagingException;
import jakarta.mail.MethodNotSupportedException;
import jakarta.mail.FetchProfile.Item;
import jakarta.mail.Flags.Flag;
import jakarta.mail.UIDFolder.FetchProfileItem;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.StringTokenizer;
import java.util.logging.Level;
import org.eclipse.angus.mail.util.LineInputStream;
import org.eclipse.angus.mail.util.MailLogger;

public class POP3Folder extends Folder {
	private String name;
	private POP3Store store;
	private volatile Protocol port;
	private int total;
	private int size;
	private boolean exists = false;
	private volatile boolean opened = false;
	private POP3Message[] message_cache;
	private boolean doneUidl = false;
	private volatile TempFile fileCache = null;
	private boolean forceClose;
	MailLogger logger;

	protected POP3Folder(POP3Store store, String name) {
		super(store);
		this.name = name;
		this.store = store;
		if (name.equalsIgnoreCase("INBOX")) {
			this.exists = true;
		}

		this.logger = new MailLogger(this.getClass(), "DEBUG POP3", store.getSession().getDebug(),
				store.getSession().getDebugOut());
	}

	public String getName() {
		return this.name;
	}

	public String getFullName() {
		return this.name;
	}

	public Folder getParent() {
		return new DefaultFolder(this.store);
	}

	public boolean exists() {
		return this.exists;
	}

	public Folder[] list(String pattern) throws MessagingException {
		throw new MessagingException("not a directory");
	}

	public char getSeparator() {
		return ' ';
	}

	public int getType() {
		return 1;
	}

	public boolean create(int type) throws MessagingException {
		return false;
	}

	public boolean hasNewMessages() throws MessagingException {
		return false;
	}

	public Folder getFolder(String name) throws MessagingException {
		throw new MessagingException("not a directory");
	}

	public boolean delete(boolean recurse) throws MessagingException {
		throw new MethodNotSupportedException("delete");
	}

	public boolean renameTo(Folder f) throws MessagingException {
		throw new MethodNotSupportedException("renameTo");
	}

	public synchronized void open(int mode) throws MessagingException {
		this.checkClosed();
		if (!this.exists) {
			throw new FolderNotFoundException(this, "folder is not INBOX");
		} else {
			try {
				this.port = this.store.getPort(this);
				Status s = this.port.stat();
				this.total = s.total;
				this.size = s.size;
				this.mode = mode;
				if (this.store.useFileCache) {
					try {
						this.fileCache = new TempFile(this.store.fileCacheDir);
					} catch (IOException var11) {
						this.logger.log(Level.FINE, "failed to create file cache", var11);
						throw var11;
					}
				}

				this.opened = true;
			} catch (IOException var12) {
				try {
					if (this.port != null) {
						this.port.quit();
					}
				} catch (IOException var9) {
				} finally {
					this.port = null;
					this.store.closePort(this);
				}

				throw new MessagingException("Open failed", var12);
			}

			this.message_cache = new POP3Message[this.total];
			this.doneUidl = false;
			this.notifyConnectionListeners(1);
		}
	}

	public synchronized void close(boolean expunge) throws MessagingException {
		this.checkOpen();

		try {
			if (this.store.rsetBeforeQuit && !this.forceClose) {
				this.port.rset();
			}

			POP3Message m;
			int i;
			if (expunge && this.mode == 2 && !this.forceClose) {
				for (i = 0; i < this.message_cache.length; ++i) {
					if ((m = this.message_cache[i]) != null && m.isSet(Flag.DELETED)) {
						try {
							this.port.dele(i + 1);
						} catch (IOException var9) {
							throw new MessagingException("Exception deleting messages during close", var9);
						}
					}
				}
			}

			for (i = 0; i < this.message_cache.length; ++i) {
				if ((m = this.message_cache[i]) != null) {
					m.invalidate(true);
				}
			}

			if (this.forceClose) {
				this.port.close();
			} else {
				this.port.quit();
			}
		} catch (IOException var10) {
		} finally {
			this.port = null;
			this.store.closePort(this);
			this.message_cache = null;
			this.opened = false;
			this.notifyConnectionListeners(3);
			if (this.fileCache != null) {
				this.fileCache.close();
				this.fileCache = null;
			}

		}

	}

	public synchronized boolean isOpen() {
		if (!this.opened) {
			return false;
		} else {
			try {
				if (!this.port.noop()) {
					throw new IOException("NOOP failed");
				} else {
					return true;
				}
			} catch (IOException var4) {
				try {
					this.close(false);
				} catch (MessagingException var3) {
				}

				return false;
			}
		}
	}

	public Flags getPermanentFlags() {
		return new Flags();
	}

	public synchronized int getMessageCount() throws MessagingException {
		if (!this.opened) {
			return -1;
		} else {
			this.checkReadable();
			return this.total;
		}
	}

	public synchronized Message getMessage(int msgno) throws MessagingException {
		this.checkOpen();
		POP3Message m;
		if ((m = this.message_cache[msgno - 1]) == null) {
			m = this.createMessage(this, msgno);
			this.message_cache[msgno - 1] = m;
		}

		return m;
	}

	protected POP3Message createMessage(Folder f, int msgno) throws MessagingException {
		POP3Message m = null;
		Constructor<?> cons = this.store.messageConstructor;
		if (cons != null) {
			try {
				Object[] o = new Object[]{this, msgno};
				m = (POP3Message) cons.newInstance(o);
			} catch (Exception var6) {
			}
		}

		if (m == null) {
			m = new POP3Message(this, msgno);
		}

		return m;
	}

	public void appendMessages(Message[] msgs) throws MessagingException {
		throw new MethodNotSupportedException("Append not supported");
	}

	public Message[] expunge() throws MessagingException {
		throw new MethodNotSupportedException("Expunge not supported");
	}

	public synchronized void fetch(Message[] msgs, FetchProfile fp) throws MessagingException {
		this.checkReadable();
		if (!this.doneUidl && this.store.supportsUidl && fp.contains(FetchProfileItem.UID)) {
			String[] uids = new String[this.message_cache.length];

			try {
				if (!this.port.uidl(uids)) {
					return;
				}
			} catch (EOFException var7) {
				this.close(false);
				throw new FolderClosedException(this, var7.toString());
			} catch (IOException var8) {
				throw new MessagingException("error getting UIDL", var8);
			}

			for (int i = 0; i < uids.length; ++i) {
				if (uids[i] != null) {
					POP3Message m = (POP3Message) this.getMessage(i + 1);
					m.uid = uids[i];
				}
			}

			this.doneUidl = true;
		}

		if (fp.contains(Item.ENVELOPE)) {
			for (int i = 0; i < msgs.length; ++i) {
				try {
					POP3Message msg = (POP3Message) msgs[i];
					msg.getHeader("");
					msg.getSize();
				} catch (MessageRemovedException var6) {
				}
			}
		}

	}

	public synchronized String getUID(Message msg) throws MessagingException {
		this.checkOpen();
		if (!(msg instanceof POP3Message)) {
			throw new MessagingException("message is not a POP3Message");
		} else {
			POP3Message m = (POP3Message) msg;

			try {
				if (!this.store.supportsUidl) {
					return null;
				} else {
					if (m.uid == "UNKNOWN") {
						m.uid = this.port.uidl(m.getMessageNumber());
					}

					return m.uid;
				}
			} catch (EOFException var4) {
				this.close(false);
				throw new FolderClosedException(this, var4.toString());
			} catch (IOException var5) {
				throw new MessagingException("error getting UIDL", var5);
			}
		}
	}

	public synchronized int getSize() throws MessagingException {
		this.checkOpen();
		return this.size;
	}

	public synchronized int[] getSizes() throws MessagingException {
		this.checkOpen();
		int[] sizes = new int[this.total];
		InputStream is = null;
		LineInputStream lis = null;

		try {
			is = this.port.list();
			lis = new LineInputStream(is);

			String line;
			while ((line = lis.readLine()) != null) {
				try {
					StringTokenizer st = new StringTokenizer(line);
					int msgnum = Integer.parseInt(st.nextToken());
					int size = Integer.parseInt(st.nextToken());
					if (msgnum > 0 && msgnum <= this.total) {
						sizes[msgnum - 1] = size;
					}
				} catch (RuntimeException var21) {
				}
			}
		} catch (IOException var22) {
		} finally {
			try {
				if (lis != null) {
					lis.close();
				}
			} catch (IOException var20) {
			}

			try {
				if (is != null) {
					is.close();
				}
			} catch (IOException var19) {
			}

		}

		return sizes;
	}

	public synchronized InputStream listCommand() throws MessagingException, IOException {
		this.checkOpen();
		return this.port.list();
	}

	protected void finalize() throws Throwable {
		this.forceClose = !this.store.finalizeCleanClose;

		try {
			if (this.opened) {
				this.close(false);
			}
		} finally {
			super.finalize();
			this.forceClose = false;
		}

	}

	private void checkOpen() throws IllegalStateException {
		if (!this.opened) {
			throw new IllegalStateException("Folder is not Open");
		}
	}

	private void checkClosed() throws IllegalStateException {
		if (this.opened) {
			throw new IllegalStateException("Folder is Open");
		}
	}

	private void checkReadable() throws IllegalStateException {
		if (!this.opened || this.mode != 1 && this.mode != 2) {
			throw new IllegalStateException("Folder is not Readable");
		}
	}

	Protocol getProtocol() throws MessagingException {
		Protocol p = this.port;
		this.checkOpen();
		return p;
	}

	protected void notifyMessageChangedListeners(int type, Message m) {
		super.notifyMessageChangedListeners(type, m);
	}

	TempFile getFileCache() {
		return this.fileCache;
	}
}