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

import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.logging.Level;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.DigestUtils.getDigest;

/**
 * A task that can download a file online.
 *
 * @author huangyuhui
 */
public class FileDownloadTask extends DownloadManager.DownloadTask<Void> {

    public static class IntegrityCheck {
        private final String algorithm;
        private final String checksum;

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

    private final IntegrityCheck integrityCheck;
    private Path candidate;
    // destination
    private final Path file;
    // temporary location to save remote file, which will be removed after downloading finished.
    private final Path downloadingFile;
    private RandomAccessFile rFile;
    private final ArrayList<IntegrityCheckHandler> integrityCheckHandlers = new ArrayList<>();

    /**
     * @param url  the URL of remote file.
     * @param file the location that download to.
     */
    public FileDownloadTask(URL url, File file) {
        this(url, file, null);
    }

    /**
     * @param url            the URL of remote file.
     * @param file           the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     */
    public FileDownloadTask(URL url, File file, IntegrityCheck integrityCheck) {
        this(Collections.singletonList(url), file, integrityCheck);
    }

    /**
     * @param url            the URL of remote file.
     * @param file           the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     * @param retry          the times for retrying if downloading fails.
     */
    public FileDownloadTask(URL url, File file, IntegrityCheck integrityCheck, int retry) {
        this(Collections.singletonList(url), file, integrityCheck, retry);
    }

    /**
     * Constructor.
     *
     * @param urls urls of remote file, will be attempted in order.
     * @param file the location that download to.
     */
    public FileDownloadTask(List<URL> urls, File file) {
        this(urls, file, null);
    }

    /**
     * Constructor.
     *
     * @param urls           urls of remote file, will be attempted in order.
     * @param file           the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     */
    public FileDownloadTask(List<URL> urls, File file, IntegrityCheck integrityCheck) {
        this(urls, file, integrityCheck, 3);
    }

    /**
     * Constructor.
     *
     * @param urls           urls of remote file, will be attempted in order.
     * @param file           the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     * @param retry          the times for retrying if downloading fails.
     */
    public FileDownloadTask(List<URL> urls, File file, IntegrityCheck integrityCheck, int retry) {
        this(new DownloadManager.DownloadTaskStateBuilder()
                .setUrls(urls).setFile(file).setRetry(retry).build(), integrityCheck);
    }

    public FileDownloadTask(DownloadManager.DownloadTaskState state) {
        this(state, null);
    }

    public FileDownloadTask(DownloadManager.DownloadTaskState state, IntegrityCheck integrityCheck) {
        super(state);

        if (state.getFile() == null) {
            throw new IllegalArgumentException("FileDownloadTask requires a file path to save remote file");
        }

        this.integrityCheck = integrityCheck;
        this.file = state.getFile();
        this.downloadingFile = state.getDownloadingFile();

        setName(FileUtils.getName(state.getFile()));
    }

    /**
     * Set candidate that the content should equal to the file to be downloaded.
     * <p>
     * If candidate set and verified, the file will be taken, and download will not happen.
     *
     * @param candidate path to candidate
     */
    public void setCandidate(Path candidate) {
        this.candidate = candidate;
    }

    public void addIntegrityCheckHandler(IntegrityCheckHandler handler) {
        integrityCheckHandlers.add(Objects.requireNonNull(handler));
    }

