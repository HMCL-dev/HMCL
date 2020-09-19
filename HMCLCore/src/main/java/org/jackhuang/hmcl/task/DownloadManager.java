/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.task;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jackhuang.hmcl.util.Logging.LOG;

public class DownloadManager {

    public static class DownloadTaskStateBuilder {
        private List<URL> urls;
        private Path file;
        private int retry = 3;
        private int initialParts = 1;

        /**
         * Set the url of remote file to be downloaded.
         * @param url url of the remote file to be downloaded
         * @return this
         */
        public DownloadTaskStateBuilder setUrl(URL url) {
            this.urls = Collections.singletonList(url);
            return this;
        }

        /**
         * Set urls of the remote file to be downloaded, will be attempted in order.
         * @param urls urls of remote files to be downloaded
         * @return this
         */
        public DownloadTaskStateBuilder setUrls(List<URL> urls) {
            this.urls = urls;
            return this;
        }

        /**
         * Set location to save remote file.
         * @param file location to save the remote file.
         * @return this
         */
        public DownloadTaskStateBuilder setFile(Path file) {
            this.file = file;
            return this;
        }

        /**
         * Set location to save remote file.
         * @param file location to save the remote file.
         * @return this
         */
        public DownloadTaskStateBuilder setFile(File file) {
            this.file = file.toPath();
            return this;
        }

        /**
         * Set retry times of one url.
         * @param retry retry times of one url.
         * @return this
         */
        public DownloadTaskStateBuilder setRetry(int retry) {
            this.retry = retry;
            return this;
        }

        /**
         * Splits the remote file into multiple parts, and download in different
         * threads.
         *
         * @param initialParts number of threads to download the file.
         * @return this
         */
        public DownloadTaskStateBuilder setInitialParts(int initialParts) {
            this.initialParts = initialParts;
            return this;
        }

        public DownloadTaskState build() {
            if (file == null) {
                return DownloadTaskState.newWithLengthUnknown(urls, null, retry, initialParts);
            }

            Path downloadingFile = file.resolveSibling(FileUtils.getName(file) + ".download");
            Path stateFile = file.resolveSibling(FileUtils.getName(file) + ".status");
            DownloadState state = null;
            if (Files.exists(downloadingFile) && Files.exists(stateFile)) {
                // Resume downloading from state
                try {
                    String status = FileUtils.readText(stateFile);
                    state = JsonUtils.fromNonNullJson(status, DownloadState.class);
                } catch (JsonParseException | IOException e) {
                    LOG.log(Level.WARNING, "Failed to parse download state file", e);
                }
            }

            if (state == null || !urls.equals(state.urls)) {
                return DownloadTaskState.newWithLengthUnknown(urls, file, retry, initialParts);
            } else {
                return new DownloadTaskState(state, file, retry);
            }
        }
    }

    protected static class SafeRegion implements AutoCloseable {
        final ReentrantLock lock = new ReentrantLock();

        void begin() {
            lock.lock();
        }

        void end() {
            lock.unlock();
        }

        @Override
        public void close() {
            end();
        }
    }

    public static abstract class Downloader<T> {

        /**
         * do something before connection.
         *
         * @param url currently ready URL
         */
        protected void onBeforeConnection(URL url) {}

        /**
         * Setup downloading environment, creates files, etc.
         *
         * @throws IOException if an I/O error occurred.
         */
        protected abstract void onStart() throws IOException;

        protected abstract void onContentLengthChanged(long contentLength) throws IOException;

        /**
         * Accept recently downloaded data segment.
         *
         * @throws IOException if an I/O error occurred.
         */
        protected abstract void write(long pos, byte[] buffer, int offset, int len) throws IOException;

        /**
         * Verify if we should check etag, or even it is already cached,
         * and make use of it.
         *
         * @return whether we should check etag, or stop downloading if cached.
         */
        protected abstract DownloadTask.EnumCheckETag shouldCheckETag();

        /**
         * Make cached file as result of downloader.
         *
         * This method can fails, if failed, downloading should continue.
         *
         * @param cachedFile verified cached file
         * @throws IOException if an I/O error occurred.
         */
        protected abstract T finishWithCachedResult(Path cachedFile) throws IOException;

