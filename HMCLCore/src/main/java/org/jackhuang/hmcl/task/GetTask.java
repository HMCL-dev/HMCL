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

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author huangyuhui
 */
public final class GetTask extends FetchTask<String> {

    private final Charset charset;

    public GetTask(URI url) {
        this(url, UTF_8);
    }

    public GetTask(URI url, Charset charset) {
        this(url, charset, DEFAULT_RETRY);
    }

    public GetTask(URI url, Charset charset, int retry) {
        this(List.of(url), charset, retry);
    }

    public GetTask(List<URI> url) {
        this(url, UTF_8, DEFAULT_RETRY);
    }

    public GetTask(List<URI> urls, Charset charset, int retry) {
        super(urls, retry);
        this.charset = charset;

        setName(urls.get(0).toString());
    }

    @Override
    protected EnumCheckETag shouldCheckETag() {
        return EnumCheckETag.CHECK_E_TAG;
    }

    @Override
    protected void useCachedResult(Path cachedFile) throws IOException {
        setResult(Files.readString(cachedFile));
    }

    @Override
    protected Context getContext(URLConnection connection, boolean checkETag, String bmclapiHash) {
        return new Context() {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();

            @Override
            public void write(byte[] buffer, int offset, int len) {
                baos.write(buffer, offset, len);
            }

            @Override
            public void close() throws IOException {
                if (!isSuccess()) return;

                String result = baos.toString(charset);
                setResult(result);

                if (checkETag) {
                    repository.cacheText(connection, result);
                }
            }
        };
    }

    public <T> Task<T> thenGetJsonAsync(Class<T> type) {
        return thenGetJsonAsync(TypeToken.get(type));
    }

    public <T> Task<T> thenGetJsonAsync(TypeToken<T> type) {
        return thenApplyAsync(jsonString -> JsonUtils.fromNonNullJson(jsonString, type));
    }
}
