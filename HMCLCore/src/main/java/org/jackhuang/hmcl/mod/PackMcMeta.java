/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

@Immutable
public class PackMcMeta implements Validation {

    @SerializedName("pack")
    private final PackInfo pack;

    public PackMcMeta() {
        this(new PackInfo());
    }

    public PackMcMeta(PackInfo packInfo) {
        this.pack = packInfo;
    }

    public PackInfo getPackInfo() {
        return pack;
    }

    @Override
    public void validate() throws JsonParseException {
        if (pack == null)
            throw new JsonParseException("pack cannot be null");
    }

    public static class PackInfo {
        @SerializedName("pack_format")
        private final int packFormat;

        @SerializedName("description")
        private final String description;

        public PackInfo() {
            this(0, "");
        }

        public PackInfo(int packFormat, String description) {
            this.packFormat = packFormat;
            this.description = description;
        }

        public int getPackFormat() {
            return packFormat;
        }

        public String getDescription() {
            return description;
        }
    }

    public static ModInfo fromFile(ModManager modManager, File modFile) throws IOException, JsonParseException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modFile.toPath())) {
            Path mcmod = fs.getPath("pack.mcmeta");
            if (Files.notExists(mcmod))
                throw new IOException("File " + modFile + " is not a resource pack.");
            PackMcMeta metadata = JsonUtils.fromNonNullJson(FileUtils.readText(mcmod), PackMcMeta.class);
            return new ModInfo(modManager, modFile, metadata.pack.description, "", "", "", "", "");
        }
    }
}
