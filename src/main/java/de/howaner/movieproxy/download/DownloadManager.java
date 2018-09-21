package de.howaner.movieproxy.download;

import com.google.common.io.Files;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.howaner.movieproxy.ProxyApplication;
import de.howaner.movieproxy.util.FilePath;
import de.howaner.movieproxy.util.HttpFile;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.Getter;

public class DownloadManager {
	@Getter private EventLoopGroup eventLoop;
	private DownloadThread dlThread;

	@Getter private final ReadWriteLock downloadsLock = new ReentrantReadWriteLock();
	private final Map<String, Download> downloads = new HashMap<>();
	private final Map<String, File> downloadRedirects = new HashMap<>();

	public DownloadManager() {
		this.eventLoop = Epoll.isAvailable()
				? new EpollEventLoopGroup(2, new ThreadFactoryBuilder().setNameFormat("Epoll Http Client").setDaemon(true).build())
				: new NioEventLoopGroup(2, new ThreadFactoryBuilder().setNameFormat("Nio Http Client").setDaemon(true).build());

		this.dlThread = new DownloadThread(this);
		this.dlThread.start();
	}

	public Collection<Download> getDownloads() {
		return this.downloads.values();
	}

	public File getDownloadRedirect(String identifier) {
		return this.downloadRedirects.get(identifier);
	}

	public void addDownloadRedirect(String identifier, File file) {
		this.downloadRedirects.put(identifier, file);
	}

	public Download getDownload(String identifier) {
		try {
			this.downloadsLock.readLock().lock();
			return this.downloads.get(identifier);
		} finally {
			this.downloadsLock.readLock().unlock();
		}
	}

	public Download createDownload(String identifier, FilePath filePath, HttpFile httpFile) {
		try {
			this.downloadsLock.writeLock().lock();
			if (this.downloads.containsKey(identifier))
				return null;

			Download download = new Download(identifier, filePath, httpFile);
			this.downloads.put(identifier, download);

			download.log("Created new download with url {} and file {}/{}", httpFile.toUrl(), filePath.getPath(), filePath.getFileName());
			return download;
		} finally {
			this.downloadsLock.writeLock().unlock();
		}
	}

	public void finishDownload(Download download) {
		download.log("Finished download.");

		boolean lock = false;
		try {
			lock = this.downloadsLock.writeLock().tryLock();
			this.downloads.remove(download.getIdentifier());

			download.finish();

			File cacheFile = new File(ProxyApplication.getInstance().getCachePath(), download.getIdentifier());
			if (cacheFile.exists()) {
				File destFile = download.getFilePath().getFile();
				Files.move(cacheFile, destFile);

				this.addDownloadRedirect(download.getIdentifier(), destFile);
				download.log("Moved cache file {} to {}", cacheFile.getPath(), destFile.getPath());
			} else {
				download.log("Can't move file to storage because cache file doesn't exist.");
			}
		} catch (IOException ex) {
			download.log("Exception while moving file", ex);
		} finally {
			if (lock)
				this.downloadsLock.writeLock().unlock();
		}
	}

	public void cancelDownload(Download download) {
		download.log("Download cancelled.");

		boolean lock = false;
		try {
			lock = this.downloadsLock.writeLock().tryLock();
			this.downloads.remove(download.getIdentifier());

			download.cancel();
		} finally {
			if (lock)
				this.downloadsLock.writeLock().unlock();
		}
	}

}
