// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DownloadCandidate;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.io.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Lang.*;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Downloads candidate URLs in order, with retries, HTTP caching, and supported range resumption.
@NotNullByDefault
public abstract class FetchTask<T extends @Nullable Object> extends Task<T> {

    protected static final int DEFAULT_RETRY = 5;

    protected final DownloadCandidates candidates;
    protected CacheRepository repository = CacheRepository.getInstance();

    /// Creates a download task with a snapshot of the candidate URLs.
    ///
    /// @param candidates nonempty candidate URLs, with no null elements
    /// @throws IllegalArgumentException if no candidates are supplied
    /// @throws NullPointerException if the list or any element is null
    public FetchTask(DownloadCandidates candidates) {
        this.candidates = candidates;
        setExecutor(DOWNLOAD_EXECUTOR);
    }

    public void setCacheRepository(CacheRepository repository) {
        this.repository = repository;
    }

    /// Invoked before each download attempt, after any cache lookup.
    ///
    /// @param url the candidate URL before redirects
    /// @throws IOException if the attempt cannot be prepared
    protected void beforeDownload(WebURL url) throws IOException {
    }

    /// Reads or installs a cached response and returns the task result.
    protected abstract T useCachedResult(Path cachedFile) throws IOException;

    /// Returns a cached file usable before the first request, or null on a cache miss.
    protected @Nullable Path findCachedFile() {
        return null;
    }

    /// Returns whether to consult HTTP cache metadata for this download.
    protected boolean shouldCheckETag() {
        return true;
    }

    /// Opens a sink for an attempt, optionally using HTTP cache and checksum metadata.
    protected abstract Context<T> getContext(@Nullable UrlResponseInfo response, boolean checkETag,
                                             @Nullable String bmclapiHash) throws IOException;

    @Override
    public T execute(TaskExecutionContext execution) throws Exception {
        @Nullable Path cached = findCachedFile();
        if (cached != null) {
            try {
                return useCachedResult(cached);
            } catch (IOException e) {
                LOG.warning("Failed to use cached file " + cached, e);
            }
        }
        boolean checkETag = shouldCheckETag();
        @Nullable ArrayList<DownloadException> exceptions = null;

        if (SEMAPHORE != null)
            SEMAPHORE.acquire();
        try {
            for (DownloadCandidate candidate : candidates.getCandidates()) {
                WebURL url = candidate.url();
                if (url == null) {
                    if (exceptions == null)
                        exceptions = new ArrayList<>();
                    exceptions.add(new DownloadException(candidate.rawUrl(), new MalformedURLException("Invalid URL: " + candidate)));
                    continue;
                }

                int retry = candidate.retry() >= 0 ? candidate.retry() : DEFAULT_RETRY;
                Duration connectTimeout = Objects.requireNonNullElse(candidate.connectTimeout(), NetworkUtils.TIMEOUT);
                Duration readTimeout = Objects.requireNonNullElse(candidate.readTimeout(), NetworkUtils.TIMEOUT);

                try {
                    if (NetworkUtils.isHttpUri(url))
                        return downloadHttp(url, retry, connectTimeout, readTimeout, checkETag);
                    return downloadNotHttp(url, retry, connectTimeout, readTimeout);
                } catch (DownloadException e) {
                    if (exceptions == null)
                        exceptions = new ArrayList<>();
                    exceptions.add(e);
                }
            }
        } finally {
            if (SEMAPHORE != null)
                SEMAPHORE.release();
        }

        if (exceptions == null) throw new IOException("No download candidates");
        DownloadException last = exceptions.remove(exceptions.size() - 1);
        for (DownloadException exception : exceptions) {
            last.addSuppressed(exception);
        }
        throw last;
    }

    private static final class HttpResumeContext {
        private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)");

        static @Nullable FetchTask.HttpResumeContext of(UrlResponseInfo response) throws IOException {
            if (response.responseCode() != HttpURLConnection.HTTP_OK)
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

            return new HttpResumeContext(response.url(), contentLength, strongETag, lastModified);
        }

        /// Final response URL whose partial content is retained for resumption.
        private final WebURL url;
        private final long contentLength;
        private final @Nullable String strongETag;
        private final @Nullable String lastModified;

        long countUncompressed;

