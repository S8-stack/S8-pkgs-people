package org.eclipse.angus.mail.imap;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.eclipse.angus.mail.util.MailLogger;

public class IdleManager {
	private Executor es;
	private Selector selector;
	private MailLogger logger;
	private volatile boolean die = false;
	private volatile boolean running;
	private Queue<IMAPFolder> toWatch = new ConcurrentLinkedQueue();
	private Queue<IMAPFolder> toAbort = new ConcurrentLinkedQueue();

	public IdleManager(Session session, Executor es) throws IOException {
		this.es = es;
		this.logger = new MailLogger(this.getClass(), "DEBUG IMAP", session.getDebug(), session.getDebugOut());
		this.selector = Selector.open();
		es.execute(new Runnable() {
			public void run() {
				IdleManager.this.logger.fine("IdleManager select starting");

				try {
					IdleManager.this.running = true;
					IdleManager.this.select();
				} finally {
					IdleManager.this.running = false;
					IdleManager.this.logger.fine("IdleManager select terminating");
				}

			}
		});
	}

	public boolean isRunning() {
		return this.running;
	}

	public void watch(Folder folder) throws MessagingException {
		if (this.die) {
			throw new MessagingException("IdleManager is not running");
		} else if (!(folder instanceof IMAPFolder)) {
			throw new MessagingException("Can only watch IMAP folders");
		} else {
			IMAPFolder ifolder = (IMAPFolder) folder;
			SocketChannel sc = ifolder.getChannel();
			if (sc == null) {
				if (folder.isOpen()) {
					throw new MessagingException("Folder is not using SocketChannels");
				} else {
					throw new MessagingException("Folder is not open");
				}
			} else {
				if (this.logger.isLoggable(Level.FINEST)) {
					this.logger.log(Level.FINEST, "IdleManager watching {0}", folderName(ifolder));
				}

				int tries;
				for (tries = 0; !ifolder.startIdle(this); ++tries) {
					if (this.logger.isLoggable(Level.FINEST)) {
						this.logger.log(Level.FINEST, "IdleManager.watch startIdle failed for {0}",
								folderName(ifolder));
					}
				}

				if (this.logger.isLoggable(Level.FINEST)) {
					if (tries > 0) {
						this.logger.log(Level.FINEST,
								"IdleManager.watch startIdle succeeded for {0} after " + tries + " tries",
								folderName(ifolder));
					} else {
						this.logger.log(Level.FINEST, "IdleManager.watch startIdle succeeded for {0}",
								folderName(ifolder));
					}
				}

				synchronized (this) {
					this.toWatch.add(ifolder);
					this.selector.wakeup();
				}
			}
		}
	}

	void requestAbort(IMAPFolder folder) {
		this.toAbort.add(folder);
		this.selector.wakeup();
	}

	private void select() {
		this.die = false;

		try {
			while (!this.die) {
				this.watchAll();
				this.logger.finest("IdleManager waiting...");
				int ns = this.selector.select();
				if (this.logger.isLoggable(Level.FINEST)) {
					this.logger.log(Level.FINEST, "IdleManager selected {0} channels", ns);
				}

				if (this.die || Thread.currentThread().isInterrupted()) {
					break;
				}

				while (true) {
					do {
						this.processKeys();
					} while (this.selector.selectNow() > 0);

					if (this.toAbort.isEmpty()) {
						break;
					}
				}
			}
		} catch (InterruptedIOException var14) {
			this.logger.log(Level.FINEST, "IdleManager interrupted", var14);
		} catch (IOException var15) {
			this.logger.log(Level.FINEST, "IdleManager got I/O exception", var15);
		} catch (Exception var16) {
			this.logger.log(Level.FINEST, "IdleManager got exception", var16);
		} finally {
			this.die = true;
			this.logger.finest("IdleManager unwatchAll");

			try {
				this.unwatchAll();
				this.selector.close();
			} catch (IOException var13) {
				this.logger.log(Level.FINEST, "IdleManager unwatch exception", var13);
			}

			this.logger.fine("IdleManager exiting");
		}

	}

