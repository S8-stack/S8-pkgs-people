package org.eclipse.angus.mail.util;

import java.io.IOException;
import java.io.OutputStream;

public class QEncoderStream extends QPEncoderStream {
	private String specials;
	private static final String WORD_SPECIALS = "=_?\"#$%&'(),.:;<>@[\\]^`{|}~";
	private static final String TEXT_SPECIALS = "=_?";

	public QEncoderStream(OutputStream out, boolean encodingWord) {
		super(out, Integer.MAX_VALUE);
		this.specials = encodingWord ? "=_?\"#$%&'(),.:;<>@[\\]^`{|}~" : "=_?";
	}

	public void write(int c) throws IOException {
		c &= 255;
		if (c == 32) {
			this.output(95, false);
		} else if (c >= 32 && c < 127 && this.specials.indexOf(c) < 0) {
			this.output(c, false);
		} else {
			this.output(c, true);
		}

	}
}