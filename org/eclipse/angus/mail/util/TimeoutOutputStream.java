package org.eclipse.angus.mail.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class TimeoutOutputStream extends OutputStream {
	private static final String WRITE_TIMEOUT_MESSAGE = "Write timed out";
	private static final String CANNOT_GET_TIMEOUT_TASK_RESULT_MESSAGE = "Couldn't get result of timeout task";
	private final OutputStream os;
	private final ScheduledExecutorService ses;
	private final Callable<String> timeoutTask;
	private final int timeout;
	private byte[] b1;
	private final Socket socket;
	private ScheduledFuture<String> sf = null;

	public TimeoutOutputStream(Socket socket, ScheduledExecutorService ses, int timeout) throws IOException {
		this.os = socket.getOutputStream();
		this.ses = ses;
		this.timeout = timeout;
		this.socket = socket;
		this.timeoutTask = new Callable<String>() {
			public String call() throws Exception {
				try {
					TimeoutOutputStream.this.os.close();
					return "Write timed out";
				} catch (Throwable var2) {
					return var2.toString();
				}
			}
		};
	}

	public synchronized void write(int b) throws IOException {
		if (this.b1 == null) {
			this.b1 = new byte[1];
		}

		this.b1[0] = (byte) b;
		this.write(this.b1);
	}

	public synchronized void write(byte[] bs, int off, int len) throws IOException {
		if (off >= 0 && off <= bs.length && len >= 0 && off + len <= bs.length && off + len >= 0) {
			if (len != 0) {
				try {
					try {
						if (this.timeout > 0) {
							this.sf = this.ses.schedule(this.timeoutTask, (long) this.timeout, TimeUnit.MILLISECONDS);
						}
					} catch (RejectedExecutionException var10) {
						if (!this.socket.isClosed()) {
							throw new IOException("Write aborted due to timeout not enforced", var10);
						}
					}

					try {
						this.os.write(bs, off, len);
					} catch (IOException var9) {
						if (this.sf != null && !this.sf.cancel(true)) {
							throw new IOException(this.handleTimeoutTaskResult(this.sf), var9);
						}

						throw var9;
					}
				} finally {
					if (this.sf != null) {
						this.sf.cancel(true);
					}

				}

			}
		} else {
			throw new IndexOutOfBoundsException();
		}
	}

	public void close() throws IOException {
		this.os.close();
		if (this.sf != null) {
			this.sf.cancel(true);
		}

	}

	private String handleTimeoutTaskResult(ScheduledFuture<String> sf) {
		boolean wasInterrupted = Thread.interrupted();
		String exceptionMessage = null;

		try {
			String var4 = (String) sf.get((long) this.timeout, TimeUnit.MILLISECONDS);
			return var4;
		} catch (TimeoutException var11) {
			exceptionMessage = String.format("%s %s", var11, this.ses.toString());
		} catch (InterruptedException var12) {
			wasInterrupted = true;
			exceptionMessage = var12.toString();
		} catch (ExecutionException var13) {
			exceptionMessage = var13.getCause() == null ? var13.toString() : var13.getCause().toString();
		} catch (Exception var14) {
			exceptionMessage = var14.toString();
		} finally {
			if (wasInterrupted) {
				Thread.currentThread().interrupt();
			}

		}

		return String.format("%s. %s", "Couldn't get result of timeout task", exceptionMessage);
	}
}