        /**
         * Inform downloader that downloading finished.
         *
         * Check state.isFinished(), and do necessary cleaning if downloading failed.
         *
         * @return result of downloader.
         * @throws IOException if an I/O error occurred.
         */
        protected abstract T finish() throws IOException;

    }

    public static abstract class DownloadTask<T> extends CompletableFutureTask<T> {
        protected final DownloadTaskState state;
        protected boolean caching = false;
        private boolean doFinish = true;
        protected CacheRepository repository = CacheRepository.getInstance();
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private EnumCheckETag checkETag;
        private Downloader<T> downloader;

        public DownloadTask(DownloadTaskState state) {
            this.state = state;
        }

        public final void setCaching(boolean caching) {
            this.caching = caching;
        }

        public final void setCacheRepository(CacheRepository repository) {
            this.repository = repository;
        }

        public final DownloadTaskState getDownloadState() {
            return state;
        }

        protected abstract Downloader<T> createDownloader();

        protected final EnumCheckETag getCheckETag() { return checkETag; }

        protected synchronized final void setContentLength(int contentLength) throws IOException {
            if (state.setContentLength(contentLength)) {
                downloader.onContentLengthChanged(contentLength);
            }
        }

        protected synchronized final void finishWithCachedResult(Path cachedFile) throws IOException {
            setResult(downloader.finishWithCachedResult(cachedFile));

            state.finished = true;
            doFinish = false;
            future.complete(null);
        }

        public synchronized final void onSegmentFinished(DownloadSegment finishedSegment) {
            assert(state.segments.contains(finishedSegment));
            finishedSegment.finished = true;

            int max = 0;

            for (DownloadSegment segment : state.segments) {
                if (segment.endPosition <= max) {
                    segment.setDownloaded();
                }
                if (!segment.isFinished())
                    return;
                max = Math.max(max, segment.startPosition + segment.downloaded);
            }

            // All segments have finished downloading.
            state.finished = true;
            future.complete(null);
        }

        @Override
        public final CompletableFuture<T> getCompletableFuture() {
            return CompletableFuture.runAsync(AsyncTaskExecutor.wrap(() -> {
                downloader = createDownloader();
                checkETag = downloader.shouldCheckETag();

                downloader.onStart();

                for (DownloadSegment segment : state.segments)
                    segment.download(this, downloader);
            }))
                    .thenCompose(unused -> future)
                    .whenComplete((unused, exception) -> {
                        if (doFinish) {
                            try {
                                setResult(downloader.finish());
                            } catch (IOException e) {
                                throw new CompletionException(e);
                            }
                        }
                        AsyncTaskExecutor.rethrow(exception);
                    })
                    .thenApplyAsync(unused -> getResult());
        }

        protected enum EnumCheckETag {
            CHECK_E_TAG,
            NOT_CHECK_E_TAG,
            CACHED
        }
    }

    protected static final class DownloadTaskState {
        private final List<URL> urls;
        @Nullable
        private final Path file;
        private final List<DownloadSegment> segments;
        private URL fastestUrl;
        private boolean segmentSupported = false;
        private final int retry;
        private int retryUrl = 0;
        private boolean cancelled = false;
        private boolean finished = false;
        private int contentLength;
        private final int initialParts;
        private boolean waitingForContentLength;

        private final SafeRegion connectionCheckRegion = new SafeRegion();
        private final SafeRegion writeRegion = new SafeRegion();

        DownloadTaskState(DownloadState state, @NotNull Path file, int retry) {
            this.urls = new ArrayList<>(state.urls);
            this.file = file;
            this.retry = retry;
            this.segments = new ArrayList<>(state.segments);
            this.contentLength = state.getContentLength();
            this.initialParts = state.getSegments().size();
            this.waitingForContentLength = false;
        }

