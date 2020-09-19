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

import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author huangyuhui
 */
public final class GetTask extends DownloadManager.DownloadTask<String> {

    private final Charset charset;
    private ByteArrayOutputStream baos;

    public GetTask(DownloadManager.DownloadTaskState state) {
        this(state, UTF_8);
    }

    public GetTask(DownloadManager.DownloadTaskState state, Charset charset) {
        super(state);
        this.charset = charset;

        setName(state.getFirstUrl().toString());
    }

    @Override
    protected EnumCheckETag shouldCheckETag() {
        return EnumCheckETag.CHECK_E_TAG;
    }

    @Override
    protected void finishWithCachedResult(Path cachedFile) throws IOException {
        setResult(FileUtils.readText(cachedFile));

        super.finishWithCachedResult(cachedFile);
    }

    @Override
    protected void write(byte[] buffer, int offset, int len) {
        baos.write(buffer, offset, len);
    }

    @Override
    protected void onStart() {
        baos = new ByteArrayOutputStream();
    }

    @Override
    public void finish() throws IOException {
        if (!state.isFinished()) return;

        String result = baos.toString(charset.name());
        setResult(result);

        if (getCheckETag() == EnumCheckETag.CHECK_E_TAG) {
            repository.cacheText(result, state.getSegments().get(0).getConnection());
        }

        super.finish();
    }

}
