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
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.UrlResponseInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Download a file to cache repository.
 *
 * @author Glavo
 */
public final class CacheFileTask extends FetchTask<Path> {

    public CacheFileTask(@NotNull String uri) {
        this(NetworkUtils.toURI(uri));
    }

    public CacheFileTask(@NotNull URI uri) {
        super(List.of(uri));
        setName(uri.toString());

        if (!NetworkUtils.isHttpUri(uri))
            throw new IllegalArgumentException(uri.toString());
    }

    @Override
    protected EnumCheckETag shouldCheckETag() {
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

    @Override
    protected Context getContext(@Nullable HttpResponse<?> response, boolean checkETag, String bmclapiHash) throws IOException {
        assert checkETag;
        assert response != null;

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
                }

                if (!isSuccess()) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException e) {
                        LOG.warning("Failed to delete file: " + temp, e);
                    }
                    return;
                }

                try {
                    setResult(repository.cacheRemoteFile(UrlResponseInfo.of(response), temp));
                } finally {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException e) {
                        LOG.warning("Failed to delete file: " + temp, e);
                    }
                }
            }
        };
    }
}
