package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.logging.Level;

abstract class DownloadTask implements Runnable {

    private final DownloadManager.DownloadTaskState state;
    private final RandomAccessFile file;
    private URLConnection conn;
    private DownloadManager.DownloadSegment segment;
    protected boolean caching;
    protected CacheRepository repository = CacheRepository.getInstance();

    public DownloadTask(DownloadManager.DownloadTaskState state, RandomAccessFile file, DownloadManager.DownloadSegment segment) {
        this.state = state;
        this.file = file;
        this.segment = segment;
    }

    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    public void setCacheRepository(CacheRepository repository) {
        this.repository = repository;
    }

    protected void beforeDownload(URL url) throws IOException {
    }

    protected abstract void useCachedResult(Path cachedFile) throws IOException;

    protected abstract FetchTask.EnumCheckETag shouldCheckETag();

    protected abstract FetchTask.Context getContext(URLConnection conn, boolean checkETag) throws IOException;

    @Override
    public void run() {
        Exception exception = null;
        URL failedURL = null;
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
        while (true) {
            if (state.isCancelled()) {
                break;
            }

            String url = repeat == 0 ? state.getFirstUrl() : state.getNextUrlToRetry();
            repeat++;

            if (url == null) {
                break;
            }

            try {
                beforeDownload(url);

                updateProgress(0);

                URLConnection conn = NetworkUtils.createConnection(NetworkUtils.toURL(url);
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
                            repeat--;
                            continue;
                        }
                    } else if (responseCode / 100 == 4) {
                        break; // we will not try this URL again
                    } else if (responseCode / 100 != 2) {
                        throw new ResponseCodeException(url, responseCode);
                    }
                }

                long contentLength = conn.getContentLength();
                try (FetchTask.Context context = getContext(conn, checkETag); InputStream stream = conn.getInputStream()) {
                    int lastDownloaded = 0, downloaded = 0;
                    byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                    while (true) {
                        if (state.isCancelled()) break;

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

                    if (state.isCancelled()) break;

                    updateDownloadSpeed(downloaded - lastDownloaded);

                    if (contentLength >= 0 && downloaded != contentLength)
                        throw new IOException("Unexpected file size: " + downloaded + ", expected: " + contentLength);

                    context.withResult(true);
                }

                return;
            } catch (IOException ex) {
                failedURL = url;
                exception = ex;
                Logging.LOG.log(Level.WARNING, "Failed to download " + url + ", repeat times: " + repeat, ex);
            }
        }

        if (exception != null)
            throw new DownloadException(failedURL, exception);
    }
}
