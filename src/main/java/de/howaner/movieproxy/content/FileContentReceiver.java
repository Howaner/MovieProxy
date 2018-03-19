package de.howaner.movieproxy.content;

import de.howaner.movieproxy.Constants;
import de.howaner.movieproxy.HttpConnection;
import de.howaner.movieproxy.util.FileInformation;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class FileContentReceiver implements ContentReceiver {
	private final File file;
	private FileInformation fileInfo;
	private RandomAccessFile reader;

	public FileContentReceiver(File file) throws IOException {
		this.file = file;
		this.reader = new RandomAccessFile(file, "r");

		this.fileInfo = new FileInformation("video/mp4", this.reader.length());
	}

	@Override
	public void requestBytes(long offset, HttpConnection connection, RequestBytesCallback callback) {
		callback.onStart(fileInfo);

		try {
			this.reader.seek(offset);

			while (offset < this.fileInfo.getContentLength()) {
				int dataLength = (int) Math.min(this.fileInfo.getContentLength() - offset, Constants.HTTP_CHUNK_SIZE);

				byte[] data = new byte[dataLength];
				this.reader.read(data);

				callback.onData(data);
				offset += dataLength;
			}
		} catch (Exception ex) {
			callback.error(ex);
		} finally {
			callback.onFinish();
		}
	}

	@Override
	public void dispose(HttpConnection connection) {
		try {
			this.reader.close();
		} catch (IOException ex) {}
	}

}
