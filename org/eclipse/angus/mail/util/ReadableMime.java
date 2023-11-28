package org.eclipse.angus.mail.util;

import jakarta.mail.MessagingException;
import java.io.InputStream;

public interface ReadableMime {
	InputStream getMimeStream() throws MessagingException;
}