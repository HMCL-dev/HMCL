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
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jackhuang.hmcl.util.Logging.LOG;

class DownloadManager {

    static DownloadTaskState download(List<String> urls, Path file, int initialParts) throws IOException {
        Path downloadingFile = file.resolveSibling(FileUtils.getName(file) + ".download");
        Path stateFile = file.resolveSibling(FileUtils.getName(file) + ".status");
        DownloadState state = null;
        if (Files.exists(downloadingFile) && Files.exists(stateFile)) {
            // Resume downloading from state
            try {
                String status = FileUtils.readText(stateFile);
                state = JsonUtils.fromNonNullJson(status, DownloadState.class);
            } catch (JsonParseException e) {
                LOG.log(Level.WARNING, "Failed to parse download state file", e);
            }
        }

        if (state == null || !urls.equals(state.urls)) {
            return DownloadTaskState.newWithLengthUnknown(urls, initialParts);
        } else {
            return new DownloadTaskState(state);
        }
    }

    protected static class DownloadTaskState {
        private final List<String> urls;
        private final List<DownloadSegment> segments;
        private final List<Thread> threads;
        private String fastestUrl;
        private int retry = 0;
        private boolean cancelled = false;

        DownloadTaskState(DownloadState state) {
            urls = new ArrayList<>(state.urls);
            segments = new ArrayList<>(state.segments);
            threads = IntStream.range(0, state.segments.size()).mapToObj(x -> (Thread) null).collect(Collectors.toList());
        }

        DownloadTaskState(List<String> urls, int contentLength, int initialParts) {
            if (urls == null || urls.size() == 0) {
                throw new IllegalArgumentException("DownloadTaskState requires at least one url candidate");
            }

            this.urls = new ArrayList<>(urls);
            segments = new ArrayList<>(initialParts);
            threads = new ArrayList<>(initialParts);
            int partLength = contentLength / initialParts;
            for (int i = 0; i < initialParts; i++) {
                int begin = partLength * i;
                int end = Math.min((partLength + 1) * i, contentLength);
                segments.add(new DownloadSegment(begin, end, 0));
                threads.add(null);
            }
        }

        public static DownloadTaskState newWithLengthUnknown(List<String> urls, int initialParts) {
            return new DownloadTaskState(urls, 0, initialParts);
        }

        public List<String> getUrls() {
            return urls;
        }

        public List<DownloadSegment> getSegments() {
            return segments;
        }

        public String getFirstUrl() {
            return urls.get(0);
        }

        /**
         * Next url for download runnable to retry.
         *
         * If some download runnable fails to connect to url, it will call this method
         * to acquire next url for retry. Making all download runnable try different
         * candidates concurrently to speed up finding fastest download source.
         *
         * @return next url to retry
         */
        public synchronized String getNextUrlToRetry() {
            String url = urls.get(retry);
            retry = (retry + 1) % urls.size();
            return url;
        }

        /**
         * One candidate that is accessible and best network connection qualified.
         *
         * When some download runnable have started downloading, DownloadManager will
         * monitor download speed and make failed download runnable connect to the
         * fastest url directly without retry.
         *
         * In some times, the fastest url may be the first url suceeded in connection.
         *
         * @return fastest url, null if no url have successfully connected yet.
         */
        public synchronized String getFastestUrl() {
            return fastestUrl;
        }

        public synchronized void setFastestUrl(String fastestUrl) {
            this.fastestUrl = fastestUrl;
        }

        public synchronized void cancel() {
            cancelled = true;
        }

        public synchronized boolean isCancelled() {
            return cancelled;
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
