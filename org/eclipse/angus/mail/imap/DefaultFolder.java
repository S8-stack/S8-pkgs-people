package org.eclipse.angus.mail.imap;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.MethodNotSupportedException;
import org.eclipse.angus.mail.iap.ProtocolException;
import org.eclipse.angus.mail.imap.protocol.IMAPProtocol;
import org.eclipse.angus.mail.imap.protocol.ListInfo;

public class DefaultFolder extends IMAPFolder {
	protected DefaultFolder(IMAPStore store) {
		super("", '￿', store, (Boolean) null);
		this.exists = true;
		this.type = 2;
	}

	public synchronized String getName() {
		return this.fullName;
	}

	public Folder getParent() {
		return null;
	}

	public synchronized Folder[] list(final String pattern) throws MessagingException {
		ListInfo[] li = null;
		li = (ListInfo[]) this.doCommand(new IMAPFolder.ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				return p.list("", pattern);
			}
		});
		if (li == null) {
			return new Folder[0];
		} else {
			IMAPFolder[] folders = new IMAPFolder[li.length];

			for (int i = 0; i < folders.length; ++i) {
				folders[i] = ((IMAPStore) this.store).newIMAPFolder(li[i]);
			}

			return folders;
		}
	}

	public synchronized Folder[] listSubscribed(final String pattern) throws MessagingException {
		ListInfo[] li = null;
		li = (ListInfo[]) this.doCommand(new IMAPFolder.ProtocolCommand() {
			public Object doCommand(IMAPProtocol p) throws ProtocolException {
				return p.lsub("", pattern);
			}
		});
		if (li == null) {
			return new Folder[0];
		} else {
			IMAPFolder[] folders = new IMAPFolder[li.length];

			for (int i = 0; i < folders.length; ++i) {
				folders[i] = ((IMAPStore) this.store).newIMAPFolder(li[i]);
			}

			return folders;
		}
	}

	public boolean hasNewMessages() throws MessagingException {
		return false;
	}

	public Folder getFolder(String name) throws MessagingException {
		return ((IMAPStore) this.store).newIMAPFolder(name, '￿');
	}

	public boolean delete(boolean recurse) throws MessagingException {
		throw new MethodNotSupportedException("Cannot delete Default Folder");
	}

	public boolean renameTo(Folder f) throws MessagingException {
		throw new MethodNotSupportedException("Cannot rename Default Folder");
	}

	public void appendMessages(Message[] msgs) throws MessagingException {
		throw new MethodNotSupportedException("Cannot append to Default Folder");
	}

	public Message[] expunge() throws MessagingException {
		throw new MethodNotSupportedException("Cannot expunge Default Folder");
	}
}