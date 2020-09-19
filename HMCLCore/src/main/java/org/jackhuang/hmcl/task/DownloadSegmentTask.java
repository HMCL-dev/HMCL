package org.jackhuang.hmcl.task;

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

class DownloadSegmentTask {

    private final DownloadManager.DownloadTask<?> task;
    private final DownloadManager.DownloadTaskState state;
    private final RandomAccessFile file;
    private int tryTime = 0;
    private URLConnection conn;
    private URL lastURL;
    private DownloadManager.DownloadSegment segment;

    public DownloadSegmentTask(DownloadManager.DownloadTask<?> task, RandomAccessFile file, DownloadManager.DownloadSegment segment) {
        this.task = task;
        this.state = task.getDownloadState();
        this.file = file;
        this.segment = segment;
    }

    private URLConnection createConnection(boolean retryLastConnection) throws IOException {
        if (retryLastConnection && lastURL != null) {
            return NetworkUtils.createConnection(lastURL, 4000);
        }

        // 1. try connection given by DownloadTask
        if (this.conn != null) {
            URLConnection conn = this.conn;
            lastURL = conn.getURL();
            this.conn = null;
            return conn;
        }

        // 2. try suggested URL at the first time
        if (tryTime == 0) {
            lastURL = state.getFirstUrl();
            return NetworkUtils.createConnection(lastURL, 4000);
        }

        // 3. try fastest URL if measured
        URL fastestURL = state.getFastestUrl();
        if (fastestURL != null) {
            lastURL = fastestURL;
            return NetworkUtils.createConnection(lastURL, 4000);
        }

        // 4. try other URL, DownloadTaskState will make all DownloadSegmentTask
        //    try different URL to speed up connection.
        URL nextURLToTry = state.getNextUrlToRetry();
        if (nextURLToTry == null) {
            return null;
        }
        tryTime++;
        lastURL = nextURLToTry;
        return NetworkUtils.createConnection(lastURL, 4000);
    }

    public void run() throws DownloadException {
        Exception exception = null;
        URL failedURL = null;
        boolean checkETag;
        switch (task.getCheckETag()) {
            case CHECK_E_TAG:
                checkETag = true;
                break;
            case NOT_CHECK_E_TAG:
                checkETag = false;
                break;
            default:
                return;
        }

        boolean retryLastConnection = false;
        while (true) {
            if (state.isCancelled() || state.isFinished()) {
                break;
            }

            try {
                URLConnection conn = createConnection(retryLastConnection);
                if (conn == null) {
                    break;
                }

                if (checkETag) task.repository.injectConnection(conn);

                task.onBeforeConnection(lastURL);
                segment.setDownloaded(0);

                try (DownloadManager.SafeRegion region = state.checkingConnection()) {
                    // If other DownloadSegmentTask finishedWithCachedResult
                    // then this task should stop.
                    if (state.isFinished()) {
                        return;
                    }

                    if (state.getContentLength() == 0) {
                        state.setContentLength(conn.getContentLength());
                    }

                    // TODO: reset connection with range

                    if (conn instanceof HttpURLConnection) {
                        conn = NetworkUtils.resolveConnection((HttpURLConnection) conn);
                        int responseCode = ((HttpURLConnection) conn).getResponseCode();

                        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            // Handle cache
                            try {
                                Path cache = task.repository.getCachedRemoteFile(conn);
                                task.finishWithCachedResult(cache);
                                return;
                            } catch (IOException e) {
                                Logging.LOG.log(Level.WARNING, "Unable to use cached file, re-download " + lastURL, e);
                                task.repository.removeRemoteEntry(conn);
                                // Now we must reconnect the server since 304 may result in empty content,
                                // if we want to re-download the file, we must reconnect the server without etag settings.
                                retryLastConnection = true;
                                continue;
                            }
                        } else if (responseCode / 100 == 4) {
                            state.forbidURL(lastURL);
                            break; // we will not try this URL again
                        } else if (responseCode / 100 != 2) {
                            throw new ResponseCodeException(lastURL, responseCode);
                        }
                    }
                }

                try (InputStream stream = conn.getInputStream()) {
                    int lastDownloaded = 0, downloaded = 0;
                    byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                    while (true) {
                        if (state.isCancelled()) break;

                        int len = stream.read(buffer);
                        if (len == -1) break;

                        try (DownloadManager.SafeRegion region = state.writing()) {
                            task.write(buffer, 0, len);
                        }

                        downloaded += len;

                        if (conn.getContentLength() >= 0) {
                            // Update progress information per second
                            segment.setDownloaded(downloaded);
                        }

                        DownloadManager.updateDownloadSpeed(downloaded - lastDownloaded);
                        lastDownloaded = downloaded;
                    }

                    if (state.isCancelled()) break;

                    if (conn.getContentLength() >= 0 && !segment.isFinished())
                        throw new IOException("Unexpected segment size: " + downloaded + ", expected: " + segment.getLength());
                }

                segment.setConnection(conn);

                return;
            } catch (IOException ex) {
                failedURL = lastURL;
                exception = ex;
                Logging.LOG.log(Level.WARNING, "Failed to download " + failedURL + ", repeat times: " + tryTime, ex);
            }
        }

        if (exception != null)
            throw new DownloadException(failedURL, exception);
    }
}
