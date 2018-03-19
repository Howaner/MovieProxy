package de.howaner.movieproxy.content;

import de.howaner.movieproxy.util.FileInformation;

public interface RequestBytesCallback {

	public void onStart(FileInformation fileInfo);

	public void onData(byte[] data);

	public void onFinish();

	public void error(Exception ex);

}
