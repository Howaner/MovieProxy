package de.howaner.movieproxy;

import com.google.gson.Gson;
import de.howaner.movieproxy.download.DownloadManager;
import de.howaner.movieproxy.server.HttpServer;
import java.io.File;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProxyApplication {
	@Getter private static ProxyApplication instance;

	@Getter private DownloadManager downloadManager = new DownloadManager();
	@Getter private final Logger logger;
	@Getter private final Gson gson;

	public ProxyApplication() {
		this.logger = LogManager.getLogger();
		this.gson = new Gson();
	}

	public static void main(String[] args) {
		instance = new ProxyApplication();
		instance.start();
	}

	public File getStoragePath() {
		return new File("storage");
	}

	public File getCachePath() {
		return new File("cache");
	}

	private void start() {
		this.logger.info("Hello world!");

		if (!this.getStoragePath().isDirectory())
			this.getStoragePath().mkdir();
		if (!this.getCachePath().isDirectory())
			this.getCachePath().mkdir();

		try {
			for (File file : this.getCachePath().listFiles()) {
				if (file.isFile())
					file.delete();
			}
		} catch (Exception ex) {
			
		}

		HttpServer server = new HttpServer();
		server.startServer();

		// Wait until stop
		try {
			server.getServer().channel().closeFuture().sync();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		// TODO
	}

}