	private void watchAll() {
		IMAPFolder folder;
		while ((folder = (IMAPFolder) this.toWatch.poll()) != null) {
			if (this.logger.isLoggable(Level.FINEST)) {
				this.logger.log(Level.FINEST, "IdleManager adding {0} to selector", folderName(folder));
			}

			try {
				SocketChannel sc = folder.getChannel();
				if (sc != null) {
					sc.configureBlocking(false);
					sc.register(this.selector, 1, folder);
				}
			} catch (IOException var3) {
				this.logger.log(Level.FINEST, "IdleManager can't register folder", var3);
			} catch (CancelledKeyException var4) {
				this.logger.log(Level.FINEST, "IdleManager can't register folder", var4);
			}
		}

	}

	private void processKeys() throws IOException {
		Set<SelectionKey> selectedKeys = this.selector.selectedKeys();
		Iterator<SelectionKey> it = selectedKeys.iterator();

		final IMAPFolder folder;
		while (it.hasNext()) {
			SelectionKey sk = (SelectionKey) it.next();
			it.remove();
			sk.cancel();
			folder = (IMAPFolder) sk.attachment();
			if (this.logger.isLoggable(Level.FINEST)) {
				this.logger.log(Level.FINEST, "IdleManager selected folder: {0}", folderName(folder));
			}

			SelectableChannel sc = sk.channel();
			sc.configureBlocking(true);

			try {
				if (folder.handleIdle(false)) {
					if (this.logger.isLoggable(Level.FINEST)) {
						this.logger.log(Level.FINEST, "IdleManager continue watching folder {0}", folderName(folder));
					}

					this.toWatch.add(folder);
				} else if (this.logger.isLoggable(Level.FINEST)) {
					this.logger.log(Level.FINEST, "IdleManager done watching folder {0}", folderName(folder));
				}
			} catch (MessagingException var8) {
				this.logger.log(Level.FINEST, "IdleManager got exception for folder: " + folderName(folder), var8);
			}
		}

		while (true) {
			while (true) {
				SocketChannel sc;
				do {
					if ((folder = (IMAPFolder) this.toAbort.poll()) == null) {
						return;
					}

					if (this.logger.isLoggable(Level.FINEST)) {
						this.logger.log(Level.FINEST, "IdleManager aborting IDLE for folder: {0}", folderName(folder));
					}

					sc = folder.getChannel();
				} while (sc == null);

				SelectionKey sk = sc.keyFor(this.selector);
				if (sk != null) {
					sk.cancel();
				}

				sc.configureBlocking(true);
				Socket sock = sc.socket();
				if (sock != null && sock.getSoTimeout() > 0) {
					this.logger.finest("IdleManager requesting DONE with timeout");
					this.toWatch.remove(folder);
					this.es.execute(new Runnable() {
						public void run() {
							folder.idleAbortWait();
						}
					});
				} else {
					folder.idleAbort();
					this.toWatch.add(folder);
				}
			}
		}
	}

	private void unwatchAll() {
		Set<SelectionKey> keys = this.selector.keys();
		Iterator var3 = keys.iterator();

		IMAPFolder folder;
		while (var3.hasNext()) {
			SelectionKey sk = (SelectionKey) var3.next();
			sk.cancel();
			folder = (IMAPFolder) sk.attachment();
			if (this.logger.isLoggable(Level.FINEST)) {
				this.logger.log(Level.FINEST, "IdleManager no longer watching folder: {0}", folderName(folder));
			}

			SelectableChannel sc = sk.channel();

			try {
				sc.configureBlocking(true);
				folder.idleAbortWait();
			} catch (IOException var8) {
				this.logger.log(Level.FINEST,
						"IdleManager exception while aborting idle for folder: " + folderName(folder), var8);
			}
		}

		while ((folder = (IMAPFolder) this.toWatch.poll()) != null) {
			if (this.logger.isLoggable(Level.FINEST)) {
				this.logger.log(Level.FINEST, "IdleManager aborting IDLE for unwatched folder: {0}",
						folderName(folder));
			}

			SocketChannel sc = folder.getChannel();
			if (sc != null) {
				try {
					sc.configureBlocking(true);
					folder.idleAbortWait();
				} catch (IOException var7) {
					this.logger.log(Level.FINEST,
							"IdleManager exception while aborting idle for folder: " + folderName(folder), var7);
				}
			}
		}

	}

	public synchronized void stop() {
		this.die = true;
		this.logger.fine("IdleManager stopping");
		this.selector.wakeup();
	}

	private static String folderName(Folder folder) {
		try {
			return folder.getURLName().toString();
		} catch (MessagingException var2) {
			return folder.getStore().toString() + "/" + folder.toString();
		}
	}
}