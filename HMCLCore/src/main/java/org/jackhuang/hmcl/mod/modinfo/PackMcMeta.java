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
import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@JsonSerializable
public record PackMcMeta(@SerializedName("pack") PackInfo pack) implements Validation {
    @Override
    public void validate() throws JsonParseException {
        if (pack == null)
            throw new JsonParseException("pack cannot be null");
    }

    @JsonAdapter(PackInfoDeserializer.class)
    public record PackInfo(@SerializedName("pack_format") int packFormat,
                           @SerializedName("min_format") PackVersion minPackVersion,
                           @SerializedName("max_format") PackVersion maxPackVersion,
                           @SerializedName("description") LocalModFile.Description description) {
        public PackVersion getEffectiveMinVersion() {
            return !minPackVersion.isUnspecified() ? minPackVersion : new PackVersion(packFormat, 0);
        }

        public PackVersion getEffectiveMaxVersion() {
            return !maxPackVersion.isUnspecified() ? maxPackVersion : new PackVersion(packFormat, 0);
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

        public static PackVersion fromJson(JsonElement element) throws JsonParseException {
            if (element == null || element.isJsonNull()) {
                return UNSPECIFIED;
            }

            try {
                if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
                    return new PackVersion(element.getAsInt(), 0);
                } else if (element instanceof JsonArray jsonArray) {
                    if (jsonArray.size() == 1 && jsonArray.get(0) instanceof JsonPrimitive) {
                        return new PackVersion(jsonArray.get(0).getAsInt(), 0);
                    } else if (jsonArray.size() == 2 && jsonArray.get(0) instanceof JsonPrimitive && jsonArray.get(1) instanceof JsonPrimitive) {
                        return new PackVersion(jsonArray.get(0).getAsInt(), jsonArray.get(1).getAsInt());
                    } else {
                        LOG.warning("Datapack version array must have 1 or 2 elements, but got " + jsonArray.size());
                    }
                }
            } catch (NumberFormatException e) {
                LOG.warning("Failed to parse datapack version component as a number. Value: " + element, e);
            }

            return UNSPECIFIED;
        }
    }

    public static final class PackInfoDeserializer implements JsonDeserializer<PackInfo> {

        private List<LocalModFile.Description.Part> pairToPart(List<Pair<String, String>> lists, String color) {
            List<LocalModFile.Description.Part> parts = new ArrayList<>();
            for (Pair<String, String> list : lists) {
                parts.add(new LocalModFile.Description.Part(list.getKey(), list.getValue().isEmpty() ? color : list.getValue()));
            }
            return parts;
        }

        private void parseComponent(JsonElement element, List<LocalModFile.Description.Part> parts, String parentColor) throws JsonParseException {
            if (parentColor == null) {
                parentColor = "";
            }
            String color = parentColor;
            if (element instanceof JsonPrimitive primitive) {
                parts.addAll(pairToPart(StringUtils.parseMinecraftColorCodes(primitive.getAsString()), color));
            } else if (element instanceof JsonObject jsonObj) {
                if (jsonObj.get("color") instanceof JsonPrimitive primitive) {
                    color = primitive.getAsString();
                }
                if (jsonObj.get("text") instanceof JsonPrimitive primitive) {
                    parts.addAll(pairToPart(StringUtils.parseMinecraftColorCodes(primitive.getAsString()), color));
                }
                if (jsonObj.get("extra") instanceof JsonArray jsonArray) {
                    parseComponent(jsonArray, parts, color);
                }
            } else if (element instanceof JsonArray jsonArray) {
                if (!jsonArray.isEmpty() && jsonArray.get(0) instanceof JsonObject jsonObj && jsonObj.get("color") instanceof JsonPrimitive primitive) {
                    color = primitive.getAsString();
                }

                for (JsonElement childElement : jsonArray) {
                    parseComponent(childElement, parts, color);
                }
            } else {
                LOG.warning("Skipping unsupported element in description. Expected a string, object, or array, but got type " + element.getClass().getSimpleName() + ". Value: " + element);
            }
        }

        private List<LocalModFile.Description.Part> parseDescription(JsonElement json) throws JsonParseException {
            List<LocalModFile.Description.Part> parts = new ArrayList<>();

            if (json == null || json.isJsonNull()) {
                return parts;
            }

            try {
                parseComponent(json, parts, "");
            } catch (JsonParseException | IllegalStateException e) {
                parts.clear();
                LOG.warning("An unexpected error occurred while parsing a description component. The description may be incomplete.", e);
            }

            return parts;
        }

        @Override
        public PackInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject packInfo = json.getAsJsonObject();
            int packFormat;
            if (packInfo.get("pack_format") instanceof JsonPrimitive primitive && primitive.isNumber()) {
                packFormat = primitive.getAsInt();
            } else {
                packFormat = 0;
            }
            PackVersion minVersion = PackVersion.fromJson(packInfo.get("min_format"));
            PackVersion maxVersion = PackVersion.fromJson(packInfo.get("max_format"));

            List<LocalModFile.Description.Part> parts = parseDescription(packInfo.get("description"));
            return new PackInfo(packFormat, minVersion, maxVersion, new LocalModFile.Description(parts));
        }
    }

    public static LocalModFile fromFile(ModManager modManager, Path modFile, ZipFileTree tree) throws IOException, JsonParseException {
        ZipArchiveEntry mcmod = tree.getEntry("pack.mcmeta");
        if (mcmod == null)
            throw new IOException("File " + modFile + " is not a resource pack.");
        PackMcMeta metadata = JsonUtils.fromNonNullJsonFully(tree.getInputStream(mcmod), PackMcMeta.class);
        return new LocalModFile(
                modManager,
                modManager.getLocalMod(FileUtils.getNameWithoutExtension(modFile), ModLoaderType.PACK),
                modFile,
                FileUtils.getNameWithoutExtension(modFile),
                metadata.pack.description,
                "", "", "", "", "");
    }
}
