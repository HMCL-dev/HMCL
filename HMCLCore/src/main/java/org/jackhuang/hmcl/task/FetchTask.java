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
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.io.ContentEncoding;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Lang.threadPool;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public abstract class FetchTask<T> extends Task<T> {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(NetworkUtils.TIME_OUT))
            .build();

    protected static final int DEFAULT_RETRY = 3;

    protected final List<URI> uris;
    protected int retry = DEFAULT_RETRY;
    protected CacheRepository repository = CacheRepository.getInstance();

    public FetchTask(@NotNull List<@NotNull URI> uris) {
        Objects.requireNonNull(uris);

        this.uris = List.copyOf(uris);

        if (this.uris.isEmpty())
            throw new IllegalArgumentException("At least one URL is required");

        setExecutor(DOWNLOAD_EXECUTOR);
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

    private Context getContext() throws IOException {
        return getContext(null, false, null);
    }

    protected abstract Context getContext(@Nullable HttpResponse<?> response, boolean checkETag, String bmclapiHash) throws IOException;

    private URI failedURI;
    private Exception exception;

    @Override
    public void execute() throws Exception {
        exception = null;

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

        SEMAPHORE.acquire();
        try {
            for (URI uri : uris) {
                var result = NetworkUtils.isHttpUri(uri)
                        ? downloadHttp(uri, checkETag)
                        : downloadNotHttp(uri);
                if (result)
                    return;
            }
        } catch (InterruptedException ignored) {
            // Cancelled
        } finally {
            SEMAPHORE.release();
        }

        if (exception != null)
            throw new DownloadException(failedURI, exception);
    }

    private void download(Context context,
                          InputStream inputStream,
                          long contentLength,
                          ContentEncoding contentEncoding) throws IOException, InterruptedException {
        try (var ignored = context;
             var counter = new CounterInputStream(inputStream);
             var input = contentEncoding.wrap(counter)) {
            long lastDownloaded = 0L;
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

                updateDownloadSpeed(counter.downloaded - lastDownloaded);
                lastDownloaded = counter.downloaded;
            }

            if (isCancelled())
                throw new InterruptedException();

            updateDownloadSpeed(counter.downloaded - lastDownloaded);

            if (contentLength >= 0 && counter.downloaded != contentLength)
                throw new IOException("Unexpected file size: " + counter.downloaded + ", expected: " + contentLength);

            context.withResult(true);
        }
    }

    private boolean downloadHttp(URI uri, boolean checkETag) throws InterruptedException {
        if (checkETag) {
            // Handle cache
            try {
                Path cache = repository.getCachedRemoteFile(uri, true);
                useCachedResult(cache);
                LOG.info("Using cached file for " + NetworkUtils.dropQuery(uri));
                return true;
            } catch (IOException ignored) {
            }
        }

        for (int retryTime = 0; retryTime < retry; retryTime++) {
            if (isCancelled()) {
                throw new InterruptedException();
            }

            List<URI> redirects = null;
            try {
                beforeDownload(uri);
                updateProgress(0);

                HttpResponse<InputStream> response;
                String bmclapiHash;

                URI currentURI = uri;

                LinkedHashMap<String, String> headers = new LinkedHashMap<>();
                headers.put("accept-encoding", "gzip");
                if (checkETag)
                    headers.putAll(repository.injectConnection(uri));

                do {
                    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(currentURI);
                    headers.forEach(requestBuilder::header);
                    response = HTTP_CLIENT.send(requestBuilder.build(), BODY_HANDLER);

                    bmclapiHash = response.headers().firstValue("x-bmclapi-hash").orElse(null);
                    if (DigestUtils.isSha1Digest(bmclapiHash)) {
                        Optional<Path> cache = repository.checkExistentFile(null, "SHA-1", bmclapiHash);
                        if (cache.isPresent()) {
                            useCachedResult(cache.get());
                            LOG.info("Using cached file for " + NetworkUtils.dropQuery(uri));
                            return true;
                        }
                    }

                    int code = response.statusCode();
                    if (code >= 300 && code <= 308 && code != 306 && code != 304) {
                        if (redirects == null) {
                            redirects = new ArrayList<>();
                        } else if (redirects.size() >= 20) {
                            throw new IOException("Too much redirects");
                        }

                        String location = response.headers().firstValue("Location").orElse(null);
                        if (StringUtils.isBlank(location))
                            throw new IOException("Redirected to an empty location");

                        URI target = currentURI.resolve(location);
                        redirects.add(target);

                        if (!NetworkUtils.isHttpUri(target))
                            throw new IOException("Redirected to not http URI: " + target);

                        currentURI = target;
                    } else {
                        break;
                    }
                } while (true);

                int responseCode = response.statusCode();
                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    // Handle cache
                    try {
                        Path cache = repository.getCachedRemoteFile(currentURI, false);
                        useCachedResult(cache);
                        LOG.info("Using cached file for " + NetworkUtils.dropQuery(uri));
                        return true;
                    } catch (CacheRepository.CacheExpiredException e) {
                        LOG.info("Cache expired for " + NetworkUtils.dropQuery(uri));
                    } catch (IOException e) {
                        LOG.warning("Unable to use cached file, redownload " + NetworkUtils.dropQuery(uri), e);
                        repository.removeRemoteEntry(currentURI);
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

                long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
                var contentEncoding = ContentEncoding.fromResponse(response);

                download(getContext(response, checkETag, bmclapiHash),
                        response.body(),
                        contentLength,
                        contentEncoding);
                return true;
            } catch (FileNotFoundException ex) {
                failedURI = uri;
                exception = ex;
                LOG.warning("Failed to download " + uri + ", not found" + (redirects == null ? "" : ", redirects: " + redirects), ex);

                break; // we will not try this URL again
            } catch (IOException ex) {
                failedURI = uri;
                exception = ex;
                LOG.warning("Failed to download " + uri + ", repeat times: " + retryTime + (redirects == null ? "" : ", redirects: " + redirects), ex);
            }
        }

        return false;
    }

    private boolean downloadNotHttp(URI uri) throws InterruptedException {
        for (int retryTime = 0; retryTime < retry; retryTime++) {
            if (isCancelled()) {
                throw new InterruptedException();
            }

            try {
                beforeDownload(uri);
                updateProgress(0);

                URLConnection conn = NetworkUtils.createConnection(uri);
                download(getContext(),
                        conn.getInputStream(),
                        conn.getContentLengthLong(),
                        ContentEncoding.fromConnection(conn));
                return true;
            } catch (FileNotFoundException ex) {
                failedURI = uri;
                exception = ex;
                LOG.warning("Failed to download " + uri + ", not found", ex);

                break; // we will not try this URL again
            } catch (IOException ex) {
                failedURI = uri;
                exception = ex;
                LOG.warning("Failed to download " + uri + ", repeat times: " + retryTime, ex);
            }
        }

        return false;
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

    private static final class CounterInputStream extends FilterInputStream {
        long downloaded;

        CounterInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0)
                downloaded++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n >= 0)
                downloaded += n;
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

    private static final HttpResponse.BodyHandler<InputStream> BODY_HANDLER = responseInfo -> {
        if (responseInfo.statusCode() / 2 == 100)
            return HttpResponse.BodySubscribers.ofInputStream();
        else
            return HttpResponse.BodySubscribers.replacing(null);
    };

    public static int DEFAULT_CONCURRENCY = Math.min(Runtime.getRuntime().availableProcessors() * 4, 64);
    private static final Semaphore SEMAPHORE = new Semaphore(DEFAULT_CONCURRENCY);
    private static int downloadExecutorConcurrency = DEFAULT_CONCURRENCY;

    protected static final ExecutorService DOWNLOAD_EXECUTOR;

    static {
        ExecutorService executorService = null;

        if (JavaRuntime.CURRENT_VERSION >= 21) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();

                Class<?> vtBuilderCls = Class.forName("java.lang.Thread$Builder$OfVirtual");

                Object virtualBuilder = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(vtBuilderCls)).invoke();
                virtualBuilder = lookup.findVirtual(vtBuilderCls, "name", MethodType.methodType(vtBuilderCls, String.class, long.class))
                        .invoke(virtualBuilder, "Download", 10L);

                ThreadFactory threadFactory = (ThreadFactory) lookup.findVirtual(vtBuilderCls, "factory", MethodType.methodType(ThreadFactory.class))
                        .invoke(virtualBuilder);

                executorService = (ExecutorService)
                        lookup.findStatic(Executors.class, "newThreadPerTaskExecutor", MethodType.methodType(ExecutorService.class, ThreadFactory.class))
                                .invoke(threadFactory);
            } catch (Throwable e) {
                LOG.warning("Failed to initialize download executor", e);
            }
        }
        if (executorService == null)
            executorService = threadPool("Download", true, downloadExecutorConcurrency, 10, TimeUnit.SECONDS);
        DOWNLOAD_EXECUTOR = executorService;
    }

    public static void setDownloadExecutorConcurrency(int concurrency) {
        concurrency = Math.max(concurrency, 1);

        synchronized (Schedulers.class) {
            int prevDownloadExecutorConcurrency = downloadExecutorConcurrency;
            downloadExecutorConcurrency = concurrency;

            int change = concurrency - prevDownloadExecutorConcurrency;
            if (change != 0 && DOWNLOAD_EXECUTOR instanceof ThreadPoolExecutor downloadExecutor) {
                if (downloadExecutor.getMaximumPoolSize() <= concurrency) {
                    downloadExecutor.setMaximumPoolSize(concurrency);
                    downloadExecutor.setCorePoolSize(concurrency);
                } else {
                    downloadExecutor.setCorePoolSize(concurrency);
                    downloadExecutor.setMaximumPoolSize(concurrency);
                }
            }

            if (change > 0) {
                SEMAPHORE.release(change);
            } else if (change < 0) {
                int permits = -change;

                if (!SEMAPHORE.tryAcquire(permits)) {
                    thread(() -> {
                        try {
                            SEMAPHORE.acquire(permits);
                        } catch (InterruptedException e) {
                            throw new AssertionError(e);
                        }
                    }, "Semaphore Acquirer", true);
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
