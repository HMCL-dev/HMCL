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
package org.jackhuang.hmcl.mod;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Immutable
public final class FabricModMetadata {
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String icon;
    private final List<FabricModAuthor> authors;
    private final Map<String, String> contact;

    public FabricModMetadata() {
        this("", "", "", "", "", Collections.emptyList(), Collections.emptyMap());
    }

    public FabricModMetadata(String id, String name, String version, String icon, String description, List<FabricModAuthor> authors, Map<String, String> contact) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.icon = icon;
        this.description = description;
        this.authors = authors;
        this.contact = contact;
    }

    public static LocalModFile fromFile(ModManager modManager, Path modFile, FileSystem fs) throws IOException, JsonParseException {
        Path mcmod = fs.getPath("fabric.mod.json");
        if (Files.notExists(mcmod))
            throw new IOException("File " + modFile + " is not a Fabric mod.");
        FabricModMetadata metadata = JsonUtils.fromNonNullJson(FileUtils.readText(mcmod), FabricModMetadata.class);
        String authors = metadata.authors == null ? "" : metadata.authors.stream().map(author -> author.name).collect(Collectors.joining(", "));
        return new LocalModFile(modManager, modManager.getLocalMod(metadata.id, ModLoaderType.FABRIC), modFile, metadata.name, new LocalModFile.Description(metadata.description),
                authors, metadata.version, "", metadata.contact != null ? metadata.contact.getOrDefault("homepage", "") : "", metadata.icon);
    }

    @JsonAdapter(FabricModAuthorSerializer.class)
    public static final class FabricModAuthor {
        private final String name;

        public FabricModAuthor() {
            this("");
        }

        public FabricModAuthor(String name) {
            this.name = name;
        }
    }

    public static final class FabricModAuthorSerializer implements JsonSerializer<FabricModAuthor>, JsonDeserializer<FabricModAuthor> {
        @Override
        public FabricModAuthor deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return json.isJsonPrimitive() ? new FabricModAuthor(json.getAsString()) : new FabricModAuthor(json.getAsJsonObject().getAsJsonPrimitive("name").getAsString());
        }

        @Override
        public JsonElement serialize(FabricModAuthor src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.name);
        }
    }
}
