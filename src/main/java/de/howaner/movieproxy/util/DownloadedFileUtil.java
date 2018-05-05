package de.howaner.movieproxy.util;

import java.io.File;
import java.util.Arrays;

public final class DownloadedFileUtil {

    private DownloadedFileUtil() {

    }

    public static File[] getDownloadedVideos(final File folder) {
        return folder.listFiles();
    }

    public static File[] getDownloadedVideos(final String folderName) {
        final File folder = getFolderByName(folderName);
        return getDownloadedVideos(folder);
    }

    public static File getFolderByName(final String folderName) {
        return Arrays.stream(getStorageFolders())
                .filter(file -> file.getName().equalsIgnoreCase(folderName))
                .findFirst().orElse(null);
    }

    public static File[] getStorageFolders() {
        final File storage = new File("storage");
        if (!storage.exists())
            throw new RuntimeException("Storage folder must be created!");
        final File[] files = storage.listFiles();
        if (files == null)
            return new File[0];
        return files;
    }

    public static File getDownloadedFile(final String folder, final String name) {
        return Arrays.stream(getDownloadedVideos(folder)).filter(file -> file.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
