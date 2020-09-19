package org.jackhuang.hmcl.task;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
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

        public DownloadTaskState build() throws IOException {
            if (file == null) {
                return DownloadTaskState.newWithLengthUnknown(urls, Files.createTempFile(null, null), retry, initialParts);
            }

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

    protected static abstract class DownloadTask<T> extends CompletableFutureTask<T> {
        protected final DownloadTaskState state;
        protected boolean caching = false;
        protected CacheRepository repository = CacheRepository.getInstance();
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private EnumCheckETag checkETag;

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

        protected abstract void write(byte[] buffer, int offset, int len) throws IOException;

        protected EnumCheckETag shouldCheckETag() {
            return EnumCheckETag.NOT_CHECK_E_TAG;
        }

        protected final EnumCheckETag getCheckETag() { return checkETag; }

        protected void onBeforeConnection(URL url) {}

        protected abstract void onStart() throws IOException;

        /**
         * Make cached file as result of this task.
         *
         * @param cachedFile verified cached file
         * @throws IOException if an I/O error occurred.
         */
        protected void finishWithCachedResult(Path cachedFile) throws IOException {
            state.finished = true;

            future.complete(getResult());
        }

        public void finish() throws IOException {
            state.finished = true;

            future.complete(getResult());
        }

        @Override
        public final CompletableFuture<T> getCompletableFuture() {
            return CompletableFuture.runAsync(() -> {
                checkETag = shouldCheckETag();

                for (Runnable runnable : state.threads)
                    download().submit(runnable);
            }).thenCompose(unused -> future);
        }

        protected enum EnumCheckETag {
            CHECK_E_TAG,
            NOT_CHECK_E_TAG,
            CACHED
        }
    }

    protected static final class DownloadTaskState {
        private final List<URL> urls;
        private final Path file;
        private final List<DownloadSegment> segments;
        private final List<Runnable> threads;
        private URL fastestUrl;
        private final int retry;
        private int retryUrl = 0;
        private boolean cancelled = false;
        private boolean finished = false;
        private int contentLength;
        private final int initialParts;

        private final SafeRegion connectionCheckRegion = new SafeRegion();
        private final SafeRegion writeRegion = new SafeRegion();

        DownloadTaskState(DownloadState state, Path file, int retry) {
            this.urls = new ArrayList<>(state.urls);
            this.file = file;
            this.retry = retry;
            this.segments = new ArrayList<>(state.segments);
            this.threads = IntStream.range(0, state.segments.size()).mapToObj(x -> (Thread) null).collect(Collectors.toList());
            this.contentLength = state.getContentLength();
            this.initialParts = state.getSegments().size();
        }

        DownloadTaskState(List<URL> urls, Path file, int retry, int contentLength, int initialParts) {
            if (urls == null || urls.size() == 0) {
                throw new IllegalArgumentException("DownloadTaskState requires at least one url candidate");
            }

            this.urls = new ArrayList<>(urls);
            this.file = file;
            this.retry = retry;
            this.initialParts = initialParts;
            this.segments = new ArrayList<>(initialParts);
            this.threads = new ArrayList<>(initialParts);
            int partLength = contentLength / initialParts;
            for (int i = 0; i < initialParts; i++) {
                int begin = partLength * i;
                int end = Math.min((partLength + 1) * i, contentLength);
                segments.add(new DownloadSegment(begin, end, 0));
                threads.add(null);
            }
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
            return file.resolveSibling(FileUtils.getName(file) + ".download");
        }

        public Path getStateFile() {
            return file.resolveSibling(FileUtils.getName(file) + ".status");
        }

        public List<DownloadSegment> getSegments() {
            return segments;
        }

        protected synchronized void setContentLength(int contentLength) {
            if (this.contentLength != 0) {
                throw new IllegalStateException("ContentLength already set");
            }

            this.contentLength = contentLength;
            if (contentLength < 0) {
                return;
            }

            int partLength = contentLength / initialParts;
            for (int i = 0; i < segments.size(); i++) {
                int begin = partLength * i;
                int end = Math.min((partLength + 1) * i, contentLength);
                segments.get(i).setDownloadRange(begin, end);
            }
        }

        public synchronized int getContentLength() {
            return contentLength;
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
        private URLConnection connection;

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
            return downloaded == getLength();
        }

        public boolean isWaiting() { return startPosition == endPosition && startPosition == 0; }
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
    private static volatile ExecutorService DOWNLOAD_EXECUTOR;

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
                DOWNLOAD_EXECUTOR.shutdownNow();
                DOWNLOAD_EXECUTOR = null;
            }
        }
    }

    public static int getDownloadExecutorConcurrency() {
        synchronized (Schedulers.class) {
            return downloadExecutorConcurrency;
        }
    }
}
