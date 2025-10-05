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
            this(0, new PackVersion(0, 0), new PackVersion(0, 0), new LocalModFile.Description(Collections.emptyList()));
        }

        public PackInfo(int packFormat, PackVersion minPackVersion, PackVersion maxPackVersion, LocalModFile.Description description) {
            this.packFormat = packFormat;
            this.minPackVersion = minPackVersion;
            this.maxPackVersion = maxPackVersion;
            this.description = description;
        }

        public PackVersion getEffectiveMinVersion() {
            return minPackVersion.majorVersion != 0 ? minPackVersion : new PackVersion(packFormat, 0);
        }

        public PackVersion getEffectiveMaxVersion() {
            return maxPackVersion.majorVersion != 0 ? maxPackVersion : new PackVersion(packFormat, 0);
        }

        public LocalModFile.Description getDescription() {
            return description;
        }
    }

    public record PackVersion(int majorVersion, int minorVersion) {

        @Override
        public String toString() {
            return majorVersion + "." + minorVersion;
        }
    }

    public static class PackInfoDeserializer implements JsonDeserializer<PackInfo> {

        private PackVersion parseVersion(JsonElement json) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return new PackVersion(0, 0);
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
                    throw new JsonParseException("Datapack version array must have 1 or 2 elements, but got " + arr.size());
                }
            }

            throw new JsonParseException("Datapack version format must be a number or a [major, minor] array.");
        }

        private void parseComponent(JsonElement element, List<LocalModFile.Description.Part> parts, String fatherColor) throws JsonParseException {
            if (fatherColor == null) {
                fatherColor = "";
            }
            if (element.isJsonPrimitive()) {
                parts.add(new LocalModFile.Description.Part(element.getAsString(),fatherColor));
            } else if (element.isJsonObject()) {
                JsonObject descriptionPart = element.getAsJsonObject();
                String color = Optional.ofNullable(descriptionPart.get("color")).map(JsonElement::getAsString).orElse(fatherColor);
                if (descriptionPart.get("text") != null) {
                    parts.add(new LocalModFile.Description.Part(descriptionPart.get("text").getAsString(), color));
                }
                if (descriptionPart.get("extra") != null) {
                    parseComponent(descriptionPart.get("extra"), parts, color);
                }
            } else if (element.isJsonArray()) {
                for (JsonElement childElement : element.getAsJsonArray()) {
                    parseComponent(childElement, parts, fatherColor);
                }
            } else {
                throw new JsonParseException("Unsupported type in description. Expected a string, object, or array, but got: " + element);
            }
        }

        private List<LocalModFile.Description.Part> parseDescription(JsonElement json) throws JsonParseException {
            List<LocalModFile.Description.Part> parts = new ArrayList<>();

            if (json == null || json.isJsonNull()) {
                return parts;
            }

            parseComponent(json, parts, "");
            return parts;
        }

        @Override
        public PackInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject packInfo = json.getAsJsonObject();
            int packFormat = Optional.ofNullable(packInfo.get("pack_format")).map(JsonElement::getAsInt).orElse(0);
            PackVersion minVersion = parseVersion(packInfo.get("min_format"));
            PackVersion maxVersion = parseVersion(packInfo.get("max_format"));

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
