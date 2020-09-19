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
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
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
    private RandomAccessFile rFile;
    private final ArrayList<IntegrityCheckHandler> integrityCheckHandlers = new ArrayList<>();

    public FileDownloadTask(DownloadManager.DownloadTaskState state) {
        this(state, null);
    }

    public FileDownloadTask(DownloadManager.DownloadTaskState state, IntegrityCheck integrityCheck) {
        super(state);
        this.integrityCheck = integrityCheck;

        setName(FileUtils.getName(state.getFile()));
    }

    /**
     * Set candidate that the content should equal to the file to be downloaded.
     *
     * If candidate set and verified, the file will be taken, and download will not happen.
     *
     * @param candidate path to candidate
     * @return this
     */
    public FileDownloadTask setCandidate(Path candidate) {
        this.candidate = candidate;
        return this;
    }

    public void addIntegrityCheckHandler(IntegrityCheckHandler handler) {
        integrityCheckHandlers.add(Objects.requireNonNull(handler));
    }

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
    protected void onBeforeConnection(URL url) {
        Logging.LOG.log(Level.FINER, "Downloading " + url + " to " + state.getFile());
    }

    @Override
    protected void finishWithCachedResult(Path cache) throws IOException {
        FileUtils.copyFile(cache, state.getFile());
    }

    @Override
    protected void write(byte[] buffer, int offset, int len) throws IOException {
        rFile.write(buffer, offset, len);
    }

    @Override
    protected void onStart() throws IOException {
        rFile = new RandomAccessFile(state.getDownloadingFile().toFile(), "rw");
    }

    @Override
    public void finish() throws IOException {
        MessageDigest digest = integrityCheck == null ? null : integrityCheck.createDigest();
        // TODO: digest

        try {
            rFile.close();
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Failed to close file: " + rFile, e);
        }

        if (!state.isFinished()) {
            try {
                Files.delete(state.getDownloadingFile());
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to delete file: " + rFile, e);
            }
            return;
        }

        for (IntegrityCheckHandler handler : integrityCheckHandlers) {
            handler.checkIntegrity(state.getDownloadingFile(), state.getFile());
        }

        Files.deleteIfExists(state.getFile());
        if (!FileUtils.makeDirectory(state.getFile().toAbsolutePath().getParent().toFile()))
            throw new IOException("Unable to make parent directory " + state.getFile());

        try {
            FileUtils.moveFile(state.getDownloadingFile().toFile(), state.getFile().toFile());
        } catch (Exception e) {
            throw new IOException("Unable to move temp file from " + state.getDownloadingFile() + " to " + state.getFile(), e);
        }

        // Integrity check
        if (integrityCheck != null) {
            integrityCheck.performCheck(digest);
        }

        if (caching && integrityCheck != null) {
            try {
                repository.cacheFile(state.getFile(), integrityCheck.getAlgorithm(), integrityCheck.getChecksum());
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to cache file", e);
            }
        }

        if (getCheckETag() == EnumCheckETag.CHECK_E_TAG) {
            repository.cacheRemoteFile(state.getFile(), state.getSegments().get(0).getConnection());
        }
    }

    public interface IntegrityCheckHandler {
        /**
         * Check whether the file is corrupted or not.
         * @param filePath the file locates in (maybe in temp directory)
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
