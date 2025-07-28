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
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.util.Lang.threadPool;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public abstract class FetchTask2<T> extends Task<T> {
    protected final List<URI> uris;
    protected final int retry;
    protected boolean caching;
    protected CacheRepository repository = CacheRepository.getInstance();
    protected HttpClient httpClient = NetworkUtils.HTTP_CLIENT;

    public FetchTask2(@NotNull List<@NotNull URI> uris, int retry) {
        Objects.requireNonNull(uris);

        this.uris = List.copyOf(uris);
        this.retry = retry;

        if (this.uris.isEmpty())
            throw new IllegalArgumentException("At least one URL is required");

        setExecutor(download());
    }

    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    public void setCacheRepository(CacheRepository repository) {
        this.repository = repository;
    }

    protected void beforeDownload(URI url) throws IOException {
    }

    protected abstract void useCachedResult(Path cachedFile) throws IOException;

    protected abstract EnumCheckETag shouldCheckETag();

    protected abstract Context getContext(@Nullable HttpResponse<?> response, boolean checkETag) throws IOException;

    @Override
    public void execute() throws Exception {
        Exception exception = null;
        URI failedURL = null;
        boolean checkETag;
        switch (shouldCheckETag()) {
            case CHECK_E_TAG:
                checkETag = true;
                break;
            case NOT_CHECK_E_TAG:
                checkETag = false;
                break;
            default:
                return;
        }

        int repeat = 0;
        download:
        for (URI uri : uris) {
            for (int retryTime = 0; retryTime < retry; retryTime++) {
                if (isCancelled()) {
                    break download;
                }

                List<URI> redirects = null;
                try {
                    beforeDownload(uri);

                    updateProgress(0);

                    long contentLength;
                    Context context;
                    InputStream stream;
                    if (NetworkUtils.isHttpUri(uri)) {
                        var builder = HttpRequest.newBuilder(uri);
                        if (checkETag) repository.injectRequest(uri, builder);
                        redirects = new ArrayList<>();

                        var response = NetworkUtils.resolveResponse(
                                httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream()),
                                HttpResponse.BodyHandlers.ofInputStream(), redirects);
                        stream = response.body();

                        if (response.statusCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            IOUtils.closeQuietly(stream);
                            // Handle cache
                            try {
                                Path cache = repository.getCachedRemoteFile(response.uri());
                                useCachedResult(cache);
                                return;
                            } catch (IOException e) {
                                LOG.warning("Unable to use cached file, redownload " + uri, e);
                                repository.removeRemoteEntry(response.uri());
                                // Now we must reconnect the server since 304 may result in empty content,
                                // if we want to redownload the file, we must reconnect the server without etag settings.
                                retryTime--;
                                continue;
                            }
                        } else if (response.statusCode() / 100 == 4) {
                            IOUtils.closeQuietly(stream);
                            throw new FileNotFoundException(uri.toString());
                        } else if (response.statusCode() / 100 != 2) {
                            IOUtils.closeQuietly(stream);
                            throw new ResponseCodeException(uri, response.statusCode());
                        }

                        contentLength = Long.parseLong(response.headers().firstValue("Content-Length")
                                .orElseThrow());
                        context = getContext(response, checkETag);
                    } else {
                        URLConnection conn = NetworkUtils.createConnection(uri.toURL());
                        contentLength = conn.getContentLength();
                        context = getContext(null, false);
                        stream = conn.getInputStream();
                    }

                    try (context; stream) {
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
                } catch (FileNotFoundException ex) {
                    failedURL = uri;
                    exception = ex;
                    LOG.warning("Failed to download " + uri + ", not found" + ((redirects == null || redirects.isEmpty()) ? "" : ", redirects: " + redirects), ex);

                    break; // we will not try this URL again
                } catch (IOException ex) {
                    failedURL = uri;
                    exception = ex;
                    LOG.warning("Failed to download " + uri + ", repeat times: " + (++repeat) + ((redirects == null || redirects.isEmpty()) ? "" : ", redirects: " + redirects), ex);
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
         *
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

    protected static final class DownloadState {
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

    protected static final class DownloadMission {

    }

    public static int DEFAULT_CONCURRENCY = Math.min(Runtime.getRuntime().availableProcessors() * 4, 64);
    private static int downloadExecutorConcurrency = DEFAULT_CONCURRENCY;
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
                    DOWNLOAD_EXECUTOR = threadPool("Download", true, downloadExecutorConcurrency, 10, TimeUnit.SECONDS);
                }
            }
        }

        return DOWNLOAD_EXECUTOR;
    }

    public static void setDownloadExecutorConcurrency(int concurrency) {
        concurrency = Math.max(concurrency, 1);
        synchronized (Schedulers.class) {
            downloadExecutorConcurrency = concurrency;

            ThreadPoolExecutor downloadExecutor = DOWNLOAD_EXECUTOR;
            if (downloadExecutor != null) {
                if (downloadExecutor.getMaximumPoolSize() <= concurrency) {
                    downloadExecutor.setMaximumPoolSize(concurrency);
                    downloadExecutor.setCorePoolSize(concurrency);
                } else {
                    downloadExecutor.setCorePoolSize(concurrency);
                    downloadExecutor.setMaximumPoolSize(concurrency);
                }
            }
        }
    }

    public static int getDownloadExecutorConcurrency() {
        synchronized (Schedulers.class) {
            return downloadExecutorConcurrency;
        }
    }
}
