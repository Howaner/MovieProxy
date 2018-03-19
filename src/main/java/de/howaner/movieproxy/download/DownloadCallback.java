package de.howaner.movieproxy.download;

import de.howaner.movieproxy.HttpConnection;
import de.howaner.movieproxy.content.RequestBytesCallback;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DownloadCallback {
	private final RequestBytesCallback requestCallback;
	private final HttpConnection connection;
	private long offset;

}
