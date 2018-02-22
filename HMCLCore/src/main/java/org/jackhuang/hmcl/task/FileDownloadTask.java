/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.event.EventManager;
import org.jackhuang.hmcl.event.FailedEvent;
import org.jackhuang.hmcl.util.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.security.MessageDigest;
import java.util.logging.Level;

/**
 * A task that can download a file online.
 *
 * @author huangyuhui
 */
public class FileDownloadTask extends Task {

    private final URL url;
    private final File file;
    private final String hash;
    private final int retry;
    private final Proxy proxy;
    private final EventManager<FailedEvent<URL>> onFailed = new EventManager<>();
    private RandomAccessFile rFile;
    private InputStream stream;

    /**
     * @param url the URL of remote file.
     * @param file the location that download to.
     */
    public FileDownloadTask(URL url, File file) {
        this(url, file, Proxy.NO_PROXY);
    }

    /**
     * @param url the URL of remote file.
     * @param file the location that download to.
     * @param proxy the proxy.
     */
    public FileDownloadTask(URL url, File file, Proxy proxy) {
        this(url, file, proxy, null);
    }

    /**
     * @param url the URL of remote file.
     * @param file the location that download to.
     * @param proxy the proxy.
     * @param hash the SHA-1 hash code of remote file, null if the hash is unknown or it is no need to check the hash code.
     */
    public FileDownloadTask(URL url, File file, Proxy proxy, String hash) {
        this(url, file, proxy, hash, 5);
    }

    /**
     * @param url the URL of remote file.
     * @param file the location that download to.
     * @param hash the SHA-1 hash code of remote file, null if the hash is unknown or it is no need to check the hash code.
     * @param retry the times for retrying if downloading fails.
     * @param proxy the proxy.
     */
    public FileDownloadTask(URL url, File file, Proxy proxy, String hash, int retry) {
        this.url = url;
        this.file = file;
        this.hash = hash;
        this.retry = retry;
        this.proxy = proxy;

        setName(file.getName());
    }

    private void closeFiles() {
        IOUtils.closeQuietly(rFile);
        rFile = null;
        IOUtils.closeQuietly(stream);
        stream = null;
    }

    @Override
    public Scheduler getScheduler() {
        return Schedulers.io();
    }

    public EventManager<FailedEvent<URL>> getOnFailed() {
        return onFailed;
    }

    public URL getUrl() {
        return url;
    }

    public File getFile() {
        return file;
    }

    @Override
    public void execute() throws Exception {
        URL currentURL = url;
        Logging.LOG.log(Level.FINER, "Downloading {0} to {1}", new Object[] { currentURL, file });
        Exception exception = null;

        for (int repeat = 0; repeat < retry; repeat++) {
            if (repeat > 0) {
                FailedEvent<URL> event = new FailedEvent<>(this, repeat, currentURL);
                onFailed.fireEvent(event);
                currentURL = event.getNewResult();
            }
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                break;
            }

            File temp = null;

            try {
                updateProgress(0);

                HttpURLConnection con = NetworkUtils.createConnection(url, proxy);
                con.connect();

                if (con.getResponseCode() / 100 != 2)
                    throw new IOException("Server error, response code: " + con.getResponseCode());

                int contentLength = con.getContentLength();
                if (contentLength < 1)
                    throw new IOException("The content length is invalid.");

                if (!FileUtils.makeDirectory(file.getAbsoluteFile().getParentFile()))
                    throw new IOException("Could not make directory " + file.getAbsoluteFile().getParent());

                temp = FileUtils.createTempFile();
                rFile = new RandomAccessFile(temp, "rw");

                MessageDigest digest = DigestUtils.getSha1Digest();

                stream = con.getInputStream();
                int lastDownloaded = 0, downloaded = 0;
                long lastTime = System.currentTimeMillis();
                byte buffer[] = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                while (true) {
                    if (Thread.interrupted()) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    int read = stream.read(buffer);
                    if (read == -1)
                        break;

                    if (hash != null)
                        digest.update(buffer, 0, read);

                    // Write buffer to file.
                    rFile.write(buffer, 0, read);
                    downloaded += read;

                    // Update progress information per second
                    updateProgress(downloaded, contentLength);
                    long now = System.currentTimeMillis();
                    if (now - lastTime >= 1000) {
                        updateMessage((downloaded - lastDownloaded) / 1024 + "KB/s");
                        lastDownloaded = downloaded;
                        lastTime = now;
                    }
                }

                closeFiles();

                // Restore temp file to original name.
                if (Thread.interrupted()) {
                    temp.delete();
                    Thread.currentThread().interrupt();
                    break;
                } else {
                    if (file.exists() && !file.delete())
                        throw new IOException("Unable to delete existent file " + file);
                    if (!FileUtils.makeDirectory(file.getAbsoluteFile().getParentFile()))
                        throw new IOException("Unable to make parent directory " + file);
                    try {
                        FileUtils.moveFile(temp, file);
                    } catch (Exception e) {
                        throw new IOException("Unable to move temp file from " + temp + " to " + file, e);
                    }
                }

                if (downloaded != contentLength)
                    throw new IllegalStateException("Unexpected file size: " + downloaded + ", expected: " + contentLength);

                // Check hash code
                if (hash != null) {
                    String hashCode = String.format("%1$040x", new BigInteger(1, digest.digest()));
                    if (!hash.equalsIgnoreCase(hashCode))
                        throw new IllegalStateException("Unexpected hash code: " + hashCode + ", expected: " + hash);
                }

                return;
            } catch (IOException | IllegalStateException e) {
                if (temp != null)
                    temp.delete();
                Logging.LOG.log(Level.WARNING, "Unable to download file " + currentURL, e);
                exception = e;
            } finally {
                closeFiles();
            }
        }

        if (exception != null)
            throw exception;
    }

}
