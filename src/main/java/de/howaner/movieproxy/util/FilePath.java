package de.howaner.movieproxy.util;

import de.howaner.movieproxy.ProxyApplication;
import java.io.File;
import lombok.Data;

@Data
public class FilePath {
	private final String fileName;
	private final String path;

	public File getFile() {
		return new File(ProxyApplication.getInstance().getStoragePath(), this.path + File.separator + this.fileName);
	}

}
