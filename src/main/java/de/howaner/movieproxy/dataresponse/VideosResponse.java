package de.howaner.movieproxy.dataresponse;

import de.howaner.movieproxy.util.DownloadedFileUtil;
import lombok.Data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class VideosResponse {

    public static List<VideosResponse.VideoFolderEntry> createVideosResponse() {
        List<VideosResponse.VideoFolderEntry> list = new ArrayList<>();

        for (final File folder : DownloadedFileUtil.getStorageFolders()) {
            final VideoFolderEntry entry = new VideoFolderEntry(folder.getName(), new ArrayList<>());
            for (final File video : DownloadedFileUtil.getDownloadedVideos(folder)) {
                entry.getVideoEntries().add(new VideoEntry(video.getName(), folder.getName()));
            }
            list.add(entry);
        }
        return list;
    }

    @Data
    public static class VideoFolderEntry {
        private final String folderName;
        private final List<VideoEntry> videoEntries;
    }

    @Data
    public static class VideoEntry {
        private final String fileName;
        private final String folder;
    }
}
