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
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Override
    public void execute() throws Exception {
        boolean checkETag;
        switch (shouldCheckETag()) {
            case CHECK_E_TAG -> checkETag = true;
            case NOT_CHECK_E_TAG -> checkETag = false;
            default -> {
                return;
            }
        }

        ArrayList<DownloadException> exceptions = null;

        if (SEMAPHORE != null)
            SEMAPHORE.acquire();
        try {
            for (URI uri : uris) {
                try {
                    if (NetworkUtils.isHttpUri(uri))
                        downloadHttp(uri, checkETag);
                    else
                        downloadNotHttp(uri);
                    return;
                } catch (DownloadException e) {
                    if (exceptions == null)
                        exceptions = new ArrayList<>();
                    exceptions.add(e);
                }
            }
        } catch (InterruptedException ignored) {
            // Cancelled
        } finally {
            if (SEMAPHORE != null)
                SEMAPHORE.release();
        }

        if (exceptions != null) {
            DownloadException last = exceptions.remove(exceptions.size() - 1);
            for (DownloadException exception : exceptions) {
                last.addSuppressed(exception);
            }
            throw last;
        }
    }

    private static final class HttpResumeContext {
        private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)");

        static @Nullable FetchTask.HttpResumeContext of(HttpResponse<?> response) throws IOException {
            if (response.statusCode() != HttpURLConnection.HTTP_OK)
                return null;

            boolean acceptRanges = response.headers().firstValue("accept-ranges").orElse("").equalsIgnoreCase("bytes");
            if (!acceptRanges)
                return null;

            var contentEncoding = ContentEncoding.fromHeaders(response.headers());
            if (contentEncoding != ContentEncoding.IDENTITY)
                return null;

            long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (contentLength < 0)
                return null;

            String eTag = response.headers().firstValue("etag").orElse(null);
            String strongETag = isStrongETag(eTag) ? eTag : null;
            String lastModified = response.headers().firstValue("last-modified").orElse(null);
            if (strongETag == null && StringUtils.isBlank(lastModified))
                return null;

            return new HttpResumeContext(response.uri(), contentLength, strongETag, lastModified);
        }

        private final URI uri;
        private final long contentLength;
        private final @Nullable String strongETag;
        private final @Nullable String lastModified;

        long countUncompressed;

        private HttpResumeContext(URI uri, long contentLength, @Nullable String strongETag, @Nullable String lastModified) {
            this.uri = uri;
            this.contentLength = contentLength;
            this.strongETag = strongETag;
            this.lastModified = lastModified;
        }

        private static boolean isStrongETag(@Nullable String eTag) {
            return StringUtils.isNotBlank(eTag) && !eTag.regionMatches(true, 0, "W/", 0, 2);
        }

        String ifRange() {
            return strongETag != null ? strongETag : Objects.requireNonNull(lastModified);
        }

        boolean canResume(HttpResponse<?> response) throws IOException {
            if (response.statusCode() != HttpURLConnection.HTTP_PARTIAL)
                return false;

            var contentEncoding = ContentEncoding.fromHeaders(response.headers());
            if (contentEncoding != ContentEncoding.IDENTITY)
                return false;

            long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (this.contentLength != contentLength + this.countUncompressed)
                return false;

            if (strongETag != null) {
                String eTag = response.headers().firstValue("etag").orElse(null);
                if (!strongETag.equals(eTag))
                    return false;
            } else {
                if (!uri.equals(response.uri()))
                    return false;

                String lastModified = response.headers().firstValue("last-modified").orElse("");
                if (!Objects.requireNonNull(this.lastModified).equals(lastModified))
                    return false;
            }

            String contentRange = response.headers().firstValue("content-range").orElse("");
            Matcher matcher = CONTENT_RANGE_PATTERN.matcher(contentRange);
            if (!matcher.matches())
                return false;

            try {
                long start = Long.parseLong(matcher.group(1));
                long end = Long.parseLong(matcher.group(2));
                long total = Long.parseLong(matcher.group(3));

                if (start != countUncompressed || end < start || total != this.contentLength)
                    return false;

                return end - start + 1 == contentLength;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        boolean hasPartialContent() {
            return countUncompressed > 0 && countUncompressed < contentLength;
        }
    }

    private void download(Context context,
                          @Nullable FetchTask.HttpResumeContext resume, InputStream inputStream,
                          long contentLength,
                          ContentEncoding contentEncoding) throws IOException, InterruptedException {
        boolean success = false;
        try (var counter = new CounterInputStream(inputStream);
             var input = contentEncoding.wrap(counter)) {
            long lastDownloaded = 0L;
            byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
            while (true) {
                if (isCancelled()) break;

                int len = input.read(buffer);
                if (len == -1) break;

                try {
                    context.write(buffer, 0, len);
                } catch (Throwable e) {
                    context.broken = true;
                    throw e;
                }

                if (resume != null)
                    resume.countUncompressed += len;

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

            success = true;
        }

        if (success) {
            context.withResult(true);
        }
    }

    private void downloadHttp(URI uri, boolean checkETag) throws DownloadException, InterruptedException {
        if (checkETag) {
            // Handle cache
            try {
                Path cache = repository.getCachedRemoteFile(uri, true);
                useCachedResult(cache);
                LOG.info("Using cached file for " + NetworkUtils.dropQuery(uri));
                return;
            } catch (IOException ignored) {
            }
        }

        Context context = null;
        HttpResumeContext resumeContext = null;

        ArrayList<Exception> exceptions = null;

        // If loading the cache fails, the cache should not be loaded again.
        boolean useCachedResult = true;
        try {
            for (int retryTime = 0, retryLimit = retry; retryTime < retryLimit; retryTime++) {
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

                    boolean resumeRequested = resumeContext != null && resumeContext.hasPartialContent();
                    if (useCachedResult && checkETag && !resumeRequested)
                        headers.putAll(repository.injectConnection(uri));
                    if (resumeRequested) {
                        headers.put("range", "bytes=" + resumeContext.countUncompressed + "-");
                        headers.put("if-range", resumeContext.ifRange());
                    }

                    do {
                        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(currentURI)
                                .timeout(Duration.ofMillis(NetworkUtils.TIME_OUT))
                                .header("User-Agent", NetworkUtils.USER_AGENT);
                        headers.forEach(requestBuilder::header);
                        response = Holder.HTTP_CLIENT.send(requestBuilder.build(), BODY_HANDLER);

                        bmclapiHash = response.headers().firstValue("x-bmclapi-hash").orElse(null);
                        if (DigestUtils.isSha1Digest(bmclapiHash)) {
                            Optional<Path> cache = repository.checkExistentFile(null, "SHA-1", bmclapiHash);
                            if (cache.isPresent()) {
                                closeResponseBody(response);
                                useCachedResult(cache.get());
                                LOG.info("Using cached file for " + NetworkUtils.dropQuery(uri));
                                return;
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

                            URI target = currentURI.resolve(NetworkUtils.encodeLocation(location));
                            redirects.add(target);

                            if (!NetworkUtils.isHttpUri(target))
                                throw new IOException("Redirected to not http URI: " + target);

                            closeResponseBody(response);
                            currentURI = target;
                        } else {
                            break;
                        }
                    } while (true);

                    boolean closeResponseBody = true;
                    try {
                        int responseCode = response.statusCode();
                        if (resumeRequested && responseCode == 416) {
                            resumeContext = null;
                            discardContext(context);
                            context = null;
                            retryLimit++;
                            continue;
                        }

                        if (useCachedResult && responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            // Handle cache
                            try {
                                Path cache = repository.getCachedRemoteFile(currentURI, false);
                                useCachedResult(cache);
                                LOG.info("Using cached file for " + NetworkUtils.dropQuery(uri));
                                return;
                            } catch (CacheRepository.CacheExpiredException e) {
                                LOG.info("Cache expired for " + NetworkUtils.dropQuery(uri));
                            } catch (IOException e) {
                                LOG.warning("Unable to use cached file, redownload " + NetworkUtils.dropQuery(uri), e);
                                repository.removeRemoteEntry(currentURI);
                                useCachedResult = false;
                                // Now we must reconnect the server since 304 may result in empty content,
                                // if we want to redownload the file, we must reconnect the server without etag settings.
                                retryLimit++;
                                continue;
                            }
                        } else if (responseCode / 100 == 4) {
                            throw new FileNotFoundException(uri.toString());
                        } else if (responseCode / 100 != 2) {
                            throw new ResponseCodeException(uri, responseCode);
                        }

                        long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
                        var contentEncoding = ContentEncoding.fromHeaders(response.headers());

                        if (context == null) {
                            context = getContext(response, checkETag, bmclapiHash);
                            resumeContext = HttpResumeContext.of(response);
                        } else if (resumeRequested) {
                            if (resumeContext.canResume(response)) {
                                // Resume download
                                LOG.info("Resuming " + resumeContext.uri + " from " + resumeContext.countUncompressed);
                            } else {
                                // Failed to resume download, so we will retry from the beginning
                                resumeContext = null;
                                discardContext(context);
                                context = null;
                                retryLimit++;
                                continue;
                            }
                        } else {
                            discardContext(context);
                            context = getContext(response, checkETag, bmclapiHash);
                            resumeContext = HttpResumeContext.of(response);
                        }

                        try {
                            closeResponseBody = false;
                            download(context,
                                    resumeContext, response.body(),
                                    contentLength,
                                    contentEncoding);
                        } catch (IOException | InterruptedException | RuntimeException | Error e) {
                            if (context.broken) {
                                IOUtils.closeQuietly(context, e);
                                context = null;
                                resumeContext = null;
                            }
                            throw e;
                        }
                        try {
                            context.close();
                        } catch (IOException | RuntimeException | Error e) {
                            context.withResult(false);
                            IOUtils.closeQuietly(context, e);
                            context = null;
                            resumeContext = null;
                            throw e;
                        }
                        context = null;
                        return;
                    } finally {
                        if (closeResponseBody) {
                            closeResponseBody(response);
                        }
                    }
                } catch (InterruptedException e) {
                    throw e;
                } catch (FileNotFoundException ex) {
                    LOG.warning("Failed to download " + uri + ", not found" + (redirects == null ? "" : ", redirects: " + redirects), ex);
                    throw toDownloadException(uri, ex, exceptions); // we will not try this URL again
                } catch (Exception ex) {
                    if (exceptions == null)
                        exceptions = new ArrayList<>();

                    exceptions.add(ex);

                    LOG.warning("Failed to download " + uri + ", repeat times: " + retryTime + (redirects == null ? "" : ", redirects: " + redirects), ex);

                    if (retryTime < retryLimit - 1) {
                        // Wait for a while before retrying
                        Thread.sleep(200);
                    }
                }
            }
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (IOException e) {
                    LOG.warning("Failed to close context for " + NetworkUtils.dropQuery(uri), e);
                }
            }
        }

        throw toDownloadException(uri, null, exceptions);
    }

    private static void closeResponseBody(HttpResponse<InputStream> response) {
        InputStream body = response.body();
        if (body != null)
            IOUtils.closeQuietly(body);
    }

    private static void discardContext(@Nullable Context context) {
        if (context != null) {
            context.withResult(false);
            IOUtils.closeQuietly(context);
        }
    }

    private void downloadNotHttp(URI uri) throws DownloadException, InterruptedException {
        ArrayList<Exception> exceptions = null;
        for (int retryTime = 0; retryTime < retry; retryTime++) {
            if (isCancelled()) {
                throw new InterruptedException();
            }

            try {
                beforeDownload(uri);
                updateProgress(0);

                URLConnection conn = NetworkUtils.createConnection(uri);
                try (Context context = getContext()) {
                    download(context,
                            null, conn.getInputStream(),
                            conn.getContentLengthLong(),
                            ContentEncoding.fromConnection(conn));
                }
                return;
            } catch (InterruptedException e) {
                throw e;
            } catch (FileNotFoundException ex) {
                LOG.warning("Failed to download " + uri + ", not found", ex);

                throw toDownloadException(uri, ex, exceptions); // we will not try this URL again
            } catch (Exception ex) {
                if (exceptions == null)
                    exceptions = new ArrayList<>();

                exceptions.add(ex);
                LOG.warning("Failed to download " + uri + ", repeat times: " + retryTime, ex);
            }
        }

        throw toDownloadException(uri, null, exceptions);
    }

    private static DownloadException toDownloadException(URI uri, @Nullable Exception last, @Nullable ArrayList<Exception> exceptions) {
        if (exceptions == null || exceptions.isEmpty()) {
            return new DownloadException(uri, last != null
                    ? last
                    : new IOException("No exceptions"));
        } else {
            if (last == null)
                last = exceptions.remove(exceptions.size() - 1);

            for (Exception e : exceptions) {
                last.addSuppressed(e);
            }
            return new DownloadException(uri, last);
        }
    }

    private static final Timer timer = new Timer("DownloadSpeedRecorder", true);
    private static final AtomicLong downloadSpeed = new AtomicLong(0L);
    public static final EventManager<SpeedEvent> SPEED_EVENT = EventBus.EVENT_BUS.channel(SpeedEvent.class);

    static {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                SPEED_EVENT.fireEvent(new SpeedEvent(SPEED_EVENT, downloadSpeed.getAndSet(0)));
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
        private boolean broken;

        protected final boolean isSuccess() {
            return success;
        }

        public void withResult(boolean success) {
            this.success = success;
        }

        public abstract void reset() throws IOException;

        public abstract void write(byte[] buffer, int offset, int len) throws IOException;

        @Override
        public abstract void close() throws IOException;
    }

    protected enum EnumCheckETag {
        CHECK_E_TAG,
        NOT_CHECK_E_TAG,
        CACHED
    }

    private static final HttpResponse.BodyHandler<InputStream> BODY_HANDLER = responseInfo -> {
        if (responseInfo.statusCode() / 100 == 2)
            return HttpResponse.BodySubscribers.ofInputStream();
        else
            return HttpResponse.BodySubscribers.replacing(null);
    };

    public static int DEFAULT_CONCURRENCY = Math.min(Runtime.getRuntime().availableProcessors() * 4, 64);
    private static int downloadExecutorConcurrency = DEFAULT_CONCURRENCY;

    // For Java 21 or later, DOWNLOAD_EXECUTOR dispatches tasks to virtual threads, and concurrency is controlled by SEMAPHORE.
    // For versions earlier than Java 21, DOWNLOAD_EXECUTOR is a ThreadPoolExecutor, SEMAPHORE is null, and concurrency is controlled by the thread pool size.

    private static final ExecutorService DOWNLOAD_EXECUTOR;
    private static final @Nullable Semaphore SEMAPHORE;

    static {
        ExecutorService executorService = Schedulers.newVirtualThreadPerTaskExecutor("Download");
        if (executorService != null) {
            DOWNLOAD_EXECUTOR = executorService;
            SEMAPHORE = new Semaphore(DEFAULT_CONCURRENCY);
        } else {
            DOWNLOAD_EXECUTOR = threadPool("Download", true, downloadExecutorConcurrency, 10, TimeUnit.SECONDS);
            SEMAPHORE = null;
        }
    }

    @FXThread
    public static void setDownloadExecutorConcurrency(int concurrency) {
        concurrency = Math.max(concurrency, 1);

        int prevDownloadExecutorConcurrency = downloadExecutorConcurrency;
        int change = concurrency - prevDownloadExecutorConcurrency;
        if (change == 0)
            return;

        downloadExecutorConcurrency = concurrency;
        if (SEMAPHORE != null) {
            if (change > 0) {
                SEMAPHORE.release(change);
            } else {
                int permits = -change;
                if (!SEMAPHORE.tryAcquire(permits)) {
                    Schedulers.io().execute(() -> {
                        try {
                            for (int i = 0; i < permits; i++) {
                                SEMAPHORE.acquire();
                            }
                        } catch (InterruptedException e) {
                            throw new AssertionError("Unreachable", e);
                        }
                    });
                }
            }
        } else {
            var downloadExecutor = (ThreadPoolExecutor) DOWNLOAD_EXECUTOR;

            if (downloadExecutor.getMaximumPoolSize() <= concurrency) {
                downloadExecutor.setMaximumPoolSize(concurrency);
                downloadExecutor.setCorePoolSize(concurrency);
            } else {
                downloadExecutor.setCorePoolSize(concurrency);
                downloadExecutor.setMaximumPoolSize(concurrency);
            }
        }
    }

    public static int getDownloadExecutorConcurrency() {
        return downloadExecutorConcurrency;
    }

    private static volatile boolean initialized = false;

    public static void notifyInitialized() {
        initialized = true;
    }

    /// Ensure that [#HTTP_CLIENT] is initialized after ProxyManager has been initialized.
    private static final class Holder {
        private static final HttpClient HTTP_CLIENT;

        static {
            if (!initialized) {
                throw new AssertionError("FetchTask.Holder accessed before ProxyManager initialization.");
            }

            boolean useHttp2 = !"false".equalsIgnoreCase(System.getProperty("hmcl.http2"));

            HTTP_CLIENT = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(NetworkUtils.TIME_OUT))
                    .version(useHttp2 ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1)
                    .build();
        }

        private Holder() {
        }
    }
}