        /// Records the response URL, total byte length, and available validators for resumption.
        private HttpResumeContext(WebURL url, long contentLength, @Nullable String strongETag, @Nullable String lastModified) {
            this.url = url;
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

        boolean canResume(int statusCode, UrlResponseInfo response) throws IOException {
            if (statusCode != HttpURLConnection.HTTP_PARTIAL)
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
                if (!url.equals(response.url()))
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

    private void download(Context<T> context,
                          @Nullable FetchTask.HttpResumeContext resume, InputStream inputStream,
                          long contentLength,
                          ContentEncoding contentEncoding) throws IOException, InterruptedException {
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
        }
    }

    /// Downloads an HTTP candidate, following HTTP(S) redirects and retrying recoverable failures.
    private T downloadHttp(WebURL url, int retry, Duration connectTimeout, Duration readTimeout, boolean checkETag) throws DownloadException, InterruptedException {
        if (checkETag) {
            // Handle cache
            try {
                Path cache = repository.getCachedRemoteFile(url, true);
                T result = useCachedResult(cache);
                LOG.info("Using cached file for " + NetworkUtils.dropQuery(url));
                return result;
            } catch (IOException ignored) {
            }
        }

        @Nullable Context<T> context = null;
        @Nullable HttpResumeContext resumeContext = null;

        @Nullable ArrayList<Exception> exceptions = null;

        // If loading the cache fails, the cache should not be loaded again.
        boolean useCachedResult = true;
        try {
            for (int attempts = 0, attemptsLimit = retry + 1; attempts < attemptsLimit; attempts++) {
                if (isCancelled()) {
                    throw new InterruptedException();
                }

                @Nullable List<WebURL> redirects = null;
                try {
                    beforeDownload(url);
                    updateProgress(0);

                    @Nullable HttpURLConnection connection;
                    UrlResponseInfo responseInfo;
                    @Nullable String bmclapiHash;
                    int responseCode;

                    WebURL currentURI = url;

                    LinkedHashMap<String, String> headers = new LinkedHashMap<>();
                    headers.put("accept-encoding", "gzip");

                    boolean resumeRequested = resumeContext != null && resumeContext.hasPartialContent();
                    if (useCachedResult && checkETag && !resumeRequested)
                        headers.putAll(repository.injectConnection(url));
                    if (resumeRequested) {
                        headers.put("range", "bytes=" + resumeContext.countUncompressed + "-");
                        headers.put("if-range", resumeContext.ifRange());
                    }

                    do {
                        connection = NetworkUtils.createHttpConnection(currentURI);
                        connection.setConnectTimeout((int) connectTimeout.toMillis());
                        connection.setReadTimeout((int) readTimeout.toMillis());
                        boolean keepConnection = false;
                        try {
                            headers.forEach(connection::setRequestProperty);
                            responseCode = connection.getResponseCode();
                            responseInfo = UrlResponseInfo.of(connection);

                            bmclapiHash = responseInfo.headers().firstValue("x-bmclapi-hash").orElse(null);
                            if (DigestUtils.isSha1Digest(bmclapiHash)) {
                                Optional<Path> cache = repository.checkExistentFile(null, "SHA-1", bmclapiHash);
                                if (cache.isPresent()) {
                                    T result = useCachedResult(cache.get());
                                    LOG.info("Using cached file for " + NetworkUtils.dropQuery(url));
                                    return result;
                                }
                            }

                            if (responseCode >= 300 && responseCode <= 308 && responseCode != 306 && responseCode != 304) {
                                if (redirects == null) {
                                    redirects = new ArrayList<>();
                                } else if (redirects.size() >= 20) {
                                    throw new IOException("Too much redirects");
                                }

                                @Nullable String location = connection.getHeaderField("Location");
                                if (StringUtils.isBlank(location))
                                    throw new IOException("Redirected to an empty location");

                                WebURL target = currentURI.resolve(location);
                                redirects.add(target);

                                if (!NetworkUtils.isHttpUri(target))
                                    throw new IOException("Redirected to non-HTTP URL: " + target);

                                currentURI = target;
                            } else {
                                keepConnection = true;
                                break;
                            }
                        } finally {
                            if (!keepConnection && connection != null) {
                                closeHttpConnection(connection);
                                connection = null;
                            }
                        }
                    } while (true);

                    @Nullable InputStream inputStream = null;
                    boolean responseBodyConsumed = false;
                    try {
                        if (resumeRequested && responseCode == 416) {
                            resumeContext = null;
                            IOUtils.closeQuietly(context);
                            context = null;
                            attemptsLimit++;
                            continue;
                        }

                        if (useCachedResult && responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            // Handle cache
                            try {
                                Path cache = repository.getCachedRemoteFile(responseInfo.url(), false);
                                T result = useCachedResult(cache);
                                LOG.info("Using cached file for " + NetworkUtils.dropQuery(url));
                                return result;
                            } catch (CacheRepository.CacheExpiredException e) {
                                LOG.info("Cache expired for " + NetworkUtils.dropQuery(url));
                            } catch (IOException e) {
                                LOG.warning("Unable to use cached file, redownload " + NetworkUtils.dropQuery(url), e);
                                repository.removeRemoteEntry(currentURI);
                                useCachedResult = false;
                                // Now we must reconnect the server since 304 may result in empty content,
                                // if we want to redownload the file, we must reconnect the server without etag settings.
                                attemptsLimit++;
                                continue;
                            }
                        } else if (responseCode / 100 == 4) {
                            throw new FileNotFoundException(url.toString());
                        } else if (responseCode / 100 != 2) {
                            throw new ResponseCodeException(url, responseCode);
                        }

                        long contentLength = responseInfo.headers().firstValueAsLong("content-length").orElse(-1L);
                        var contentEncoding = ContentEncoding.fromHeaders(responseInfo.headers());

                        if (context == null) {
                            context = getContext(responseInfo, checkETag, bmclapiHash);
                            resumeContext = HttpResumeContext.of(responseInfo);
                        } else if (resumeRequested) {
                            if (resumeContext.canResume(responseCode, responseInfo)) {
                                // Resume download
                                LOG.info("Resuming " + resumeContext.url + " from " + resumeContext.countUncompressed);
                            } else {
                                // Failed to resume download, so we will retry from the beginning
                                resumeContext = null;
                                IOUtils.closeQuietly(context);
                                context = null;
                                attemptsLimit++;
                                continue;
                            }
                        } else {
                            IOUtils.closeQuietly(context);
                            context = getContext(responseInfo, checkETag, bmclapiHash);
                            resumeContext = HttpResumeContext.of(responseInfo);
                        }

                        try {
                            inputStream = connection.getInputStream();
                            download(context,
                                    resumeContext, inputStream,
                                    contentLength,
                                    contentEncoding);
                            inputStream = null;
                            responseBodyConsumed = true;
                        } catch (IOException | InterruptedException | RuntimeException | Error e) {
                            if (context.broken) {
                                IOUtils.closeQuietly(context, e);
                                context = null;
                                resumeContext = null;
                            }
                            throw e;
                        }
                        try (Context<T> completed = context) {
                            context = null;
                            resumeContext = null;
                            return completed.finish();
                        }
                    } finally {
                        IOUtils.closeQuietly(inputStream);
                        if (connection != null && !responseBodyConsumed)
                            closeHttpConnection(connection);
                    }
                } catch (InterruptedException e) {
                    throw e;
                } catch (FileNotFoundException ex) {
                    LOG.warning("Failed to download " + url + ", not found" + (redirects == null ? "" : ", redirects: " + redirects), ex);
                    throw toDownloadException(url, ex, exceptions); // we will not try this URL again
                } catch (Exception ex) {
                    if (exceptions == null)
                        exceptions = new ArrayList<>();

                    exceptions.add(ex);

                    LOG.warning("Failed to download " + url + ", repeat times: " + attempts + (redirects == null ? "" : ", redirects: " + redirects), ex);

                    if (attempts < attemptsLimit - 1) {
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
                    LOG.warning("Failed to close context for " + NetworkUtils.dropQuery(url), e);
                }
            }
        }

        throw toDownloadException(url, null, exceptions);
    }

    /// Disconnects an HTTP URL connection whose response body will not be consumed.
    private static void closeHttpConnection(HttpURLConnection connection) {
        IOUtils.closeQuietly(connection.getErrorStream());
        connection.disconnect();
    }

    /// Downloads a non-HTTP candidate through its installed URL handler, retrying I/O failures.
    private T downloadNotHttp(WebURL url, int retry, Duration connectTimeout, Duration readTimeout) throws DownloadException, InterruptedException {
        @Nullable ArrayList<Exception> exceptions = null;
        for (int attempts = 0, attemptsLimit = retry + 1; attempts < attemptsLimit; attempts++) {
            if (isCancelled()) {
                throw new InterruptedException();
            }

            try {
                beforeDownload(url);
                updateProgress(0);

                URLConnection conn = NetworkUtils.createConnection(url);
                conn.setConnectTimeout((int) connectTimeout.toMillis());
                conn.setReadTimeout((int) readTimeout.toMillis());
                try (Context<T> context = getContext(null, false, null)) {
                    download(context,
                            null, conn.getInputStream(),
                            conn.getContentLengthLong(),
                            ContentEncoding.fromConnection(conn));
                    return context.finish();
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (FileNotFoundException ex) {
                LOG.warning("Failed to download " + url + ", not found", ex);

                throw toDownloadException(url, ex, exceptions); // we will not try this URL again
            } catch (Exception ex) {
                if (exceptions == null)
                    exceptions = new ArrayList<>();

                exceptions.add(ex);
                LOG.warning("Failed to download " + url + ", repeat times: " + attempts, ex);
            }
        }

        throw toDownloadException(url, null, exceptions);
    }

    /// Wraps the final failure and attaches preceding failures as suppressed exceptions.
    private static DownloadException toDownloadException(WebURL url, @Nullable Exception last, @Nullable ArrayList<Exception> exceptions) {
        if (exceptions == null || exceptions.isEmpty()) {
            return new DownloadException(url, last != null
                    ? last
                    : new IOException("No exceptions"));
        } else {
            if (last == null)
                last = exceptions.remove(exceptions.size() - 1);

            for (Exception e : exceptions) {
                last.addSuppressed(e);
            }
            return new DownloadException(url, last);
        }
    }

    private static final Timer timer = new Timer("DownloadSpeedRecorder", true);
    private static final AtomicLong downloadSpeed = new AtomicLong(0L);
    public static final EventManager<SpeedEvent> SPEED_EVENT = new EventManager<>();

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

    /// Receives one response body and releases temporary resources on close.
    protected abstract static class Context<R extends @Nullable Object> implements Closeable {
        /// Marks a sink that has failed during a write and cannot resume.
        private boolean broken;

        /// Writes a slice of the response body.
        public abstract void write(byte[] buffer, int offset, int len) throws IOException;

        /// Commits the complete response and returns the result. The caller closes the sink afterwards.
        public abstract R finish() throws IOException;

        /// Releases temporary resources, including after a failed or abandoned attempt.
        @Override
        public void close() throws IOException {
        }
    }

    /// Stages one download in a temporary file until it is ready to verify or install.
    abstract static class FileContext<R extends @Nullable Object> extends Context<R> {
        /// File owned by the context until it is moved or deleted.
        private final Path temp;

        /// Stream writing the response and optionally computing its digest.
        private final OutputStream output;

        /// Creates a temporary file and opens it for writing.
        FileContext(@Nullable MessageDigest digest) throws IOException {
            temp = Files.createTempFile("hmcl-download-", null);
            try {
                OutputStream stream = Files.newOutputStream(temp);
                output = digest == null ? stream : new DigestOutputStream(stream, digest);
            } catch (IOException e) {
                deleteTempFile();
                throw e;
            }
        }

        @Override
        public final void write(byte[] buffer, int offset, int len) throws IOException {
            output.write(buffer, offset, len);
        }

        @Override
        public final R finish() throws IOException {
            output.close();
            return commit(temp);
        }

        /// Verifies or installs the closed temporary file and returns the download result.
        abstract R commit(Path temp) throws IOException;

        @Override
        public final void close() throws IOException {
            try {
                output.close();
            } finally {
                deleteTempFile();
            }
        }

        /// Removes any remaining temporary file after a successful or abandoned attempt.
        private void deleteTempFile() {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                LOG.warning("Failed to delete temporary file " + temp, e);
            }
        }
    }

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

}
