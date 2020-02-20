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
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.io.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.DigestUtils.getDigest;

/**
 * A task that can download a file online.
 *
 * @author huangyuhui
 */
public class FileDownloadTask extends Task<Void> {

    public static class IntegrityCheck {
        private String algorithm;
        private String checksum;

        public IntegrityCheck(String algorithm, String checksum) {
            this.algorithm = requireNonNull(algorithm);
            this.checksum = requireNonNull(checksum);
        }

        public static IntegrityCheck of(String algorithm, String checksum) {
            if (checksum == null) return null;
            else return new IntegrityCheck(algorithm, checksum);
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getChecksum() {
            return checksum;
        }

        public MessageDigest createDigest() {
            return getDigest(algorithm);
        }

        public void performCheck(MessageDigest digest) throws ChecksumMismatchException {
            String actualChecksum = String.format("%1$040x", new BigInteger(1, digest.digest()));
            if (!checksum.equalsIgnoreCase(actualChecksum)) {
                throw new ChecksumMismatchException(algorithm, checksum, actualChecksum);
            }
        }
    }

    private final List<URL> urls;
    private final File file;
    private final IntegrityCheck integrityCheck;
    private final int retry;
    private Path candidate;
    private boolean caching;
    private CacheRepository repository = CacheRepository.getInstance();
    private RandomAccessFile rFile;
    private InputStream stream;

    /**
     * @param url the URL of remote file.
     * @param file the location that download to.
     */
    public FileDownloadTask(URL url, File file) {
        this(url, file, null);
    }

    /**
     * @param url the URL of remote file.
     * @param file the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     */
    public FileDownloadTask(URL url, File file, IntegrityCheck integrityCheck) {
        this(Collections.singletonList(url), file, integrityCheck);
    }

    /**
     * @param url the URL of remote file.
     * @param file the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     * @param retry the times for retrying if downloading fails.
     */
    public FileDownloadTask(URL url, File file, IntegrityCheck integrityCheck, int retry) {
        this(Collections.singletonList(url), file, integrityCheck, retry);
    }

    /**
     * Constructor.
     * @param urls urls of remote file, will be attempted in order.
     * @param file the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     */
    public FileDownloadTask(List<URL> urls, File file, IntegrityCheck integrityCheck) {
        this(urls, file, integrityCheck, 3);
    }

    /**
     * Constructor.
     * @param urls urls of remote file, will be attempted in order.
     * @param file the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     * @param retry the times for retrying if downloading fails.
     */
    public FileDownloadTask(List<URL> urls, File file, IntegrityCheck integrityCheck, int retry) {
        if (urls == null || urls.isEmpty())
            throw new IllegalArgumentException("At least one URL is required");

        this.urls = new ArrayList<>(urls);
        this.file = file;
        this.integrityCheck = integrityCheck;
        this.retry = retry;

        setName(file.getName());
        setExecutor(Schedulers.io());
    }

    private void closeFiles() {
        if (rFile != null)
            try {
                rFile.close();
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to close file: " + rFile, e);
            }

        rFile = null;

        if (stream != null)
            try {
                stream.close();
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to close stream", e);
            }
        stream = null;
    }

    public File getFile() {
        return file;
    }

    public FileDownloadTask setCandidate(Path candidate) {
        this.candidate = candidate;
        return this;
    }

    public FileDownloadTask setCaching(boolean caching) {
        this.caching = caching;
        return this;
    }

    public FileDownloadTask setCacheRepository(CacheRepository repository) {
        this.repository = repository;
        return this;
    }

