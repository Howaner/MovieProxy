package de.howaner.movieproxy.content;

import de.howaner.movieproxy.HttpConnection;

public interface ContentReceiver {

	public void requestBytes(long offset, HttpConnection connection, RequestBytesCallback callback);

	public void dispose(HttpConnection connection);

}
