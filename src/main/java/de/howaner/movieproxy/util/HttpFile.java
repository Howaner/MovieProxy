package de.howaner.movieproxy.util;

import io.netty.handler.codec.http.HttpMethod;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class HttpFile {
	private String protocol;
	private HttpMethod method;
	private String host;
	private String path;

	private static final Pattern URL_PATTERN = Pattern.compile("([a-zA-Z]*)://([a-zA-Z0-9.\\-_]*)/(.*)");

	public static HttpFile createFromUrl(String url) {
		Matcher matcher = URL_PATTERN.matcher(url);
		if (!matcher.matches())
			return null;

		return new HttpFile(matcher.group(1), HttpMethod.GET, matcher.group(2), "/" + matcher.group(3));
	}

	public String toUrl() {
		return String.format("%s://%s%s", this.protocol, this.host, this.path);
	}

}
