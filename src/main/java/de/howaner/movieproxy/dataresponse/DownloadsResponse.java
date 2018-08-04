package de.howaner.movieproxy.dataresponse;

import de.howaner.movieproxy.ProxyApplication;
import de.howaner.movieproxy.download.Download;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

public class DownloadsResponse {

	public static List<DownloadEntry> createDownloadsResponse() {
		List<DownloadEntry> list = new ArrayList<>();

		try {
			ProxyApplication.getInstance().getDownloadManager().getDownloadsLock().readLock().lock();
			for (Download download : ProxyApplication.getInstance().getDownloadManager().getDownloads()) {
				DownloadEntry entry = new DownloadEntry(download.getFilePath().getFileName(), download.getFilePath().getPath(), "/proxy/" + download.getIdentifier() + ".mp4", "/cancel/" + download.getIdentifier());

				if (download.getFileInfo() != null) {
					entry.maxBytes = download.getFileInfo().getContentLength();
					entry.downloadedBytes = download.getFileInfo().getContentLength() - download.getOffsetMap().getMissingBytesAmount(download.getFileInfo().getContentLength() - 1);
					entry.progress = (int) ( (entry.downloadedBytes / (float)entry.maxBytes) * 100F );
				}

				list.add(entry);
			}
		} finally {
			ProxyApplication.getInstance().getDownloadManager().getDownloadsLock().readLock().unlock();
		}

		return list;
	}

	@Data
	public static class DownloadEntry {
		private final String saveFileName;
		private final String saveFolder;
		private final String streamingUrl;
		private final String cancelUrl;
		private long downloadedBytes;
		private long maxBytes;
		private int progress;
	}

}
