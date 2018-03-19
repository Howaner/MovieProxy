package de.howaner.movieproxy.util;

import io.netty.handler.codec.http.HttpResponseStatus;

public enum CloseReason {
	NewConnection, InvalidRequest, StreamClosed, Unknown, Finished, Error;

	public HttpResponseStatus getHttpResponseStatus() {
		switch (this) {
			case InvalidRequest:
				return HttpResponseStatus.BAD_REQUEST;
			default:
				return HttpResponseStatus.INTERNAL_SERVER_ERROR;
		}
	}

}
