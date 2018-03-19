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
				DownloadEntry entry = new DownloadEntry(download.getFilePath().getFileName(), download.getFilePath().getPath(), download.getHttpFile().toUrl());

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

		/*DownloadEntry test = new DownloadEntry("Test.mp4", "/Animes/Test", "http://google.de");
		test.downloadedBytes = 1000L;
		test.maxBytes = 2000L;
		test.progress = 50;
		list.add(test);*/

		return list;
	}

	@Data
	public static class DownloadEntry {
		private final String saveFileName;
		private final String saveFolder;
		private final String streamingUrl;
		private long downloadedBytes;
		private long maxBytes;
		private int progress;
	}

}