        DownloadTaskState(List<URL> urls, @Nullable Path file, int retry, int contentLength, int initialParts) {
            if (urls == null || urls.size() == 0) {
                throw new IllegalArgumentException("DownloadTaskState requires at least one url candidate");
            }

            this.urls = new ArrayList<>(urls);
            this.file = file;
            this.retry = retry;
            this.initialParts = initialParts;
            this.segments = new ArrayList<>(initialParts);
            int partLength = contentLength / initialParts;
            for (int i = 0; i < initialParts; i++) {
                int begin = partLength * i;
                int end = Math.min((partLength + 1) * i, contentLength);
                segments.add(new DownloadSegment(begin, end, 0));
            }
            this.waitingForContentLength = contentLength == 0;
        }

        public static DownloadTaskState newWithLengthUnknown(List<URL> urls, Path file, int retry, int initialParts) {
            return new DownloadTaskState(urls, file, retry, 0, initialParts);
        }

        public synchronized List<URL> getUrls() {
            return urls;
        }

        public Path getFile() {
            return file;
        }

        public Path getDownloadingFile() {
            return file == null ? null : file.resolveSibling(FileUtils.getName(file) + ".download");
        }

        public Path getStateFile() {
            return file == null ? null : file.resolveSibling(FileUtils.getName(file) + ".status");
        }

        public List<DownloadSegment> getSegments() {
            return segments;
        }

        /**
         * Figure out actually content length
         * @param contentLength new content length
         * @return if content length changed
         */
        protected synchronized boolean setContentLength(int contentLength) {
            if (!waitingForContentLength) {
                throw new IllegalStateException("ContentLength already set");
            }

            if (this.contentLength == contentLength) {
                return false;
            }

            waitingForContentLength = false;

            this.contentLength = contentLength;
            if (contentLength < 0) {
                return true;
            }

            int partLength = contentLength / initialParts;
            for (int i = 0; i < segments.size(); i++) {
                int begin = partLength * i;
                int end = Math.min(partLength * (i + 1), contentLength);
                segments.get(i).setDownloadRange(begin, end);
            }
            return true;
        }

        public synchronized int getContentLength() {
            return contentLength;
        }

        public boolean isWaitingForContentLength() {
            return waitingForContentLength;
        }

        public synchronized boolean isSegmentSupported() {
            return segmentSupported;
        }

        public synchronized void setSegmentSupported(boolean segmentSupported) {
            this.segmentSupported = segmentSupported;
        }

        public synchronized URL getFirstUrl() {
            return urls.get(0);
        }

        public synchronized boolean isFinished() {
            return finished;
        }

        protected synchronized void setFinished(boolean finished) {
            this.finished = finished;
        }

        /**
         * Next url for download runnable to retry.
         *
         * If some download runnable fails to connect to url, it will call this method
         * to acquire next url for retry. Making all download runnable try different
         * candidates concurrently to speed up finding fastest download source.
         *
         * If all URLs are tried and tested definitely negative for downloading,
         * returns null.
         *
         * @return next url to retry
         */
        @Nullable
        public synchronized URL getNextUrlToRetry() {
            if (retryUrl < 0 || retryUrl >= urls.size()) return null;
            URL url = urls.get(retryUrl);
            retryUrl = (retryUrl + 1) % urls.size();
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
        public synchronized URL getFastestUrl() {
            return fastestUrl;
        }

        public synchronized void setFastestUrl(URL fastestUrl) {
            if (this.fastestUrl == null)
                this.fastestUrl = fastestUrl;
        }

        public synchronized void cancel() {
            cancelled = true;
        }

        public synchronized boolean isCancelled() {
            return cancelled;
        }

        public SafeRegion checkingConnection() {
            connectionCheckRegion.begin();
            return connectionCheckRegion;
        }

        public SafeRegion writing() {
            writeRegion.begin();
            return writeRegion;
        }

        public synchronized void forbidURL(URL url) {
            int index;
            while ((index = urls.indexOf(url)) != -1) {
                if (retryUrl >= index) {
                    retryUrl--;
                }
                urls.remove(index);
            }
        }
    }

    protected static final class DownloadState {
        private final List<URL> urls;
        private final List<DownloadSegment> segments;
        private final int contentLength;

        /**
         * Constructor for Gson
         */
        public DownloadState() {
            this(Collections.emptyList(), Collections.emptyList(), 0);
        }

        public DownloadState(List<URL> urls, List<DownloadSegment> segments, int contentLength) {
            this.urls = urls;
            this.segments = segments;
            this.contentLength = contentLength;
        }

