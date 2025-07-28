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

import org.jackhuang.hmcl.util.Hex;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.DigestUtils.getDigest;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * A task that can download a file online.
 *
 * @author huangyuhui
 */
public class FileDownloadTask extends FetchTask<Void> {

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
            String actualChecksum = Hex.encodeHex(digest.digest());
            if (!checksum.equalsIgnoreCase(actualChecksum)) {
                throw new ChecksumMismatchException(algorithm, checksum, actualChecksum);
            }
        }
    }

    private final Path file;
    private final IntegrityCheck integrityCheck;
    private Path candidate;
    private final ArrayList<IntegrityCheckHandler> integrityCheckHandlers = new ArrayList<>();

    /**
     * @param uri the URI of remote file.
     * @param path the location that download to.
     */
    public FileDownloadTask(URI uri, Path path) {
        this(uri, path, null);
    }

    /**
     * @param uri the URI of remote file.
     * @param path the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     */
    public FileDownloadTask(URI uri, Path path, IntegrityCheck integrityCheck) {
        this(List.of(uri), path, integrityCheck);
    }

    /**
     * @param uri the URI of remote file.
     * @param path the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     * @param retry the times for retrying if downloading fails.
     */
    public FileDownloadTask(URI uri, Path path, IntegrityCheck integrityCheck, int retry) {
        this(List.of(uri), path, integrityCheck, retry);
    }

    /**
     * Constructor.
     * @param uris uris of remote file, will be attempted in order.
     * @param file the location that download to.
     */
    public FileDownloadTask(List<URI> uris, Path file) {
        this(uris, file, null);
    }

    /**
     * Constructor.
     * @param uris uris of remote file, will be attempted in order.
     * @param path the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     */
    public FileDownloadTask(List<URI> uris, Path path, IntegrityCheck integrityCheck) {
        this(uris, path, integrityCheck, 3);
    }

    /**
     * Constructor.
     * @param uris uris of remote file, will be attempted in order.
     * @param path the location that download to.
     * @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
     * @param retry the times for retrying if downloading fails.
     */
    public FileDownloadTask(List<URI> uris, Path path, IntegrityCheck integrityCheck, int retry) {
        super(uris, retry);
        this.file = path;
        this.integrityCheck = integrityCheck;

        setName(path.getFileName().toString());
    }

    public Path getPath() {
        return file;
    }

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
                    FileUtils.copyFile(cache.get(), file);
                    LOG.trace("Successfully verified file " + file + " from " + uris.get(0));
                    return EnumCheckETag.CACHED;
                } catch (IOException e) {
                    LOG.warning("Failed to copy cache files", e);
                }
            }
            return EnumCheckETag.NOT_CHECK_E_TAG;
        } else {
            return EnumCheckETag.CHECK_E_TAG;
        }
    }

    @Override
    protected void beforeDownload(URI uri) {
        LOG.trace("Downloading " + uri + " to " + file);
    }

    @Override
    protected void useCachedResult(Path cache) throws IOException {
        FileUtils.copyFile(cache, file);
    }

    @Override
    protected Context getContext(@Nullable HttpResponse<?> response, boolean checkETag) throws IOException {
        Path temp = Files.createTempFile(null, null);
        RandomAccessFile rFile = new RandomAccessFile(temp.toFile(), "rw");
        MessageDigest digest = integrityCheck == null ? null : integrityCheck.createDigest();

        return new Context() {
            @Override
            public void write(byte[] buffer, int offset, int len) throws IOException {
                if (digest != null) {
                    digest.update(buffer, offset, len);
                }

                rFile.write(buffer, offset, len);
            }

            @Override
            public void close() throws IOException {
                try {
                    rFile.close();
                } catch (IOException e) {
                    LOG.warning("Failed to close file: " + rFile, e);
                }

                if (!isSuccess()) {
                    try {
                        Files.delete(temp);
                    } catch (IOException e) {
                        LOG.warning("Failed to delete file: " + rFile, e);
                    }
                    return;
                }

                for (IntegrityCheckHandler handler : integrityCheckHandlers) {
                    handler.checkIntegrity(temp, file);
                }

                Files.deleteIfExists(file);
                if (!FileUtils.makeDirectory(file.toFile().getAbsoluteFile().getParentFile()))
                    throw new IOException("Unable to make parent directory " + file);

                try {
                    FileUtils.moveFile(temp.toFile(), file.toFile()); // TODO
                } catch (Exception e) {
                    throw new IOException("Unable to move temp file from " + temp + " to " + file, e);
                }

                // Integrity check
                if (integrityCheck != null) {
                    integrityCheck.performCheck(digest);
                }

                if (caching && integrityCheck != null) {
                    try {
                        repository.cacheFile(file, integrityCheck.getAlgorithm(), integrityCheck.getChecksum());
                    } catch (IOException e) {
                        LOG.warning("Failed to cache file", e);
                    }
                }

                if (checkETag) {
                    repository.cacheRemoteFile(response, file);
                }
            }
        };
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
        String ext = FileUtils.getExtension(destinationPath).toLowerCase(Locale.ROOT);
        if (ext.equals("zip") || ext.equals("jar")) {
            try (FileSystem ignored = CompressingUtils.createReadOnlyZipFileSystem(filePath)) {
                // test for zip format
            }
        }
    };
}
