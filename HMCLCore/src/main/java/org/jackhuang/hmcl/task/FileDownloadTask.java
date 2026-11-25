// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.UrlResponseInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// A task that can download a file online.
///
/// @author huangyuhui
public class FileDownloadTask extends FetchTask<@Nullable Void> {

    public record IntegrityCheck(String algorithm, String checksum) {
        public IntegrityCheck(String algorithm, String checksum) {
            this.algorithm = requireNonNull(algorithm);
            this.checksum = requireNonNull(checksum);
        }

        public static IntegrityCheck of(String algorithm, String checksum) {
            if (checksum == null) return null;
            else return new IntegrityCheck(algorithm, checksum);
        }

        @Override
        public @NotNull String toString() {
            return String.format("IntegrityCheck[algorithm='%s', checksum='%s']", algorithm, checksum);
        }
    }

    private final Path file;
    /// Optional expected checksum for downloaded and cached content.
    private final @Nullable IntegrityCheck integrityCheck;
    private boolean caching;
    private Path candidate;
    private final ArrayList<IntegrityCheckHandler> integrityCheckHandlers = new ArrayList<>();

    /// Creates a download task for an absolute URL string.
    ///
    /// @param url  the URL of remote file.
    /// @param path the location that download to.
    public FileDownloadTask(String url, Path path) {
        this(List.of(WebURL.parse(url)), path, null);
    }

    /// Creates a download task with an optional integrity check for an absolute URL string.
    ///
    /// @param url            the URL of remote file.
    /// @param path           the location that download to.
    /// @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
    public FileDownloadTask(String url, Path path, @Nullable IntegrityCheck integrityCheck) {
        this(List.of(WebURL.parse(url)), path, integrityCheck);
    }

    /// Creates a download task with a snapshot of nonempty candidate URLs.
    ///
    /// @param urls candidate URLs of the remote file, attempted in order
    /// @param file the location that download to.
    public FileDownloadTask(List<WebURL> urls, Path file) {
        this(urls, file, null);
    }

    /// Creates a download task with a snapshot of nonempty candidates and an optional integrity check.
    ///
    /// @param urls           candidate URLs of the remote file, attempted in order
    /// @param path           the location that download to.
    /// @param integrityCheck the integrity check to perform, null if no integrity check is to be performed
    public FileDownloadTask(List<WebURL> urls, Path path, @Nullable IntegrityCheck integrityCheck) {
        super(DownloadCandidates.ofUrls(urls));
        this.file = path;
        this.integrityCheck = integrityCheck;

        setName(path.getFileName().toString());
    }

    public FileDownloadTask(DownloadCandidates candidates, Path file) {
        this(candidates, file, null);
    }

    public FileDownloadTask(DownloadCandidates candidates, Path path, @Nullable IntegrityCheck integrityCheck) {
        super(candidates);
        this.file = path;
        this.integrityCheck = integrityCheck;

        setName(path.getFileName().toString());
    }

    public Path getPath() {
        return file;
    }

    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    public FileDownloadTask setCandidate(Path candidate) {
        this.candidate = candidate;
        return this;
    }

    public void addIntegrityCheckHandler(IntegrityCheckHandler handler) {
        integrityCheckHandlers.add(Objects.requireNonNull(handler));
    }

    @Override
    protected @Nullable Path findCachedFile() {
        if (integrityCheck == null || !caching) return null;
        return repository.checkExistentFile(candidate, integrityCheck.algorithm(), integrityCheck.checksum()).orElse(null);
    }

    @Override
    protected boolean shouldCheckETag() {
        return integrityCheck == null || !caching;
    }

    @Override
    protected void beforeDownload(WebURL url) {
        LOG.trace("Downloading " + url + " to " + file);
    }

    @Override
    protected @Nullable Void useCachedResult(Path cache) throws IOException {
        // A URL cache entry can predate the checksum requested by this download.
        if (!caching && integrityCheck != null && !integrityCheck.checksum().isEmpty())
            ChecksumMismatchException.verifyChecksum(cache, integrityCheck.algorithm(), integrityCheck.checksum());
        FileUtils.copyFile(cache, file);
        return null;
    }

    @Override
    protected Context<@Nullable Void> getContext(@Nullable UrlResponseInfo response, boolean checkETag, @Nullable String bmclapiHash) throws IOException {
        @Nullable String algorithm;
        @Nullable String checksum;
        if (integrityCheck != null) {
            algorithm = integrityCheck.algorithm();
            checksum = integrityCheck.checksum();
        } else if (bmclapiHash != null && DigestUtils.isSha1Digest(bmclapiHash)) {
            algorithm = "SHA-1";
            checksum = bmclapiHash;
        } else {
            algorithm = null;
            checksum = null;
        }

        @Nullable MessageDigest digest = algorithm != null ? DigestUtils.getDigest(algorithm) : null;

        return new FileContext<>(digest) {
            @Override
            @Nullable Void commit(Path temp) throws IOException {
                for (IntegrityCheckHandler handler : integrityCheckHandlers) {
                    handler.checkIntegrity(temp, file);
                }

                if (checksum != null && !checksum.isEmpty()) {
                    String actualChecksum = HexFormat.of().formatHex(digest.digest());
                    if (!checksum.equalsIgnoreCase(actualChecksum)) {
                        throw new ChecksumMismatchException(algorithm, checksum, actualChecksum);
                    }
                }

                Files.createDirectories(file.toAbsolutePath().getParent());

                try {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    throw new IOException("Unable to move temp file from " + temp + " to " + file, e);
                }

                if (caching && algorithm != null) {
                    try {
                        repository.cacheFile(file, algorithm, checksum);
                    } catch (IOException e) {
                        LOG.warning("Failed to cache file", e);
                    }
                }

                if (checkETag) {
                    repository.cacheRemoteFile(response, file);
                }
                return null;
            }
        };
    }

    public interface IntegrityCheckHandler {
        /// Check whether the file is corrupted or not.
        ///
        /// @param filePath        the file locates in (maybe in temp directory)
        /// @param destinationPath for real file name
        /// @throws IOException if the file is corrupted
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
