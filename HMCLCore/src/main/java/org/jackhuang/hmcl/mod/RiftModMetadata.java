/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.mod;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.CompressingUtils;
import org.jackhuang.hmcl.util.IOUtils;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Immutable
public final class RiftModMetadata {
    private final String id;
    private final String name;
    private final List<String> authors;

    public RiftModMetadata() {
        this("", "", Collections.emptyList());
    }

    public RiftModMetadata(String id, String name, List<String> authors) {
        this.id = id;
        this.name = name;
        this.authors = authors;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public static ModInfo fromFile(File modFile) throws IOException, JsonParseException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modFile.toPath())) {
            Path mcmod = fs.getPath("riftmod.json");
            if (Files.notExists(mcmod))
                throw new IOException("File " + modFile + " is not a Forge mod.");
            RiftModMetadata metadata = JsonUtils.fromNonNullJson(IOUtils.readFullyAsString(Files.newInputStream(mcmod)), RiftModMetadata.class);
            String authors = metadata.getAuthors() == null ? "" : String.join(", ", metadata.getAuthors());
            return new ModInfo(modFile, metadata.getName(), "",
                    authors, "", "", "");
        }
    }
}
