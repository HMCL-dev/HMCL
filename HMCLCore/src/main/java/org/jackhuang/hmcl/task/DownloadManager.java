package org.jackhuang.hmcl.task;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class DownloadManager {

    static DownloadState ne(int contentLength, int initialParts) {

    }

    static DownloadTaskState download(List<String> urls, Path file, int initialParts) throws IOException {
        Path downloadingFile = file.resolveSibling(FileUtils.getName(file) + ".download");
        Path stateFile = file.resolveSibling(FileUtils.getName(file) + ".status");
        DownloadState state;
        if (Files.exists(downloadingFile) && Files.exists(stateFile)) {
            // Resume downloading from state
            try {
                String status = FileUtils.readText(stateFile);
                state = JsonUtils.fromNonNullJson(status, DownloadState.class);
            } catch (JsonParseException e) {
                state =
            }
        }
    }

    protected static class DownloadTaskState {
        private final List<String> urls;
        private final List<DownloadSegment> segments;

        DownloadTaskState(DownloadState state) {
            urls = new ArrayList<>(state.urls);
            segments = new ArrayList<>(state.segments);
        }

        DownloadTaskState(List<String> urls, int contentLength, int initialParts) {
            urls = new ArrayList<>(urls);
            segments = new ArrayList<>(initialParts);
            int partLength = contentLength / initialParts;
            for (int i = 0; i < initialParts; i++) {
                int begin = partLength * i;
                int end = Math.min((partLength + 1) * i, contentLength);
                segments.add(new DownloadSegment(begin, end, 0));
            }
        }

        public static DownloadTaskState newWithLengthUnknown(List<String> urls, int initialParts) {
            return
        }

        public List<String> getUrls() {
            return urls;
        }

        public List<DownloadSegment> getSegments() {
            return segments;
        }
    }

    protected static class DownloadState {
        private final List<String> urls;
        private final List<DownloadSegment> segments;

        /**
         * Constructor for Gson
         */
        public DownloadState() {
            this(Collections.emptyList(), Collections.emptyList());
        }

        public DownloadState(List<String> urls, List<DownloadSegment> segments) {
            this.urls = urls;
            this.segments = segments;
        }

        public List<String> getUrls() {
            return urls;
        }

        public List<DownloadSegment> getSegments() {
            return segments;
        }
    }

    protected static class DownloadSegment {
        private final int startPosition;
        private final int endPosition;
        private int currentPosition;

        /**
         * Constructor for Gson
         */
        public DownloadSegment() {
            this(0, 0, 0);
        }

        public DownloadSegment(int startPosition, int endPosition, int currentPosition) {
            if (currentPosition < startPosition || currentPosition > endPosition) {
                throw new IllegalArgumentException("Illegal download state: start " + startPosition + ", end " + endPosition + ", cur " + currentPosition);
            }
            this.startPosition = startPosition;
            this.endPosition = endPosition;
            this.currentPosition = currentPosition;
        }

        public int getStartPosition() {
            return startPosition;
        }

        public int getEndPosition() {
            return endPosition;
        }

        public int getCurrentPosition() {
            return currentPosition;
        }

        public void setCurrentPosition(int currentPosition) {
            this.currentPosition = currentPosition;
        }

        public boolean isFinished() {
            return currentPosition == endPosition;
        }

        public boolean isWaiting() { return startPosition == endPosition && startPosition == 0; }
    }

}
