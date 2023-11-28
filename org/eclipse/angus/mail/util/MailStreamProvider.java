package org.eclipse.angus.mail.util;

import jakarta.mail.util.SharedByteArrayInputStream;
import jakarta.mail.util.StreamProvider;
import java.io.InputStream;
import java.io.OutputStream;

public class MailStreamProvider implements StreamProvider {
	public InputStream inputBase64(InputStream in) {
		return new BASE64DecoderStream(in);
	}

	public OutputStream outputBase64(OutputStream out) {
		return new BASE64EncoderStream(out);
	}

	public InputStream inputBinary(InputStream in) {
		return in;
	}

	public OutputStream outputBinary(OutputStream out) {
		return out;
	}

	public OutputStream outputB(OutputStream out) {
		return new BEncoderStream(out);
	}

	public InputStream inputQ(InputStream in) {
		return new QDecoderStream(in);
	}

	public OutputStream outputQ(OutputStream out, boolean encodingWord) {
		return new QEncoderStream(out, encodingWord);
	}

	public LineInputStream inputLineStream(InputStream in, boolean allowutf8) {
		return new LineInputStream(in, allowutf8);
	}

	public LineOutputStream outputLineStream(OutputStream out, boolean allowutf8) {
		return new LineOutputStream(out, allowutf8);
	}

	public InputStream inputQP(InputStream in) {
		return new QPDecoderStream(in);
	}

	public OutputStream outputQP(OutputStream out) {
		return new QPEncoderStream(out);
	}

	public InputStream inputSharedByteArray(byte[] bytes) {
		return new SharedByteArrayInputStream(bytes);
	}

	public InputStream inputUU(InputStream in) {
		return new UUDecoderStream(in);
	}

	public OutputStream outputUU(OutputStream out, String filename) {
		return filename == null ? new UUEncoderStream(out) : new UUEncoderStream(out, filename);
	}
}