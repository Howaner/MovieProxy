package de.howaner.movieproxy.dataresponse;

import de.howaner.movieproxy.ProxyApplication;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

public class FoldersResponse {

	public static List<FolderEntry> createFolderEntries() {
		return createFolderEntries("", ProxyApplication.getInstance().getStoragePath());
	}

	public static List<FolderEntry> createFolderEntries(String folderStr, File folder) {
		List<FolderEntry> list = new ArrayList<>();

		for (File file : folder.listFiles()) {
			if (!file.isDirectory())
				continue;

			FolderEntry entry = FolderEntry.builder()
					.identifier(folderStr + "/" + file.getName())
					.text(file.getName())
					.population(file.lastModified() / 1000L)
					.checked(false)
					.build();
			entry.children = createFolderEntries(entry.identifier, file);
			entry.hasChildren = !entry.children.isEmpty();
			list.add(entry);
		}

		return list.stream().sorted(Comparator.comparingLong((FolderEntry x) -> x.population).reversed()).collect(Collectors.toList());
	}

	@Data
	@Builder
	public static class FolderEntry {
		private String identifier;
		private String text;
		private long population;
		//private String flagUrl;
		private boolean checked;
		private boolean hasChildren;
		private List<FolderEntry> children;
	}

}
