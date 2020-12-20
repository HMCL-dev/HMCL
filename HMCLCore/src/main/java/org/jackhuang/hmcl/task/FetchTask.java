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

import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public abstract class FetchTask<T> extends Task<T> {
    protected final List<URL> urls;
    protected final int retry;
    protected boolean caching;
    protected CacheRepository repository = CacheRepository.getInstance();

    public FetchTask(List<URL> urls, int retry) {
        if (urls == null || urls.isEmpty())
            throw new IllegalArgumentException("At least one URL is required");

        this.urls = new ArrayList<>(urls);
        this.retry = retry;

        setExecutor(download());
    }

    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    public void setCacheRepository(CacheRepository repository) {
        this.repository = repository;
    }

    protected void beforeDownload(URL url) throws IOException {}

    protected abstract void useCachedResult(Path cachedFile) throws IOException;

    protected abstract EnumCheckETag shouldCheckETag();

    protected abstract Context getContext(URLConnection conn, boolean checkETag) throws IOException;

    @Override
    public void execute() throws Exception {
        Exception exception = null;
        URL failedURL = null;
        boolean checkETag;
        switch (shouldCheckETag()) {
            case CHECK_E_TAG: checkETag = true; break;
            case NOT_CHECK_E_TAG: checkETag = false; break;
            default: return;
        }

        int repeat = 0;
        download: for (URL url : urls) {
            for (int retryTime = 0; retryTime < retry; retryTime++) {
                if (isCancelled()) {
                    break download;
                }

                try {
                    beforeDownload(url);

                    updateProgress(0);

                    URLConnection conn = NetworkUtils.createConnection(url);
                    if (checkETag) repository.injectConnection(conn);

                    if (conn instanceof HttpURLConnection) {
                        conn = NetworkUtils.resolveConnection((HttpURLConnection) conn);
                        int responseCode = ((HttpURLConnection) conn).getResponseCode();

                        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            // Handle cache
                            try {
                                Path cache = repository.getCachedRemoteFile(conn);
                                useCachedResult(cache);
                                return;
                            } catch (IOException e) {
                                Logging.LOG.log(Level.WARNING, "Unable to use cached file, redownload " + url, e);
                                repository.removeRemoteEntry(conn);
                                // Now we must reconnect the server since 304 may result in empty content,
                                // if we want to redownload the file, we must reconnect the server without etag settings.
                                retryTime--;
                                continue;
                            }
                        } else if (responseCode / 100 == 4) {
                            break; // we will not try this URL again
                        } else if (responseCode / 100 != 2) {
                            throw new ResponseCodeException(url, responseCode);
                        }
                    }

                    long contentLength = conn.getContentLength();
                    try (Context context = getContext(conn, checkETag); InputStream stream = conn.getInputStream()) {
                        int lastDownloaded = 0, downloaded = 0;
                        byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                        while (true) {
                            if (isCancelled()) break;

                            int len = stream.read(buffer);
                            if (len == -1) break;

                            context.write(buffer, 0, len);

                            downloaded += len;

                            if (contentLength >= 0) {
                                // Update progress information per second
                                updateProgress(downloaded, contentLength);
                            }

                            updateDownloadSpeed(downloaded - lastDownloaded);
                            lastDownloaded = downloaded;
                        }

                        if (isCancelled()) break download;

                        updateDownloadSpeed(downloaded - lastDownloaded);

                        if (contentLength >= 0 && downloaded != contentLength)
                            throw new IOException("Unexpected file size: " + downloaded + ", expected: " + contentLength);

                        context.withResult(true);
                    }

                    return;
                } catch (IOException ex) {
                    failedURL = url;
                    exception = ex;
                    Logging.LOG.log(Level.WARNING, "Failed to download " + url + ", repeat times: " + (++repeat), ex);
                }
            }
        }

        if (exception != null)
            throw new DownloadException(failedURL, exception);
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

    private static void updateDownloadSpeed(int speed) {
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

    protected static abstract class Context implements Closeable {
        private boolean success;

        public abstract void write(byte[] buffer, int offset, int len) throws IOException;

        public final void withResult(boolean success) {
            this.success = success;
        }

        protected boolean isSuccess() {
            return success;
        }
    }

    protected enum EnumCheckETag {
        CHECK_E_TAG,
        NOT_CHECK_E_TAG,
        CACHED
    }
    
    protected class DownloadState {
        private final int startPosition;
        private final int endPosition;
        private final int currentPosition;
        private final boolean finished;

        public DownloadState(int startPosition, int endPosition, int currentPosition) {
            if (currentPosition < startPosition || currentPosition > endPosition) {
                throw new IllegalArgumentException("Illegal download state: start " + startPosition + ", end " + endPosition + ", cur " + currentPosition);
            }
            this.startPosition = startPosition;
            this.endPosition = endPosition;
            this.currentPosition = currentPosition;
            finished = currentPosition == endPosition;
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

        public boolean isFinished() {
            return finished;
        }
    }

    protected class DownloadMission {



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
                    ThreadPoolExecutor executor = new ThreadPoolExecutor(downloadExecutorConcurrency, downloadExecutorConcurrency, 10, TimeUnit.SECONDS,
                            new ArrayBlockingQueue<>(downloadExecutorConcurrency),
                            runnable -> {
                                Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                                thread.setDaemon(true);
                                return thread;
                            });
                    executor.allowCoreThreadTimeOut(true);
                    DOWNLOAD_EXECUTOR = executor;
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
