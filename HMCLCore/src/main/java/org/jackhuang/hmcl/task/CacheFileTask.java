/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.UrlResponseInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Downloads a remote file to a cache repository.
///
/// @author Glavo
public final class CacheFileTask extends FetchTask<Path> {

    /// Expected SHA-1 checksum, or `null` when the remote cache policy determines reuse.
    private final @Nullable String expectedSha1;

    /// Creates a task for one URI string using remote cache metadata.
    ///
    /// @param uri the HTTP or HTTPS URI string
    public CacheFileTask(@NotNull String uri) {
        this(NetworkUtils.toURI(uri));
    }

    /// Creates a task for one URI using remote cache metadata.
    ///
    /// @param uri the HTTP or HTTPS URI
    public CacheFileTask(@NotNull URI uri) {
        this(List.of(uri));
    }

    /// Creates a task for candidate URIs using remote cache metadata.
    ///
    /// @param uris candidate download URIs in attempt order
    public CacheFileTask(@NotNull List<@NotNull URI> uris) {
        super(uris);
        this.expectedSha1 = null;
        validateUris(uris);
        setName(uris.get(0).toString());
    }

    /// Creates a task that returns content cached under a verified SHA-1 checksum.
    ///
    /// @param uris         candidate download URIs in attempt order
    /// @param expectedSha1 the expected SHA-1 checksum
    public CacheFileTask(
            @NotNull List<@NotNull URI> uris,
            @NotNull String expectedSha1) {
        super(uris);
        if (!DigestUtils.isSha1Digest(expectedSha1)) {
            throw new IllegalArgumentException("Invalid SHA-1 checksum: " + expectedSha1);
        }
        this.expectedSha1 = expectedSha1.toLowerCase(Locale.ROOT);
        validateUris(uris);
        setName(uris.get(0).toString());
    }

    /// Verifies that all candidate URIs use HTTP or HTTPS.
    ///
    /// @param uris the candidate URIs
    private static void validateUris(@NotNull List<@NotNull URI> uris) {
        if (!uris.stream().allMatch(NetworkUtils::isHttpUri)) {
            throw new IllegalArgumentException(uris.toString());
        }
    }

    /// Selects a verified content-addressed entry or the applicable remote-cache policy.
    ///
    /// @return the cache action to perform before downloading
    @Override
    protected EnumCheckETag shouldCheckETag() {
        if (expectedSha1 != null) {
            Optional<Path> cached = repository.checkExistentFile(
                    null, CacheRepository.SHA1, expectedSha1);
            if (cached.isPresent()) {
                setResult(cached.get());
                LOG.info("Using cached file with SHA-1 " + expectedSha1);
                return EnumCheckETag.CACHED;
            }
            return EnumCheckETag.NOT_CHECK_E_TAG;
        }

        // Check cache
        for (URI uri : uris) {
            try {
                setResult(repository.getCachedRemoteFile(uri, true));
                LOG.info("Using cached file for " + NetworkUtils.dropQuery(uri));
                return EnumCheckETag.CACHED;
            } catch (CacheRepository.CacheExpiredException e) {
                LOG.info("Cache expired for " + NetworkUtils.dropQuery(uri));
            } catch (IOException ignored) {
            }
        }
        return EnumCheckETag.CHECK_E_TAG;
    }

    @Override
    protected void useCachedResult(Path cache) {
        setResult(cache);
    }

    /// Creates a temporary sink that publishes a successful download to the cache repository.
    ///
    /// @param response     the HTTP response metadata
    /// @param checkETag    whether remote cache metadata is being checked
    /// @param bmclapiHash  the hash supplied by BMCLAPI, or `null`
    /// @return the temporary download sink
    /// @throws IOException if the temporary file cannot be created
    @Override
    protected Context getContext(@Nullable UrlResponseInfo response, boolean checkETag, @Nullable String bmclapiHash) throws IOException {
        if (expectedSha1 == null && (!checkETag || response == null)) {
            throw new IOException("Remote response metadata is unavailable");
        }

        return new Context() {
            private final Path temp = Files.createTempFile("hmcl-download-", null);
            private final FileChannel fileOutput = FileChannel.open(temp,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE);

            @Override
            public void reset() throws IOException {
                fileOutput.truncate(0L);
            }

            @Override
            public void write(byte[] buffer, int offset, int len) throws IOException {
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, offset, len);
                while (byteBuffer.hasRemaining()) {
                    //noinspection ResultOfMethodCallIgnored
                    fileOutput.write(byteBuffer);
                }
            }

            @Override
            public void close() throws IOException {
                try {
                    fileOutput.close();
                } catch (IOException e) {
                    LOG.warning("Failed to close file: " + temp, e);
                    deleteTempFile();
                    throw e;
                }

                if (!isSuccess()) {
                    deleteTempFile();
                    return;
                }

                try {
                    if (expectedSha1 != null) {
                        ChecksumMismatchException.verifyChecksum(
                                temp, CacheRepository.SHA1, expectedSha1);
                        setResult(repository.cacheFile(
                                temp, CacheRepository.SHA1, expectedSha1));
                    } else {
                        setResult(repository.cacheRemoteFile(
                                Objects.requireNonNull(response), temp));
                    }
                } finally {
                    deleteTempFile();
                }
            }

            private void deleteTempFile() {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    LOG.warning("Failed to delete file: " + temp, e);
                }
            }
        };
    }
}
