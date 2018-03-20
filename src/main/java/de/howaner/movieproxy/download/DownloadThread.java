package de.howaner.movieproxy.download;

import de.howaner.movieproxy.Constants;
import de.howaner.movieproxy.ProxyApplication;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DownloadThread extends Thread {
	private final DownloadManager dlManager;
	private long lastConnectionCheck = System.currentTimeMillis();

	public DownloadThread(DownloadManager dlManager) {
		super("Download thread");
		this.setDaemon(true);
		this.setPriority(MIN_PRIORITY);

		this.dlManager = dlManager;
	}

	@Override
	public void run() {
		while (!this.isInterrupted()) {
			List<Download> finishedDownloads = new ArrayList<>();
			List<DownloadCallback> finishedCallbacks = new ArrayList<>();
			try {
				this.dlManager.getDownloadsLock().readLock().lock();

				// Check connections every 10 seconds, maybe there are dead connections in download callbacks (I don't know why ...)
				if ((System.currentTimeMillis() - this.lastConnectionCheck) >= 10000L) {
					for (Download download : this.dlManager.getDownloads()) {
						download.getCallbacks().removeIf(c -> c.getConnection().isClosed());
					}
					this.lastConnectionCheck = System.currentTimeMillis();
				}

				for (Download download : this.dlManager.getDownloads()) {
					if (download.getFileInfo() == null)
						continue;

					List<DownloadCallback> notLoadedCallbacks = new ArrayList<>();

					for (DownloadCallback callback : download.getCallbacks()) {
						if (callback.getOffset() + 1 >= download.getFileInfo().getContentLength()) {
							finishedCallbacks.add(callback);
							continue;
						}

						if (!callback.getRequestCallback().isWritable())
							continue;

						long startOffset = callback.getOffset();
						long endOffset = startOffset + Constants.HTTP_CHUNK_SIZE - 1;
						if (endOffset >= download.getFileInfo().getContentLength()) {
							endOffset = download.getFileInfo().getContentLength() - 1;
						}

						if (download.getOffsetMap().contains(startOffset, endOffset)) {
							byte[] data = download.readBytes(startOffset, (int) (endOffset - startOffset + 1));
							callback.getRequestCallback().onData(data);
							callback.setOffset(endOffset + 1);
						} else {
							notLoadedCallbacks.add(callback);
						}
					}

					if (!notLoadedCallbacks.isEmpty()) {
						DownloadCallback callback = notLoadedCallbacks.stream().sorted(Comparator.comparingLong((DownloadCallback c) -> c.getCreationTime()).reversed()).findFirst().get();
						if (download.getDlConnection() == null
								|| ((System.currentTimeMillis() - download.getDlConnection().getCreationTime()) > Constants.SWITCH_CONNECTION_OFFSET_DELAY &&
								(
									(callback.getOffset() < download.getDlConnection().getOffset() && !download.getOffsetMap().contains(callback.getOffset(), download.getDlConnection().getOffset() - 1))
									|| (callback.getOffset() - download.getDlConnection().getOffset()) > Constants.ACCEPTABLE_WAITING_RANGE)
								)) {
							download.log("Switched connection to offset {} because the browser want it.", callback.getOffset());
							download.startDownloadConnection(callback.getOffset(), 0L);
						}
					} else if (download.getDlConnection() == null) {
						List<OffsetMap.OffsetEntry> missingEntries = download.getOffsetMap().searchMissingOffsets(download.getFileInfo().getContentLength() - 1);
						if (missingEntries.isEmpty()) {
							// Complete download if no connections are open
							if (download.getCallbacks().isEmpty() && (System.currentTimeMillis() - download.getLastChanges()) > Constants.DOWNLOAD_FINISH_WAIT_TIME) {
								finishedDownloads.add(download);
							}
						} else if ((System.currentTimeMillis() - download.getLastChanges()) > Constants.DOWNLOAD_LOOKUP_WAIT_TIME) {
							// Start new download
							OffsetMap.OffsetEntry offset = missingEntries.get(0);
							download.log("Download the file from offset {} - {} to complete bytes.", offset.start, offset.end);
							download.startDownloadConnection(offset.start, offset.end);
						}
					}
				}
			} catch (Exception ex) {
				ProxyApplication.getInstance().getLogger().error("Exception while executing download thread", ex);
			} finally {
				this.dlManager.getDownloadsLock().readLock().unlock();
			}

			// Finish finished downloads
			try {
				if (!finishedCallbacks.isEmpty())
					finishedCallbacks.forEach(c -> c.getRequestCallback().onFinish());

				if (!finishedDownloads.isEmpty())
					finishedDownloads.forEach(ProxyApplication.getInstance().getDownloadManager()::finishDownload);
			} catch (Exception ex) {
				ProxyApplication.getInstance().getLogger().error("Exception while executing download thread (finishing downloads)", ex);
			}

			try {
				Thread.sleep(5L);
			} catch (InterruptedException ex) {
				break;
			}
		}
	}

}