    @Override
    protected DownloadManager.Downloader<Void> createDownloader() {
        return new DownloadManager.Downloader<Void>() {
            @Override
            protected EnumCheckETag shouldCheckETag() {
                // Check cache
                if (integrityCheck != null && caching) {
                    Optional<Path> cache = repository.checkExistentFile(candidate, integrityCheck.getAlgorithm(), integrityCheck.getChecksum());
                    if (cache.isPresent()) {
                        try {
                            FileUtils.copyFile(cache.get(), state.getFile());
                            Logging.LOG.log(Level.FINER, "Successfully verified file " + state.getFile() + " from " + state.getFirstUrl());
                            return EnumCheckETag.CACHED;
                        } catch (IOException e) {
                            Logging.LOG.log(Level.WARNING, "Failed to copy cache files", e);
                        }
                    }
                    return EnumCheckETag.NOT_CHECK_E_TAG;
                } else {
                    return EnumCheckETag.CHECK_E_TAG;
                }
            }

            @Override
            protected void onBeforeConnection(DownloadManager.DownloadSegment segment, URL url) {
                Logging.LOG.log(Level.FINER, "Downloading segment " + segment.getStartPosition() + "~" + segment.getEndPosition() + " of " + url + " to " + state.getFile());
            }

            @Override
            protected Void finishWithCachedResult(Path cache) throws IOException {
                FileUtils.copyFile(cache, state.getFile());
                return null;
            }

            @Override
            protected void write(long pos, byte[] buffer, int offset, int len) throws IOException {
                rFile.seek(pos);
                rFile.write(buffer, offset, len);
            }

            @Override
            protected void onStart() throws IOException {
                rFile = new RandomAccessFile(downloadingFile.toFile(), "rw");
            }

            @Override
            protected void onContentLengthChanged(long contentLength) throws IOException {
                if (contentLength > 0) {
                    rFile.setLength(contentLength);
                }
            }

            @Override
            public Void finish() throws IOException {
                try {
                    rFile.close();
                } catch (IOException e) {
                    Logging.LOG.log(Level.WARNING, "Failed to close file: " + rFile, e);
                }

                if (!state.isFinished()) {
                    try {
                        Files.delete(downloadingFile);
                    } catch (IOException e) {
                        Logging.LOG.log(Level.WARNING, "Failed to delete file: " + rFile, e);
                    }
                    return null;
                }

                // Integrity check
                if (integrityCheck != null) {
                    try (InputStream is = Files.newInputStream(downloadingFile)) {
                        MessageDigest digest = integrityCheck.createDigest();
                        DigestUtils.updateDigest(digest, is);
                        integrityCheck.performCheck(digest);
                    }

                    if (caching) {
                        try {
                            repository.cacheFile(downloadingFile, integrityCheck.getAlgorithm(), integrityCheck.getChecksum());
                        } catch (IOException e) {
                            Logging.LOG.log(Level.WARNING, "Failed to cache file", e);
                        }
                    }
                }

                for (IntegrityCheckHandler handler : integrityCheckHandlers) {
                    handler.checkIntegrity(downloadingFile, state.getFile());
                }

                Files.deleteIfExists(file);
                if (!FileUtils.makeDirectory(file.toAbsolutePath().getParent().toFile()))
                    throw new IOException("Unable to make parent directory " + state.getFile());

                try {
                    FileUtils.moveFile(downloadingFile.toFile(), file.toFile());
                } catch (Exception e) {
                    throw new IOException("Unable to move temp file from " + state.getDownloadingFile() + " to " + file, e);
                }

                if (getCheckETag() == EnumCheckETag.CHECK_E_TAG) {
                    repository.cacheRemoteFile(file, state.getSegments().get(0).getConnection());
                }

                return null;
            }
        };
    }

    public interface IntegrityCheckHandler {
        /**
         * Check whether the file is corrupted or not.
         *
         * @param filePath        the file locates in (maybe in temp directory)
         * @param destinationPath for real file name
         * @throws IOException if the file is corrupted
         */
        void checkIntegrity(Path filePath, Path destinationPath) throws IOException;
    }

    public static final IntegrityCheckHandler ZIP_INTEGRITY_CHECK_HANDLER = (filePath, destinationPath) -> {
        String ext = FileUtils.getExtension(destinationPath).toLowerCase();
        if (ext.equals("zip") || ext.equals("jar")) {
            try (FileSystem ignored = CompressingUtils.createReadOnlyZipFileSystem(filePath)) {
                // test for zip format
            }
        }
    };
}
