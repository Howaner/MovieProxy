package de.howaner.movieproxy.dataresponse;

import de.howaner.movieproxy.ProxyApplication;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

public class MoviesResponse {

	public static List<MovieEntry> createMovieEntries() {
		return MoviesResponse.createMovieEntries("", ProxyApplication.getInstance().getStoragePath());
	}

	public static List<MovieEntry> createMovieEntries(String folderStr, File folder) {
		List<MovieEntry> list = new ArrayList<>();

		for (File file : folder.listFiles()) {
			MovieEntry entry = MovieEntry.builder()
					.identifier(folderStr + "/" + file.getName())
					.text(file.getName())
					.population(file.lastModified() / 1000L)
					.checked(false)
					.build();

			if (file.isDirectory()) {
				entry.children = MoviesResponse.createMovieEntries(entry.identifier, file);
				entry.hasChildren = !entry.children.isEmpty();
			} else {
				entry.children = Collections.EMPTY_LIST;
				entry.hasChildren = false;
			}

			list.add(entry);
		}

		return list.stream().sorted(Comparator.comparing(s -> s.getText())).collect(Collectors.toList());
	}

	@Data
	@Builder
	public static class MovieEntry {
		private String identifier;
		private String text;
		private long population;
		//private String flagUrl;
		private boolean checked;
		private boolean hasChildren;
		private List<MovieEntry> children;
	}

}
