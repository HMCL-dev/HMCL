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
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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
    protected Context getContext(HttpResponse<?> response, boolean checkETag, String bmclapiHash) throws IOException {
        assert checkETag;
        assert response != null;

        Path temp = Files.createTempFile("hmcl-download-", null);
        OutputStream fileOutput = Files.newOutputStream(temp);

        return new Context() {
            @Override
            public void write(byte[] buffer, int offset, int len) throws IOException {
                fileOutput.write(buffer, offset, len);
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
                    setResult(repository.cacheRemoteFile(response, temp));
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
