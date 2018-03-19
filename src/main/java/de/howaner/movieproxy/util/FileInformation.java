package de.howaner.movieproxy.util;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileInformation {
	private final String contentType;
	private final long contentLength;

}