        public List<URL> getUrls() {
            return urls;
        }

        public List<DownloadSegment> getSegments() {
            return segments;
        }

        public int getContentLength() {
            return contentLength;
        }
    }

    protected static final class DownloadSegment {
        private int startPosition;
        private int endPosition;
        private int downloaded;
        private boolean finished;
        private URLConnection connection;
        private Future<?> future;

        /**
         * Constructor for Gson
         */
        public DownloadSegment() {
            this(0, 0, 0);
        }

        public DownloadSegment(int startPosition, int endPosition, int downloaded) {
            if (downloaded > endPosition - startPosition) {
                throw new IllegalArgumentException("Illegal download state: start " + startPosition + ", end " + endPosition + ", total downloaded " + downloaded);
            }
            this.startPosition = startPosition;
            this.endPosition = endPosition;
            this.downloaded = downloaded;
        }

        public int getStartPosition() {
            return startPosition;
        }

        public int getEndPosition() {
            return endPosition;
        }

        public void setDownloadRange(int start, int end) {
            this.startPosition = start;
            this.endPosition = end;
            this.downloaded = 0;
        }

        public int getDownloaded() {
            return downloaded;
        }

        public void setDownloaded() {
            this.downloaded = endPosition - startPosition;
        }

        public void setDownloaded(int downloaded) {
            this.downloaded = downloaded;
        }

        public int getLength() {
            return endPosition - startPosition;
        }

        public URLConnection getConnection() {
            return connection;
        }

        protected void setConnection(URLConnection connection) {
            this.connection = connection;
        }

        public boolean isFinished() {
            return finished || downloaded >= getLength();
        }

        public Future<?> download(DownloadTask<?> task, Downloader<?> downloader) {
            if (future == null) {
                future = DownloadManager.download().submit(new DownloadSegmentTask(task, downloader, this));
            }
            return future;
        }
    }

    private static final Timer timer = new Timer("DownloadSpeedRecorder", true);
    private static final AtomicInteger downloadSpeed = new AtomicInteger(0);
    public static final EventBus speedEvent = new EventBus();

    static {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                speedEvent.channel(SpeedEvent.class).fireEvent(new SpeedEvent(speedEvent, downloadSpeed.getAndSet(0)));
            }
        }, 0, 1000);
    }

    public static void updateDownloadSpeed(int speed) {
        downloadSpeed.addAndGet(speed);
    }

    public static class SpeedEvent extends Event {
        private final int speed;

        public SpeedEvent(Object source, int speed) {
            super(source);

            this.speed = speed;
        }

        /**
         * Download speed in byte/sec.
         * @return download speed
         */
        public int getSpeed() {
            return speed;
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this).append("speed", speed).toString();
        }
    }

    private static int downloadExecutorConcurrency = Math.min(Runtime.getRuntime().availableProcessors() * 4, 64);
    private static volatile ThreadPoolExecutor DOWNLOAD_EXECUTOR;

    /**
     * Get singleton instance of the thread pool for file downloading.
     *
     * @return Thread pool for FetchTask
     */
    protected static ExecutorService download() {
        if (DOWNLOAD_EXECUTOR == null) {
            synchronized (Schedulers.class) {
                if (DOWNLOAD_EXECUTOR == null) {
                    DOWNLOAD_EXECUTOR = new ThreadPoolExecutor(0, downloadExecutorConcurrency, 10, TimeUnit.SECONDS, new SynchronousQueue<>(),
                            runnable -> {
                                Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                                thread.setDaemon(true);
                                return thread;
                            });
                }
            }
        }

        return DOWNLOAD_EXECUTOR;
    }

    public static void setDownloadExecutorConcurrency(int concurrency) {
        synchronized (Schedulers.class) {
            downloadExecutorConcurrency = concurrency;
            if (DOWNLOAD_EXECUTOR != null) {
                DOWNLOAD_EXECUTOR.setMaximumPoolSize(concurrency);
            }
        }
    }

    public static int getDownloadExecutorConcurrency() {
        synchronized (Schedulers.class) {
            return downloadExecutorConcurrency;
        }
    }
}
