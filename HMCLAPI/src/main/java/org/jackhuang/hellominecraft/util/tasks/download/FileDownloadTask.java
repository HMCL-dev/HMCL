/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.util.tasks.download;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.code.DigestUtils;
import org.jackhuang.hellominecraft.util.func.Function;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.tasks.communication.PreviousResult;
import org.jackhuang.hellominecraft.util.tasks.communication.PreviousResultRegistrar;
import org.jackhuang.hellominecraft.util.system.IOUtils;

/**
 *
 * @author huangyuhui
 */
// This class downloads a file from a URL.
public class FileDownloadTask extends Task implements PreviousResult<File>, PreviousResultRegistrar<String> {

    // Max size of download buffer.
    protected static final int MAX_BUFFER_SIZE = 2048;

    protected URL url; // download URL
    protected int downloaded = 0; // number of bytes downloaded
    protected File filePath;
    protected String expectedHash;

    protected Function<Integer, String> failedCallbackReturnsNewURL;

    public FileDownloadTask setFailedCallbackReturnsNewURL(Function<Integer, String> failedCallbackReturnsNewURL) {
        this.failedCallbackReturnsNewURL = failedCallbackReturnsNewURL;
        return this;
    }

    public FileDownloadTask() {
    }

    public FileDownloadTask(File filePath) {
        this((URL) null, filePath);
    }

    public FileDownloadTask(String url, File filePath) {
        this(IOUtils.parseURL(url), filePath);
    }

    public FileDownloadTask(URL url, File filePath) {
        this(url, filePath, null);
    }

    public FileDownloadTask(String url, File filePath, String hash) {
        this(IOUtils.parseURL(url), filePath, hash);
    }

    public FileDownloadTask(URL url, File file, String hash) {
        this.url = url;
        this.filePath = file;
        this.expectedHash = hash;
    }

    // Get this download's URL.
    public String getUrl() {
        return url.toString();
    }

    RandomAccessFile file = null;
    InputStream stream = null;
    boolean shouldContinue = true;

    private void closeFiles() {
        // Close file.
        if (file != null)
            try {
                file.close();
                file = null;
            } catch (IOException e) {
                HMCLog.warn("Failed to close file", e);
            }

        // Close connection to server.
        if (stream != null)
            try {
                stream.close();
                stream = null;
            } catch (IOException e) {
                HMCLog.warn("Failed to close stream", e);
            }
    }

    // Download file.
    @Override
    public void executeTask() throws Throwable {
        for (PreviousResult<String> p : al)
            this.url = IOUtils.parseURL(p.getResult());

        for (int repeat = 0; repeat < 6; repeat++) {
            if (repeat > 0) {
                HMCLog.warn("Failed to download, repeat: " + repeat);
                if (failedCallbackReturnsNewURL != null) {
                    URL tmp = IOUtils.parseURL(failedCallbackReturnsNewURL.apply(repeat));
                    if (tmp != null) {
                        url = tmp;
                        HMCLog.warn("Switch to: " + url);
                    }
                }
            }
            try {

                // Open connection to URL.
                HttpURLConnection connection
                                  = (HttpURLConnection) url.openConnection();

                connection.setConnectTimeout(5000);
                connection.setRequestProperty("User-Agent", "Hello Minecraft!");

                // Connect to server.
                connection.connect();

                // Make sure response code is in the 200 range.
                if (connection.getResponseCode() / 100 != 2)
                    throw new NetException(C.i18n("download.not_200") + " " + connection.getResponseCode());

                // Check for valid content length.
                int contentLength = connection.getContentLength();
                if (contentLength < 1)
                    throw new NetException("The content length is invalid.");

                filePath.getParentFile().mkdirs();

                File tempFile = new File(filePath.getAbsolutePath() + ".hmd");
                if (!tempFile.exists())
                    tempFile.createNewFile();

                // Open file and seek to the end of it.
                file = new RandomAccessFile(tempFile, "rw");

                MessageDigest digest = DigestUtils.getSha1Digest();

                stream = connection.getInputStream();
                int lastDownloaded = 0;
                downloaded = 0;
                long lastTime = System.currentTimeMillis();
                while (true) {
                    // Size buffer according to how much of the file is left to download.
                    if (!shouldContinue) {
                        closeFiles();
                        filePath.delete();
                        break;
                    }

                    byte buffer[] = new byte[MAX_BUFFER_SIZE];

                    // Read from server into buffer.
                    int read = stream.read(buffer);
                    if (read == -1)
                        break;

                    digest.update(buffer, 0, read);

                    // Write buffer to file.
                    file.write(buffer, 0, read);
                    downloaded += read;

                    long now = System.currentTimeMillis();
                    if (ppl != null && (now - lastTime) >= 1000) {
                        ppl.setProgress(this, downloaded, contentLength);
                        ppl.setStatus(this, (downloaded - lastDownloaded) / 1024 + "KB/s");
                        lastDownloaded = downloaded;
                        lastTime = now;
                    }
                }
                if (downloaded != contentLength)
                    throw new IllegalStateException("Unexptected file size: " + downloaded + ", expected: " + contentLength);
                closeFiles();
                if (aborted)
                    tempFile.delete();
                else {
                    if (filePath.exists())
                        filePath.delete();
                    tempFile.renameTo(filePath);
                }
                String hashCode = String.format("%1$040x", new Object[] { new BigInteger(1, digest.digest()) });
                if (expectedHash != null && !expectedHash.equals(hashCode))
                    throw new IllegalStateException("Unexpected hash code: " + hashCode + ", expected: " + expectedHash);

                if (ppl != null)
                    ppl.onProgressProviderDone(this);
                return;
            } catch (Exception e) {
                setFailReason(new NetException(C.i18n("download.failed") + " " + url, e));
            } finally {
                closeFiles();
            }
        }
        if (failReason != null)
            throw failReason;
    }

    public static void download(String url, String file, DownloadListener dl) throws Throwable {
        download(url, new File(file), dl);
    }

    public static void download(String url, File file, DownloadListener dl) throws Throwable {
        ((Task) new FileDownloadTask(url, file).setProgressProviderListener(dl)).executeTask();
    }

    @Override
    public boolean abort() {
        //for (Downloader d : downloaders) d.abort();
        shouldContinue = false;
        aborted = true;
        return true;
    }

    @Override
    public String getInfo() {
        return C.i18n("download") + ": " + url;
    }

    @Override
    public File getResult() {
        return filePath;
    }

    ArrayList<PreviousResult<String>> al = new ArrayList();

    @Override
    public Task registerPreviousResult(PreviousResult<String> pr) {
        al.add(pr);
        return this;
    }
}
