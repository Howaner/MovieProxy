package de.howaner.movieproxy.download;

import de.howaner.movieproxy.HttpConnection;
import de.howaner.movieproxy.ProxyApplication;
import de.howaner.movieproxy.content.ContentReceiver;
import de.howaner.movieproxy.content.RequestBytesCallback;
import de.howaner.movieproxy.util.FileInformation;
import de.howaner.movieproxy.util.FilePath;
import de.howaner.movieproxy.util.HttpFile;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class Download implements ContentReceiver {
	private String identifier;
	private FilePath filePath;
	private HttpFile httpFile;
	private volatile boolean cancelled = false;

	private FileInformation fileInfo;
	private long lastChanges;
	private RandomAccessFile writer;
	private Lock writeLock = new ReentrantLock();
	private OffsetMap offsetMap = new OffsetMap();

	private DownloadConnection dlConnection;
	private List<DownloadCallback> callbacks = new ArrayList<>();

	public Download(String identifier, FilePath filePath, HttpFile httpFile) {
		this.identifier = identifier;
		this.filePath = filePath;
		this.httpFile = httpFile;
		this.lastChanges = System.currentTimeMillis();

		try {
			File file = new File(ProxyApplication.getInstance().getCachePath(), this.identifier);
			this.writer = new RandomAccessFile(file, "rw");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	protected void setFileInfo(FileInformation fileInfo) throws IOException {
		this.fileInfo = fileInfo;
		this.writer.setLength(fileInfo.getContentLength());
		this.log("Received file informations (content length: {}, content type: {})", fileInfo.getContentLength(), fileInfo.getContentType());
	}

	public void finish() {
		try {
			this.writer.close();
		} catch (IOException ex) {
			this.log("Can't close stream.", ex);
		}
		this.writer = null;
	}

	protected void cancel() {
		this.cancelled = true;

		if (this.dlConnection != null) {
			this.dlConnection.close();
			this.dlConnection = null;
		}

		try {
			this.writeLock.lock();
			try {
				this.writer.close();
			} catch (IOException ex) {
				this.log("Can't close stream.", ex);
			}
			this.writer = null;

			File cacheFile = new File(ProxyApplication.getInstance().getCachePath(), this.identifier);
			if (cacheFile.exists())
				cacheFile.delete();
		} finally {
			this.writeLock.unlock();
		}

		this.callbacks.forEach(c -> c.getRequestCallback().error(new Exception("Download terminated.")));
		this.callbacks.clear();
	}

	public void startDownloadConnection(long offset, long maxOffset) {
		if (cancelled)
			return;

		if (this.dlConnection != null)
			this.dlConnection.close();

		this.log("Start new download with offset {} and maxoffset {} ...", offset, maxOffset);

		this.dlConnection = new DownloadConnection(this, offset, maxOffset);
		this.dlConnection.connect();
	}

	/**
	 * Called by DownloadConnection if the connection was closed.
	 */
	protected void disposeDownloadConnection() {
		if (this.dlConnection != null) {
			this.dlConnection = null;
			this.log("Disposed download.");
		}
	}

	public byte[] readBytes(long offset, int amount) throws IOException {
		this.writeLock.lock();
		try {
			this.writer.seek(offset);

			byte[] data = new byte[amount];
			this.writer.read(data);

			return data;
		} finally {
			this.writeLock.unlock();
		}
	}

	public void writeBytes(long offset, byte[] content) throws IOException {
		if (cancelled)
			return;

		this.writeLock.lock();
		try {
			this.writer.seek(offset);
			this.writer.write(content);
		} finally {
			this.writeLock.unlock();
			this.lastChanges = System.currentTimeMillis();
		}
	}

	@Override
	public void requestBytes(long offset, HttpConnection connection, RequestBytesCallback callback) {
		this.lastChanges = System.currentTimeMillis();

		DownloadCallback dlCallback;
		try {
			ProxyApplication.getInstance().getDownloadManager().getDownloadsLock().writeLock().lock();
			dlCallback = new DownloadCallback(callback, connection, offset);
			this.callbacks.add(dlCallback);
		} finally {
			ProxyApplication.getInstance().getDownloadManager().getDownloadsLock().writeLock().unlock();
		}

		if (this.fileInfo != null && (this.dlConnection == null || this.dlConnection.isConnected()))
			dlCallback.getRequestCallback().onStart(this.fileInfo);
	}

	@Override
	public void dispose(HttpConnection connection) {
		this.lastChanges = System.currentTimeMillis();
		try {
			ProxyApplication.getInstance().getDownloadManager().getDownloadsLock().writeLock().lock();
			DownloadCallback dlCallback = this.callbacks.stream().filter(c -> c.getConnection() == connection).findAny().orElse(null);
			if (dlCallback != null)
				this.callbacks.remove(dlCallback);
		} finally {
			ProxyApplication.getInstance().getDownloadManager().getDownloadsLock().writeLock().unlock();
		}
	}

	public void log(String message, Object ... params) {
		ProxyApplication.getInstance().getLogger().info("[Download " + this.identifier + "] " + message, params);
	}

	public void log(String message, Throwable ex) {
		ProxyApplication.getInstance().getLogger().error("[Download " + this.identifier + "] " + message, ex);
	}

}
