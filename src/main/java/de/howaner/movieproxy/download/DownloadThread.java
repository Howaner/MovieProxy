package de.howaner.movieproxy.download;

import de.howaner.movieproxy.Constants;
import de.howaner.movieproxy.ProxyApplication;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DownloadThread extends Thread {
	private final DownloadManager dlManager;

	public DownloadThread(DownloadManager dlManager) {
		super("Download thread");
		this.setDaemon(true);
		this.setPriority(MIN_PRIORITY);

		this.dlManager = dlManager;
	}

	@Override
	public void run() {
		while (!this.isInterrupted()) {
			try {
				this.dlManager.getDownloadsLock().readLock().lock();
				List<Download> finishedDownloads = new ArrayList<>();

				for (Download download : this.dlManager.getDownloads()) {
					List<DownloadCallback> notLoadedCallbacks = new ArrayList<>();

					for (DownloadCallback callback : download.getCallbacks()) {
						try {
							if (download.getFileInfo() != null && callback.getOffset() >= download.getFileInfo().getContentLength()) {
								callback.getRequestCallback().onFinish();
							}

							if (download.getOffsetMap().contains(callback.getOffset(), callback.getOffset() + Constants.HTTP_CHUNK_SIZE)) {
								byte[] data = download.readBytes(callback.getOffset(), Constants.HTTP_CHUNK_SIZE);
								callback.getRequestCallback().onData(data);
								callback.setOffset(callback.getOffset() + Constants.HTTP_CHUNK_SIZE);
							} else {
								notLoadedCallbacks.add(callback);
							}
						} catch (IOException ex) {
							ex.printStackTrace();
						}
					}

					if (download.getCallbacks().size() == 1 && notLoadedCallbacks.size() == 1) {
						DownloadCallback callback = notLoadedCallbacks.get(0);
						if (download.getDlConnection() == null || Math.abs(callback.getOffset() - download.getDlConnection().getOffset()) > Constants.ACCEPTABLE_WAITING_RANGE) {
							download.log("Switched connection to offset {} because the browser want it.", callback.getOffset());
							download.startDownloadConnection(callback.getOffset(), 0L);
						}
					} else if (download.getCallbacks().isEmpty() && (System.currentTimeMillis() - download.getLastChanges()) > Constants.CONNECTION_WAIT_TIME && download.getDlConnection() == null && download.getFileInfo() != null) {
						List<OffsetMap.OffsetEntry> missingEntries = download.getOffsetMap().searchMissingOffsets(download.getFileInfo().getContentLength() - 1);
						if (missingEntries.isEmpty()) {
							// Download completed :)
							finishedDownloads.add(download);
						} else {
							// Start new download
							OffsetMap.OffsetEntry offset = missingEntries.get(0);
							download.log("Download the file from offset {} - {} to complete bytes.", offset.start, offset.end);
							download.startDownloadConnection(offset.start, offset.end);
						}
					}
				}

				// Finish finished downloads
				if (!finishedDownloads.isEmpty())
					finishedDownloads.forEach(ProxyApplication.getInstance().getDownloadManager()::finishDownload);
			} catch (Exception ex) {
				ex.printStackTrace();
			} finally {
				this.dlManager.getDownloadsLock().readLock().unlock();
			}

			try {
				Thread.sleep(5L);
			} catch (InterruptedException ex) {
				break;
			}
		}
	}

}
