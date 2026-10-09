/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.patchpack;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class PatchpackHelper {

    private PatchpackHelper() {
        throw new AssertionError();
    }

    public static boolean isPatchPackByExtension(Path file) {
        return "zip".equalsIgnoreCase(FileUtils.getExtension(file));
    }

    public static Charset findSuitableEncoding(Path file) throws IOException {
        return CompressingUtils.findSuitableEncoding(file);
    }

    public static PatchpackInfo readPatchPackInfo(Path file, @Nullable Charset charset) throws IOException {
        Charset encoding = charset != null ? charset : findSuitableEncoding(file);

        String json;
        try (var zip = CompressingUtils.openZipFile(file, encoding)) {
            var entry = zip.getEntry(PatchpackInfo.FILE_NAME);
            if (entry == null)
                throw new IOException("Missing " + PatchpackInfo.FILE_NAME + " in the patch pack");

            try (InputStream input = zip.getInputStream(entry)) {
                json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        PatchpackInfo info;
        try {
            info = JsonUtils.fromJson(json, PatchpackInfo.class);
        } catch (JsonParseException e) {
            throw new IOException("Malformed " + PatchpackInfo.FILE_NAME, e);
        }

        if (info == null)
            throw new IOException("Empty " + PatchpackInfo.FILE_NAME);

        return info;
    }

    public static PatchpackInstallTask getInstallTask(Path zipFile, Charset charset, PatchpackInfo info, Path runDirectory) {
        return new PatchpackInstallTask(zipFile, charset, info, runDirectory);
    }
}
