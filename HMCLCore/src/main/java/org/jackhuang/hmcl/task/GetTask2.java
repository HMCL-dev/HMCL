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

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author huangyuhui
 */
public final class GetTask2 extends FetchTask2<String> {

    private static final int DEFAULT_RETRY = 3;

    private final Charset charset;

    public GetTask2(URI url) {
        this(url, UTF_8);
    }

    public GetTask2(URI url, Charset charset) {
        this(url, charset, DEFAULT_RETRY);
    }

    public GetTask2(URI url, Charset charset, int retry) {
        this(List.of(url), charset, retry);
    }

    public GetTask2(List<URI> url) {
        this(url, UTF_8, DEFAULT_RETRY);
    }

    public GetTask2(List<URI> urls, Charset charset, int retry) {
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
    protected Context getContext(@Nullable HttpResponse<?> response, boolean checkETag) {
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
                    repository.cacheText(response, result);
                }
            }
        };
    }

}
