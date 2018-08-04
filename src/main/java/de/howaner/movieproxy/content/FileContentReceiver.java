package de.howaner.movieproxy.content;

import de.howaner.movieproxy.Constants;
import de.howaner.movieproxy.HttpConnection;
import de.howaner.movieproxy.ProxyApplication;
import de.howaner.movieproxy.util.FileInformation;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import lombok.Getter;

public class FileContentReceiver implements ContentReceiver {
	private static final long MAX_LOOP_BYTES = 1024 * 1024 * 5;  // The loop method will send maximally 5 mbytes every run.

	@Getter private final File file;
	private FileInformation fileInfo;
	private RandomAccessFile reader;

	private long offset;
	private HttpConnection connection;
	private RequestBytesCallback callback;

	public FileContentReceiver(File file) throws IOException {
		this.file = file;
		this.reader = new RandomAccessFile(file, "r");

		String contentType = Files.probeContentType(file.toPath());
		if (contentType == null)
			contentType = "video/mp4";

		this.fileInfo = new FileInformation(contentType, this.reader.length());
	}

	/**
	 * Called every second by FileContentReceiverManager
	 */
	public void loop() {
		try {
			long startOffset = this.offset;

			while (this.offset < this.fileInfo.getContentLength() && this.connection.isWritable() && (this.offset - startOffset) < MAX_LOOP_BYTES) {
				int dataLength = (int) Math.min(this.fileInfo.getContentLength() - this.offset, Constants.HTTP_CHUNK_SIZE);

				byte[] data = new byte[dataLength];
				this.reader.read(data);

				this.callback.onData(data);
				this.offset += dataLength;
			}

			if (this.offset >= this.fileInfo.getContentLength()) {
				this.removeReceiver();
				this.callback.onFinish();
			}
		} catch (Exception ex) {
			this.removeReceiver();
			this.callback.error(ex);
		}
	}

	private void removeReceiver() {
		ProxyApplication.getInstance().getFileContentReceiverManager().removeReceiver(this);
	}

	@Override
	public void requestBytes(long offset, HttpConnection connection, RequestBytesCallback callback) {
		this.offset = offset;
		this.connection = connection;
		this.callback = callback;

		callback.onStart(this.fileInfo);
		try {
			this.reader.seek(offset);
		} catch (IOException ex) {
			callback.error(ex);
			return;
		}

		ProxyApplication.getInstance().getFileContentReceiverManager().addReceiver(this);
	}

	@Override
	public void dispose(HttpConnection connection) {
		this.removeReceiver();

		try {
			this.reader.close();
		} catch (IOException ex) {}
	}

}
