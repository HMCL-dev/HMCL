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
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.io.ContentEncoding;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.jackhuang.hmcl.util.Lang.threadPool;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public abstract class FetchTask<T> extends Task<T> {
    protected static final int DEFAULT_RETRY = 3;

    protected final List<URI> uris;
    protected int retry = DEFAULT_RETRY;
    protected CacheRepository repository = CacheRepository.getInstance();

    public FetchTask(@NotNull List<@NotNull URI> uris) {
        Objects.requireNonNull(uris);

        this.uris = List.copyOf(uris);

        if (this.uris.isEmpty())
            throw new IllegalArgumentException("At least one URL is required");

        setExecutor(download());
    }

    public void setRetry(int retry) {
        if (retry <= 0)
            throw new IllegalArgumentException("Retry count must be greater than 0");

        this.retry = retry;
    }

    public void setCacheRepository(CacheRepository repository) {
        this.repository = repository;
    }

    protected void beforeDownload(URI uri) throws IOException {
    }

    protected abstract void useCachedResult(Path cachedFile) throws IOException;

    protected abstract EnumCheckETag shouldCheckETag();

    protected abstract Context getContext(URLConnection connection, boolean checkETag, String bmclapiHash) throws IOException;

    @Override
    public void execute() throws Exception {
        Exception exception = null;
        URI failedURI = null;
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
                String bmclapiHash = null;
                try {
                    beforeDownload(uri);
                    updateProgress(0);

                    URLConnection conn = NetworkUtils.createConnection(uri);

                    if (conn instanceof HttpURLConnection) {
                        var httpConnection = (HttpURLConnection) conn;
                        httpConnection.setRequestProperty("Accept-Encoding", "gzip");

                        if (checkETag) repository.injectConnection(httpConnection);
                        Map<String, List<String>> requestProperties = httpConnection.getRequestProperties();

                        bmclapiHash = httpConnection.getHeaderField("x-bmclapi-hash");
                        if (DigestUtils.isSha1Digest(bmclapiHash)) {
                            Optional<Path> cache = repository.checkExistentFile(null, "SHA-1", bmclapiHash);
                            if (cache.isPresent()) {
                                useCachedResult(cache.get());
                                LOG.info("Using cached file for " + NetworkUtils.dropQuery(uri));
                                return;
                            }
                        } else {
                            bmclapiHash = null;
                        }

                        while (true) {
                            int code = httpConnection.getResponseCode();
                            if (code >= 300 && code <= 308 && code != 306 && code != 304) {
                                if (redirects == null) {
                                    redirects = new ArrayList<>();
                                } else if (redirects.size() >= 20) {
                                    httpConnection.disconnect();
                                    throw new IOException("Too much redirects");
                                }

                                String location = httpConnection.getHeaderField("Location");

                                httpConnection.disconnect();
                                if (StringUtils.isBlank(location))
                                    throw new IOException("Redirected to an empty location");

                                URI target = NetworkUtils.toURI(location).resolve(NetworkUtils.toURI(location));
                                redirects.add(target);

                                if (!NetworkUtils.isHttpUri(target))
                                    throw new IOException("Redirected to not http URI: " + target);

                                HttpURLConnection redirected = NetworkUtils.createHttpConnection(target);
                                redirected.setUseCaches(checkETag);
                                requestProperties
                                        .forEach((key, value) -> value.forEach(element ->
                                                redirected.addRequestProperty(key, element)));
                                httpConnection = redirected;
                            } else {
                                break;
                            }
                        }
                        conn = httpConnection;
                        int responseCode = ((HttpURLConnection) conn).getResponseCode();

                        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            // Handle cache
                            try {
                                Path cache = repository.getCachedRemoteFile(NetworkUtils.toURI(conn.getURL()));
                                useCachedResult(cache);
                                LOG.info("Using cached file for " + NetworkUtils.dropQuery(uri));
                                return;
                            } catch (IOException e) {
                                LOG.warning("Unable to use cached file, redownload " + NetworkUtils.dropQuery(uri), e);
                                repository.removeRemoteEntry(conn.getURL().toURI());
                                // Now we must reconnect the server since 304 may result in empty content,
                                // if we want to redownload the file, we must reconnect the server without etag settings.
                                retryTime--;
                                continue;
                            }
                        } else if (responseCode / 100 == 4) {
                            throw new FileNotFoundException(uri.toString());
                        } else if (responseCode / 100 != 2) {
                            throw new ResponseCodeException(uri, responseCode);
                        }
                    }

                    long contentLength = conn.getContentLength();
                    var encoding = ContentEncoding.fromConnection(conn);
                    try (var context = getContext(conn, checkETag, bmclapiHash);
                         var counter = new CounterInputStream(conn.getInputStream());
                         var input = encoding.wrap(counter)) {
                        byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                        while (true) {
                            if (isCancelled()) break;

                            int len = input.read(buffer);
                            if (len == -1) break;

                            context.write(buffer, 0, len);

                            if (contentLength >= 0) {
                                // Update progress information per second
                                updateProgress(counter.downloaded, contentLength);
                            }

                            updateDownloadSpeed(counter.lastRead);
                        }

                        if (isCancelled()) break download;

                        updateDownloadSpeed(counter.lastRead);

                        if (contentLength >= 0 && counter.downloaded != contentLength)
                            throw new IOException("Unexpected file size: " + counter.downloaded + ", expected: " + contentLength);

                        context.withResult(true);
                    }

                    return;
                } catch (FileNotFoundException ex) {
                    failedURI = uri;
                    exception = ex;
                    LOG.warning("Failed to download " + uri + ", not found" + (redirects == null ? "" : ", redirects: " + redirects), ex);

                    break; // we will not try this URL again
                } catch (IOException ex) {
                    failedURI = uri;
                    exception = ex;
                    LOG.warning("Failed to download " + uri + ", repeat times: " + (++repeat) + (redirects == null ? "" : ", redirects: " + redirects), ex);
                }
            }
        }

        if (exception != null)
            throw new DownloadException(failedURI, exception);
    }

    private static final Timer timer = new Timer("DownloadSpeedRecorder", true);
    private static final AtomicLong downloadSpeed = new AtomicLong(0L);
    public static final EventBus speedEvent = new EventBus();

    static {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                speedEvent.channel(SpeedEvent.class).fireEvent(new SpeedEvent(speedEvent, downloadSpeed.getAndSet(0)));
            }
        }, 0, 1000);
    }

    private static void updateDownloadSpeed(long speed) {
        downloadSpeed.addAndGet(speed);
    }

    private static class CounterInputStream extends FilterInputStream {
        long downloaded;
        long lastRead;

        CounterInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                downloaded++;
                lastRead = 1;
            } else {
                lastRead = 0;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0) {
                downloaded += n;
                lastRead = n;
            } else {
                lastRead = 0;
            }
            return n;
        }
    }

    public static class SpeedEvent extends Event {
        private final long speed;

        public SpeedEvent(Object source, long speed) {
            super(source);

            this.speed = speed;
        }

        /**
         * Download speed in byte/sec.
         *
         * @return download speed
         */
        public long getSpeed() {
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
