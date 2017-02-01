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
package org.jackhuang.hellominecraft.util.net;

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
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.sys.FileUtils;
import org.jackhuang.hellominecraft.util.task.Task;
import org.jackhuang.hellominecraft.util.task.comm.PreviousResult;
import org.jackhuang.hellominecraft.util.task.comm.PreviousResultRegistrar;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 *
 * @author huangyuhui
 */
// This class downloads a file from a URL.
public class FileDownloadTask extends Task implements PreviousResult<File>, PreviousResultRegistrar<String> {

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
        IOUtils.closeQuietly(file);
        file = null;
        IOUtils.closeQuietly(stream);
        stream = null;
    }

    // Download file.
    @Override
    public void executeTask(boolean areDependTasksSucceeded) throws Throwable {
        for (PreviousResult<String> p : al)
            this.url = IOUtils.parseURL(p.getResult());

        for (int repeat = 0; repeat < 6; repeat++) {
            if (repeat > 0)
                if (failedCallbackReturnsNewURL != null) {
                    URL tmp = IOUtils.parseURL(failedCallbackReturnsNewURL.apply(repeat));
                    if (tmp != null) {
                        url = tmp;
                        HMCLog.warn("Switch to: " + url);
                    }
                }
            HMCLog.log("Downloading: " + url + " to: " + filePath);
            if (!shouldContinue)
                break;
            try {
                if (ppl != null)
                    ppl.setProgress(this, -1, 1);

                // Open connection to URL.
                HttpURLConnection con = (HttpURLConnection) url.openConnection();

                con.setDoInput(true);
                con.setConnectTimeout(15000);
                con.setReadTimeout(15000);
                con.setRequestProperty("User-Agent", "Hello Minecraft!");

                // Connect to server.
                con.connect();

                // Make sure response code is in the 200 range.
                if (con.getResponseCode() / 100 != 2)
                    throw new IOException(C.i18n("download.not_200") + " " + con.getResponseCode());

                // Check for valid content length.
                int contentLength = con.getContentLength();
                if (contentLength < 1)
                    throw new IOException("The content length is invalid.");

                if (!FileUtils.makeDirectory(filePath.getParentFile()))
                    throw new IOException("Could not make directory");

                // We use temp file to prevent files from aborting downloading and broken.
                File tempFile = new File(filePath.getAbsolutePath() + ".hmd");
                if (!tempFile.exists())
                    tempFile.createNewFile();
                else if (!tempFile.renameTo(tempFile)) // check file lock
                    throw new IllegalStateException("The temp file is locked, maybe there is an application using the file?");

                // Open file and seek to the end of it.
                file = new RandomAccessFile(tempFile, "rw");

                MessageDigest digest = DigestUtils.getSha1Digest();

                stream = con.getInputStream();
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

                    byte buffer[] = new byte[IOUtils.MAX_BUFFER_SIZE];

                    // Read from server into buffer.
                    int read = stream.read(buffer);
                    if (read == -1)
                        break;

                    if (expectedHash != null)
                        digest.update(buffer, 0, read);

                    // Write buffer to file.
                    file.write(buffer, 0, read);
                    downloaded += read;

                    // Update progress information per second
                    long now = System.currentTimeMillis();
                    if (ppl != null && (now - lastTime) >= 1000) {
                        ppl.setProgress(this, downloaded, contentLength);
                        ppl.setStatus(this, (downloaded - lastDownloaded) / 1024 + "KB/s");
                        lastDownloaded = downloaded;
                        lastTime = now;
                    }
                }
                closeFiles();
                
                // Restore temp file to original name.
                if (aborted)
                    tempFile.delete();
                else {
                    if (filePath.exists())
                        filePath.delete();
                    tempFile.renameTo(filePath);
                }
                if (!shouldContinue)
                    break;
                if (downloaded != contentLength)
                    throw new IllegalStateException("Unexptected file size: " + downloaded + ", expected: " + contentLength);

                // Check hash code
                String hashCode = String.format("%1$040x", new BigInteger(1, digest.digest()));
                if (expectedHash != null && !expectedHash.equals(hashCode))
                    throw new IllegalStateException("Unexpected hash code: " + hashCode + ", expected: " + expectedHash);

                if (ppl != null)
                    ppl.onProgressProviderDone(this);
                return;
            } catch (IOException | IllegalStateException e) {
                setFailReason(new IOException(C.i18n("download.failed") + " " + url, e));
            } finally {
                closeFiles();
            }
        }
        if (failReason != null)
            throw failReason;
    }

    @Override
    public boolean abort() {
        shouldContinue = false;
        aborted = true;
        return true;
    }

    @Override
    public String getInfo() {
        return C.i18n("download") + ": " + (tag == null ? url : tag);
    }

    @Override
    public File getResult() {
        return filePath;
    }

    ArrayList<PreviousResult<String>> al = new ArrayList<>();

    @Override
    public Task registerPreviousResult(PreviousResult<String> pr) {
        al.add(pr);
        return this;
    }
}