    @Override
    public void execute() throws Exception {
        boolean checkETag;
        // Check cache
        if (integrityCheck != null && caching) {
            checkETag = false;
            Optional<Path> cache = repository.checkExistentFile(candidate, integrityCheck.getAlgorithm(), integrityCheck.getChecksum());
            if (cache.isPresent()) {
                try {
                    FileUtils.copyFile(cache.get().toFile(), file);
                    Logging.LOG.log(Level.FINER, "Successfully verified file " + file + " from " + urls.get(0));
                    return;
                } catch (IOException e) {
                    Logging.LOG.log(Level.WARNING, "Failed to copy cache files", e);
                }
            }
        } else {
            checkETag = true;
        }

        Logging.LOG.log(Level.FINER, "Downloading " + urls.get(0) + " to " + file);
        Exception exception = null;

        for (int repeat = 0; repeat < retry * urls.size(); repeat++) {
            URL url = urls.get(repeat / retry);
            if (isCancelled()) {
                break;
            }

            Path temp = null;

            try {
                updateProgress(0);

                HttpURLConnection con = NetworkUtils.createConnection(url);
                if (checkETag) repository.injectConnection(con);
                con = NetworkUtils.resolveConnection(con);

                if (con.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    // Handle cache
                    try {
                        Path cache = repository.getCachedRemoteFile(con);
                        FileUtils.copyFile(cache.toFile(), file);
                        return;
                    } catch (IOException e) {
                        Logging.LOG.log(Level.WARNING, "Unable to use cached file, redownload it", e);
                        repository.removeRemoteEntry(con);
                        // Now we must reconnect the server since 304 may result in empty content,
                        // if we want to redownload the file, we must reconnect the server without etag settings.
                        repeat--;
                        continue;
                    }
                } else if (con.getResponseCode() / 100 != 2) {
                    throw new ResponseCodeException(url, con.getResponseCode());
                }

                int contentLength = con.getContentLength();
                if (contentLength < 0)
                    throw new IOException("The content length is invalid.");

                if (!FileUtils.makeDirectory(file.getAbsoluteFile().getParentFile()))
                    throw new IOException("Could not make directory " + file.getAbsoluteFile().getParent());

                temp = Files.createTempFile(null, null);
                rFile = new RandomAccessFile(temp.toFile(), "rw");

                MessageDigest digest = integrityCheck == null ? null : integrityCheck.createDigest();

                stream = con.getInputStream();
                int lastDownloaded = 0, downloaded = 0;
                byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
                while (true) {
                    if (isCancelled()) {
                        break;
                    }

                    int read = stream.read(buffer);
                    if (read == -1)
                        break;

                    if (digest != null) {
                        digest.update(buffer, 0, read);
                    }

                    // Write buffer to file.
                    rFile.write(buffer, 0, read);
                    downloaded += read;

                    // Update progress information per second
                    updateProgress(downloaded, contentLength);

                    updateDownloadSpeed(downloaded - lastDownloaded);
                    lastDownloaded = downloaded;
                }

                updateDownloadSpeed(downloaded - lastDownloaded);

                closeFiles();

                // Restore temp file to original name.
                if (isCancelled()) {
                    temp.toFile().delete();
                    break;
                } else {
                    Files.deleteIfExists(file.toPath());
                    if (!FileUtils.makeDirectory(file.getAbsoluteFile().getParentFile()))
                        throw new IOException("Unable to make parent directory " + file);
                    try {
                        FileUtils.moveFile(temp.toFile(), file);
                    } catch (Exception e) {
                        throw new IOException("Unable to move temp file from " + temp + " to " + file, e);
                    }
                }

                if (downloaded != contentLength)
                    throw new IOException("Unexpected file size: " + downloaded + ", expected: " + contentLength);

                // Integrity check
                if (integrityCheck != null) {
                    integrityCheck.performCheck(digest);
                }

                if (caching && integrityCheck != null) {
                    try {
                        repository.cacheFile(file.toPath(), integrityCheck.getAlgorithm(), integrityCheck.getChecksum());
                    } catch (IOException e) {
                        Logging.LOG.log(Level.WARNING, "Failed to cache file", e);
                    }
                }

                if (checkETag) {
                    repository.cacheRemoteFile(file.toPath(), con);
                }

                return;
            } catch (IOException e) {
                if (temp != null)
                    temp.toFile().delete();
                exception = e;
                Logging.LOG.log(Level.WARNING, "Failed to download " + url + ", repeat times: " + (repeat + 1), e);
            } finally {
                closeFiles();
            }
        }

        if (exception != null)
            throw new DownloadException(urls.get(0), exception);
    }

    private static final Timer timer = new Timer("DownloadSpeedRecorder", true);
    private static final AtomicInteger downloadSpeed = new AtomicInteger(0);
    public static final EventBus speedEvent = new EventBus();

    static {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                speedEvent.channel(SpeedEvent.class).fireEvent(new SpeedEvent(speedEvent, downloadSpeed.getAndSet(0)));
            }
        }, 0, 1000);
    }

    private static void updateDownloadSpeed(int speed) {
        downloadSpeed.addAndGet(speed);
    }

    public static class SpeedEvent extends Event {
        private final int speed;

        public SpeedEvent(Object source, int speed) {
            super(source);

            this.speed = speed;
        }

        /**
         * Download speed in byte/sec.
         * @return
         */
        public int getSpeed() {
            return speed;
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this).append("speed", speed).toString();
        }
    }

}
