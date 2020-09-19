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
import java.util.concurrent.Callable;
import java.util.logging.Level;

class DownloadSegmentTask implements Callable<Void> {

    private final DownloadManager.DownloadTask<?> task;
    private final DownloadManager.Downloader<?> downloader;
    private final DownloadManager.DownloadTaskState state;
    private int tryTime = 0;
    private URL lastURL;
    private DownloadManager.DownloadSegment segment;

    public DownloadSegmentTask(DownloadManager.DownloadTask<?> task, DownloadManager.Downloader<?> downloader, DownloadManager.DownloadSegment segment) {
        this.task = task;
        this.downloader = downloader;
        this.state = task.getDownloadState();
        this.segment = segment;
    }

    private URLConnection createConnection(boolean retryLastConnection, int startPosition, int endPosition) throws IOException {
        if (retryLastConnection && lastURL != null) {
            return NetworkUtils.createConnection(lastURL, 4000);
        }

        // 1. If we don't know content length now, DownloadSegmentTasks should try
        //    different candidates.
        if (state.isWaitingForContentLength()) {
            URL nextUrlToRetry = state.getNextUrlToRetry();
            if (nextUrlToRetry == null) {
                return null;
            }
            lastURL = nextUrlToRetry;
            return NetworkUtils.createConnection(lastURL, 4000);
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

    @Override
    public Void call() throws DownloadException {
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
                return null;
        }

        boolean retryLastConnection = false;
        while (true) {
            if (state.isCancelled() || state.isFinished()) {
                break;
            }

            try {
                boolean detectRange = state.isWaitingForContentLength();
                URLConnection conn = createConnection(retryLastConnection, segment.getStartPosition(), segment.getEndPosition());
                if (conn == null) {
                    break;
                }

                if (checkETag) task.repository.injectConnection(conn);

                downloader.onBeforeConnection(lastURL);
                segment.setDownloaded(0);

                if (conn instanceof HttpURLConnection) {
                    conn = NetworkUtils.resolveConnection((HttpURLConnection) conn);
                }

                try (DownloadManager.SafeRegion region = state.checkingConnection()) {
                    // If other DownloadSegmentTask finishedWithCachedResult
                    // then this task should stop.
                    if (state.isFinished()) {
                        return null;
                    }

                    if (conn instanceof HttpURLConnection) {
                        int responseCode = ((HttpURLConnection) conn).getResponseCode();

                        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                            // Handle cache
                            try {
                                Path cache = task.repository.getCachedRemoteFile(conn);
                                task.finishWithCachedResult(cache);
                                return null;
                            } catch (IOException e) {
                                Logging.LOG.log(Level.WARNING, "Unable to use cached file, re-download " + lastURL, e);
                                task.repository.removeRemoteEntry(conn);
                                // Now we must reconnect the server since 304 may result in empty content,
                                // if we want to re-download the file, we must reconnect the server without etag settings.
                                retryLastConnection = true;
                                continue;
                            }
                        } else if (responseCode / 100 == 4) {
                            // 404 may occurs when we hit some mirror that does not have the file,
                            // but other mirrors may have, so we should try other URLs.
                            state.forbidURL(lastURL);
                            continue;
                        } else if (responseCode / 100 != 2) {
                            throw new ResponseCodeException(lastURL, responseCode);
                        }

                        // TODO: maybe some server supports partial content, other servers does not support,
                        // there should be a way to pick fastest server.
                        if (conn.getHeaderField("Range") != null && responseCode == 206) {
                            state.setSegmentSupported(true);
                        }
                    }

                    if (state.getContentLength() == 0) {
                        task.setContentLength(conn.getContentLength());
                    }

                    if (state.getContentLength() != conn.getContentLength()) {
                        // If content length is not expected, forbids this URL
                        state.forbidURL(lastURL);
                        continue;
                    }

                    // TODO: Currently we mark first successfully connected URL as "fastest" URL.
                    state.setFastestUrl(conn.getURL());

                    if (!state.isSegmentSupported() && segment.getStartPosition() != 0) {
                        // Now we have not figured if URL supports segment downloading,
                        // and have successfully fetched content length.
                        // We should check states of non-first DownloadSegmentTasks.
                        // First DownloadSegmentTask will continue downloading whatever segment is supported or not.
                        if (detectRange) {
                            // If this DownloadSegmentTask detects content length,
                            // reconnect to same URL with header Range, detecting if segment supported.
                            retryLastConnection = true;
                            continue;
                        } else {
                            // We already tested Range and found segment not supported.
                            // Make only first DownloadSegmentTask continue.
                            task.onSegmentFinished(segment);
                            return null;
                        }
                    }
                }

                try (InputStream stream = conn.getInputStream()) {
                    int lastDownloaded = 0, downloaded = 0;
                    byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                    while (true) {
                        if (state.isCancelled()) break;

                        // For first DownloadSegmentTask, if other DownloadSegmentTask have figured out
                        // that segment is supported, and this segment have already be finished,
                        // stop downloading.
                        if (state.isSegmentSupported() && segment.isFinished()) {
                            break;
                        }

                        int len = stream.read(buffer);
                        if (len == -1) break;

                        try (DownloadManager.SafeRegion region = state.writing()) {
                            downloader.write(segment.getStartPosition() + downloaded, buffer, 0, len);
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
                task.onSegmentFinished(segment);

                return null;
            } catch (IOException ex) {
                failedURL = lastURL;
                exception = ex;
                Logging.LOG.log(Level.WARNING, "Failed to download " + failedURL + ", repeat times: " + tryTime, ex);
            }
        }

        if (exception != null)
            throw new DownloadException(failedURL, exception);
        return null;
    }
}
