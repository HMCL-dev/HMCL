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

import static org.jackhuang.hmcl.util.Logging.LOG;

class DownloadSegmentTask implements Runnable {

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

    private URLConnection createConnection(URL url, int startPosition, int endPosition) throws IOException {
        URLConnection conn = NetworkUtils.createConnection(url, 4000);
        if (startPosition != endPosition) {
            conn.setRequestProperty("Range", "bytes=" + startPosition + "-" + (endPosition - 1));
        }
        return conn;
    }

    private URLConnection createConnection(boolean retryLastConnection, int startPosition, int endPosition) throws IOException {
        if (retryLastConnection && lastURL != null) {
            return createConnection(lastURL, startPosition, endPosition);
        }

        // 1. If we don't know content length now, DownloadSegmentTasks should try
        //    different candidates.
        //    Ensure first segment always try url with highest priority first.
        if (state.isWaitingForContentLength() && segment.getIndex() != 0) {
            URL nextUrlToRetry = state.getNextUrlToRetry();
            if (nextUrlToRetry == null) {
                return null;
            }
            lastURL = nextUrlToRetry;
            tryTime++;
            return createConnection(lastURL, startPosition, endPosition);
        }

        // 2. try suggested URL at the first time
        if (tryTime == 0) {
            lastURL = state.getFirstUrl();
            tryTime++;
            return createConnection(lastURL, startPosition, endPosition);
        }

        // 3. try fastest URL if measured
        URL fastestURL = state.getFastestUrl();
        if (fastestURL != null) {
            lastURL = fastestURL;
            return createConnection(lastURL, startPosition, endPosition);
        }

        if (tryTime >= state.getRetry()) {
            return null;
        }

        // 4. try other URL, DownloadTaskState will make all DownloadSegmentTask
        //    try different URL to speed up connection.
        URL nextURLToTry = state.getNextUrlToRetry();
        if (nextURLToTry == null) {
            return null;
        }
        tryTime++;
        lastURL = nextURLToTry;
        return createConnection(lastURL, startPosition, endPosition);
    }

    @Override
    public void run() {
        try {
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
            loop: while (true) {
                if (state.isCancelled() || state.isFinished()) {
                    break;
                }

                try {
                    boolean detectRange = state.isWaitingForContentLength();
                    boolean connectionSegmented = false;
                    URLConnection conn = createConnection(retryLastConnection, segment.getStartPosition(), segment.getEndPosition());
                    if (conn == null) {
                        break;
                    }

                    URL url = conn.getURL();

                    if (checkETag) task.repository.injectConnection(conn);

                    LOG.log(Level.INFO, "URL " + url + " " + this);
                    downloader.onBeforeConnection(segment, lastURL);

                    if (conn instanceof HttpURLConnection) {
                        conn = NetworkUtils.resolveConnection((HttpURLConnection) conn);
                    }

                    try (DownloadManager.SafeRegion region = state.checkingConnection()) {
                        // If other DownloadSegmentTask finishedWithCachedResult
                        // then this task should stop.
                        if (state.isFinished()) {
                            return;
                        }

                        if (conn instanceof HttpURLConnection) {
                            int responseCode = ((HttpURLConnection) conn).getResponseCode();

                            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                                // Handle cache
                                try {
                                    Path cache = task.repository.getCachedRemoteFile(conn);
                                    task.finishWithCachedResult(cache);
                                    return;
                                } catch (IOException e) {
                                    LOG.log(Level.WARNING, "Unable to use cached file, re-download " + lastURL, e);
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
                            if (conn.getHeaderField("Content-Range") != null && responseCode == 206) {
                                state.setSegmentSupported(true);
                                connectionSegmented = true;
                            }
                        }

                        if (state.getContentLength() == 0) {
                            task.setContentLength(conn.getContentLength());
                        }

                        int expectedLength = connectionSegmented ? segment.getLength() : state.getContentLength();
                        if (expectedLength != conn.getContentLength()) {
                            // If content length is not expected, forbids this URL
                            LOG.warning("Content length mismatch " + segment + ", expected: " + expectedLength + ", actual: " + conn.getContentLength());
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
                                return;
                            }
                        }
                    }

                    try (InputStream stream = conn.getInputStream()) {
                        int startPosition, lastPosition, position;
                        position = lastPosition = startPosition = connectionSegmented ? segment.getStartPosition() : 0;
                        segment.setCurrentPosition(startPosition);
                        byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                        while (true) {
                            if (state.isCancelled()) break;

                            // For first DownloadSegmentTask, if other DownloadSegmentTask have figured out
                            // that segment is supported, and this segment have already be finished,
                            // stop downloading.
                            if (state.isSegmentSupported() && segment.isFinished()) {
                                break;
                            }

                            // If some non-first segment started downloading without segment,
                            // stop it.
                            if (state.isSegmentSupported() && position < segment.getStartPosition() && segment.getIndex() != 0) {
                                continue loop;
                            }

                            int len = stream.read(buffer);
                            if (len == -1) break;

                            try (DownloadManager.SafeRegion region = state.writing()) {
                                System.err.println("Write " + url + " segment " + segment + ",pos=" + position + ",len=" + len + ", segmented?=" + connectionSegmented);
                                downloader.write(position, buffer, 0, len);
                            }

                            position += len;

                            if (conn.getContentLength() >= 0) {
                                // Update progress information per second
                                segment.setCurrentPosition(position);
                            }

                            DownloadManager.updateDownloadSpeed(position - lastPosition);
                            lastPosition = position;
                        }

                        if (state.isCancelled()) break;

                        if (conn.getContentLength() >= 0 && !segment.isFinished())
                            throw new IOException("Unexpected segment size: " + (position - startPosition) + ", expected: " + segment.getLength());
                    }

                    segment.setConnection(conn);
                    task.onSegmentFinished(segment);

                    return;
                } catch (IOException ex) {
                    failedURL = lastURL;
                    exception = ex;
                    LOG.log(Level.WARNING, "Failed to download " + failedURL + ", repeat times: " + tryTime, ex);
                }
            }

            if (exception != null)
                throw new DownloadException(failedURL, exception);
        } catch (Throwable t) {
            task.onSegmentFailed(segment, t);
        }
    }
}
