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
package org.jackhuang.hmcl.mod.modinfo;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

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

    @JsonAdapter(PackInfoDeserializer.class)
    public static class PackInfo {
        @SerializedName("pack_format")
        private final int packFormat;

        @SerializedName("min_format")
        private final PackVersion minPackVersion;
        @SerializedName("max_format")
        private final PackVersion maxPackVersion;

        @SerializedName("description")
        private final LocalModFile.Description description;

        public PackInfo() {
            this(0, PackVersion.UNSPECIFIED, PackVersion.UNSPECIFIED, new LocalModFile.Description(Collections.emptyList()));
        }

        public PackInfo(int packFormat, PackVersion minPackVersion, PackVersion maxPackVersion, LocalModFile.Description description) {
            this.packFormat = packFormat;
            this.minPackVersion = minPackVersion;
            this.maxPackVersion = maxPackVersion;
            this.description = description;
        }

        public PackVersion getEffectiveMinVersion() {
            return !minPackVersion.isUnspecified() ? minPackVersion : new PackVersion(packFormat, 0);
        }

        public PackVersion getEffectiveMaxVersion() {
            return !maxPackVersion.isUnspecified() ? maxPackVersion : new PackVersion(packFormat, 0);
        }

        public LocalModFile.Description getDescription() {
            return description;
        }
    }

    public record PackVersion(int majorVersion, int minorVersion) implements Comparable<PackVersion> {

        public static final PackVersion UNSPECIFIED = new PackVersion(0, 0);

        @Override
        public String toString() {
            return minorVersion != 0 ? majorVersion + "." + minorVersion : String.valueOf(majorVersion);
        }

        @Override
        public int compareTo(PackVersion other) {
            int majorCompare = Integer.compare(this.majorVersion, other.majorVersion);
            if (majorCompare != 0) {
                return majorCompare;
            }
            return Integer.compare(this.minorVersion, other.minorVersion);
        }

        public boolean isUnspecified() {
            return this.equals(UNSPECIFIED);
        }

        public static PackVersion fromJson(JsonElement json) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return UNSPECIFIED;
            }

            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                return new PackVersion(json.getAsInt(), 0);
            }

            if (json.isJsonArray()) {
                JsonArray arr = json.getAsJsonArray();
                if (arr.size() == 1) {
                    return new PackVersion(arr.get(0).getAsInt(), 0);
                } else if (arr.size() == 2) {
                    return new PackVersion(arr.get(0).getAsInt(), arr.get(1).getAsInt());
                } else {
                    LOG.warning("Datapack version array must have 1 or 2 elements, but got " + arr.size());
                }
            }

            LOG.warning("Datapack version format must be a number or a [major, minor] array.");
            return UNSPECIFIED;
        }
    }

    public static class PackInfoDeserializer implements JsonDeserializer<PackInfo> {

        private void parseComponent(JsonElement element, List<LocalModFile.Description.Part> parts, String parentColor) throws JsonParseException {
            if (parentColor == null) {
                parentColor = "";
            }
            if (element instanceof JsonPrimitive primitive) {
                parts.add(new LocalModFile.Description.Part(primitive.getAsString(), parentColor));
            } else if (element instanceof JsonObject jsonObj) {
                String color = jsonObj.has("color") ? jsonObj.get("color").getAsString() : parentColor;
                if (jsonObj.has("text")) {
                    parts.add(new LocalModFile.Description.Part(jsonObj.get("text").getAsString(), color));
                }
                if (jsonObj.has("extra")) {
                    parseComponent(jsonObj.get("extra"), parts, color);
                }
            } else if (element instanceof JsonArray jsonArray) {
                for (JsonElement childElement : jsonArray) {
                    parseComponent(childElement, parts, parentColor);
                }
            } else {
                LOG.warning("Unsupported type in description. Expected a string, object, or array, but got: " + element);
            }
        }

        private List<LocalModFile.Description.Part> parseDescription(JsonElement json) throws JsonParseException {
            List<LocalModFile.Description.Part> parts = new ArrayList<>();

            if (json == null || json.isJsonNull()) {
                return parts;
            }

            try {
                parseComponent(json, parts, "");
            } catch (Exception e) {
                parts.clear();
                parts.add(new LocalModFile.Description.Part("简介解析出错"));
                LOG.warning("Description parsing error", e);
            }

            return parts;
        }

        @Override
        public PackInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject packInfo = json.getAsJsonObject();
            int packFormat;
            try {
                packFormat = Optional.ofNullable(packInfo.get("pack_format")).map(JsonElement::getAsInt).orElse(0);
            } catch (NumberFormatException e) {
                packFormat = 0;
                LOG.warning("Pack format is not a number");
            }
            PackVersion minVersion = PackVersion.fromJson(packInfo.get("min_format"));
            PackVersion maxVersion = PackVersion.fromJson(packInfo.get("max_format"));

            List<LocalModFile.Description.Part> parts = parseDescription(packInfo.get("description"));
            return new PackInfo(packFormat, minVersion, maxVersion, new LocalModFile.Description(parts));
        }
    }

    public static LocalModFile fromFile(ModManager modManager, Path modFile, FileSystem fs) throws IOException, JsonParseException {
        Path mcmod = fs.getPath("pack.mcmeta");
        if (Files.notExists(mcmod))
            throw new IOException("File " + modFile + " is not a resource pack.");
        PackMcMeta metadata = JsonUtils.fromNonNullJson(Files.readString(mcmod), PackMcMeta.class);
        return new LocalModFile(
                modManager,
                modManager.getLocalMod(FileUtils.getNameWithoutExtension(modFile), ModLoaderType.PACK),
                modFile,
                FileUtils.getNameWithoutExtension(modFile),
                metadata.pack.description,
                "", "", "", "", "");
    }
}
