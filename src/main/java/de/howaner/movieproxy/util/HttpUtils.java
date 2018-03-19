package de.howaner.movieproxy.util;

import com.google.common.net.HttpHeaders;
import de.howaner.movieproxy.exception.InvalidRequestException;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

public class HttpUtils {

	public static long readOffset(HttpRequest request) throws InvalidRequestException {
		String rangeHeader = request.headers().get(HttpHeaders.RANGE);
		if (rangeHeader == null)
			return 0L;

		if (!rangeHeader.startsWith("bytes="))
			throw new InvalidRequestException("Invalid range header (beginning): " + rangeHeader);

		int index1 = rangeHeader.indexOf('=');
		if (index1 == -1)
			throw new InvalidRequestException("Invalid range header (missing =): " + rangeHeader);

		int index2 = rangeHeader.indexOf('-');
		if (index2 == -1)
			throw new InvalidRequestException("Invalid range header (missing -): " + rangeHeader);

		String offsetStr = rangeHeader.substring(index1 + 1, index2);
		try {
			return Long.parseLong(offsetStr);
		} catch (NumberFormatException ex) {
			throw new InvalidRequestException("Invalid range header (number parse failure): " + rangeHeader, ex);
		}
	}

	public static long readOffset(HttpResponse response) {
		if (response.status() == HttpResponseStatus.PARTIAL_CONTENT && response.headers().contains(HttpHeaders.CONTENT_RANGE)) {
			String contentRange = response.headers().get(HttpHeaders.CONTENT_RANGE);
			if (!contentRange.startsWith("bytes "))
				throw new RuntimeException("Invalid range: Need to start with bytes, header: " + contentRange);

			contentRange = contentRange.substring("bytes ".length());
			int index = contentRange.indexOf('-');
			if (index == -1)
				throw new RuntimeException("Invalid range: Need to have a - , header: " + contentRange);

			return Long.parseLong(contentRange.substring(0, index));
		} else {
			return 0L;
		}
	}

	public static String getContentType(String fileName) {
		int index = fileName.lastIndexOf('.');
		if (index == -1)
			return null;

		String ending = fileName.substring(index + 1).toLowerCase();
		switch (ending) {
			case "html":     return "text/html";
			case "css":      return "text/css";
			case "js":       return "text/javascript";
			case "txt":      return "text/plain";
			case "png":      return "image/png";
			case "jpg":      return "image/jpeg";
			case "jpeg":     return "image/jpeg";
			case "json":     return "application/json";
			case "svg":      return "image/svg+xml";
			case "ttf":      return "font/opentype";
			case "woff":     return "application/x-font-woff";
			default:         return null;
		}
	}

}
