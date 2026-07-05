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
package org.jackhuang.hmcl.game;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.*;

@NotNullByDefault
@JsonAdapter(GameInstanceManifest.Adapter.class)
public record GameInstanceManifest(
        GameInstanceID id,
        @Nullable String minecraftArguments,
        @Nullable Arguments arguments,
        @Nullable String mainClass,
        @Nullable GameInstanceID inheritsFrom,
        @Nullable GameInstanceID jar,
        @Nullable AssetIndexInfo assetIndex,
        @Nullable String assets,
        @Nullable Integer complianceLevel,
        @Nullable GameJavaVersion javaVersion,
        @Nullable @Unmodifiable List<Library> libraries,
        @Nullable @Unmodifiable List<CompatibilityRule> compatibilityRules,
        @Nullable @Unmodifiable Map<DownloadType, DownloadInfo> downloads,
        @Nullable @Unmodifiable Map<DownloadType, LoggingInfo> logging,
        @Nullable ReleaseType type,
        @Nullable String time,
        @Nullable String releaseTime,
        @Nullable Integer minimumLauncherVersion,
        @Nullable Boolean root,
        @Nullable Boolean hidden,
        @Nullable @Unmodifiable List<GameInstancePatch> patches,
        @Nullable @Unmodifiable JsonObject rawJson
) {

    /// A launch-ready manifest view with inheritance and pending patches applied.
    ///
    /// @param manifest       the final manifest data used by launch-time consumers
    /// @param appliedPatches patches that have already been applied to produce the final manifest
    @NotNullByDefault
    public record Resolved(GameInstanceManifest manifest,
                           @Unmodifiable List<GameInstancePatch> appliedPatches) {

        /// Creates a resolved manifest view.
        public Resolved {
            Objects.requireNonNull(manifest);
            appliedPatches = List.copyOf(appliedPatches);

            if (manifest.inheritsFrom() != null) {
                throw new IllegalArgumentException("Resolved manifest cannot inherit from another manifest");
            }
            if (manifest.patches() != null && !manifest.patches().isEmpty()) {
                throw new IllegalArgumentException("Resolved manifest cannot contain pending patches");
            }
        }
    }

    /// A manifest view whose inheritance has been folded while preserving pending patches.
    ///
    /// @param manifest the standalone manifest data that can be saved back to disk
    @NotNullByDefault
    public record Standalone(GameInstanceManifest manifest) {

        /// Creates a standalone manifest view.
        public Standalone {
            Objects.requireNonNull(manifest);

            if (manifest.inheritsFrom() != null) {
                throw new IllegalArgumentException("Standalone manifest cannot inherit from another manifest");
            }
        }
    }

    GameInstanceManifest merge(GameInstanceManifest parent) {
        return new GameInstanceManifest(
                id,
                minecraftArguments == null ? parent.minecraftArguments : minecraftArguments,
                Arguments.merge(parent.arguments, arguments),
                mainClass == null ? parent.mainClass : mainClass,
                null, // inheritsFrom
                jar == null ? parent.jar : jar,
                assetIndex == null ? parent.assetIndex : assetIndex,
                assets == null ? parent.assets : assets,
                complianceLevel,
                javaVersion == null ? parent.javaVersion : javaVersion,
                Lang.merge(this.libraries, parent.libraries),
                Lang.merge(parent.compatibilityRules, this.compatibilityRules),
                downloads == null ? parent.downloads : downloads,
                logging == null ? parent.logging : logging,
                type == null ? parent.type : type,
                time == null ? parent.time : time,
                releaseTime == null ? parent.releaseTime : releaseTime,
                Lang.merge(minimumLauncherVersion, parent.minimumLauncherVersion, Math::max),
                true,
                hidden,
                Lang.merge(Lang.merge(parent.patches, Collections.singleton(toPatch())), patches),
                null
        );
    }

    public GameInstanceManifest(GameInstanceID id) {
        this(id,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                false,
                null,
                null);
    }

    public static GameInstanceManifest fromJson(JsonObject json, boolean copyJson) throws JsonParseException {
        if (copyJson) {
            json = json.deepCopy();
        }

        Builder builder = new Builder();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String memberName = entry.getKey();
            JsonElement value = entry.getValue();

            switch (memberName) {
                case "id" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            builder.id = new GameInstanceID(primitive.getAsString());
                        } catch (IllegalArgumentException e) {
                            throw new JsonParseException(e);
                        }
                    }
                }
                case "minecraftArguments" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        builder.minecraftArguments = primitive.getAsString();
                    }
                }
                case "arguments" -> {
                    builder.arguments = JsonUtils.GSON.fromJson(value, Arguments.class);
                }
                case "mainClass" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        builder.mainClass = primitive.getAsString();
                    }
                }
                case "inheritsFrom" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            builder.inheritsFrom = new GameInstanceID(primitive.getAsString());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                case "jar" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            builder.jar = new GameInstanceID(primitive.getAsString());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                case "assetIndex" -> {
                    builder.assetIndex = JsonUtils.GSON.fromJson(value, AssetIndexInfo.class);
                }
                case "assets" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        builder.assets = primitive.getAsString();
                    }
                }
                case "complianceLevel" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
                        builder.complianceLevel = primitive.getAsInt();
                    }
                }
                case "javaVersion" -> {
                    builder.javaVersion = JsonUtils.GSON.fromJson(value, GameJavaVersion.class);
                }
                case "libraries" -> {
                    if (value instanceof JsonArray array) {
                        List<Library> list = new ArrayList<>(array.size());
                        for (JsonElement element : array) {
                            if (element instanceof JsonObject object) {
                                list.add(Library.fromJson(object));
                            } else {
                                throw new JsonParseException("Invalid library element: " + element);
                            }
                        }
                        builder.libraries = List.copyOf(list);
                    }
                }
                case "compatibilityRules" -> {
                    if (value instanceof JsonArray array) {
                        List<CompatibilityRule> list = new ArrayList<>(array.size());
                        for (JsonElement element : array) {
                            list.add(JsonUtils.GSON.fromJson(element, CompatibilityRule.class));
                        }
                        builder.compatibilityRules = List.copyOf(list);
                    }
                }
                case "downloads" -> {
                    if (value instanceof JsonObject obj) {
                        Map<DownloadType, DownloadInfo> map = new EnumMap<>(DownloadType.class);
                        for (Map.Entry<String, JsonElement> downloadEntry : obj.entrySet()) {
                            try {
                                DownloadType type = DownloadType.valueOf(downloadEntry.getKey());
                                map.put(type, JsonUtils.GSON.fromJson(downloadEntry.getValue(), DownloadInfo.class));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        builder.downloads = Collections.unmodifiableMap(map);
                    }
                }
                case "logging" -> {
                    if (value instanceof JsonObject obj) {
                        Map<DownloadType, LoggingInfo> map = new EnumMap<>(DownloadType.class);
                        for (Map.Entry<String, JsonElement> loggingEntry : obj.entrySet()) {
                            try {
                                DownloadType type = DownloadType.valueOf(loggingEntry.getKey());
                                map.put(type, JsonUtils.GSON.fromJson(loggingEntry.getValue(), LoggingInfo.class));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        builder.logging = Map.copyOf(map);
                    }
                }
                case "type" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            builder.type = ReleaseType.valueOf(primitive.getAsString());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                case "time" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        builder.time = primitive.getAsString();
                    }
                }
                case "releaseTime" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        builder.releaseTime = primitive.getAsString();
                    }
                }
                case "minimumLauncherVersion" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
                        builder.minimumLauncherVersion = primitive.getAsInt();
                    }
                }
                case "root" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
                        builder.root = primitive.getAsBoolean();
                    }
                }
                case "hidden" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
                        builder.hidden = primitive.getAsBoolean();
                    }
                }
                case "patches" -> {
                    if (value instanceof JsonArray array) {
                        List<GameInstancePatch> list = new ArrayList<>(array.size());
                        for (JsonElement element : array) {
                            if (element instanceof JsonObject object) {
                                list.add(GameInstancePatch.fromJson(object));
                            }
                        }
                        builder.patches = List.copyOf(list);
                    }
                }
            }
        }

        builder.rawJson = json;
        if (builder.id == null) {
            throw new JsonParseException("Missing instance id");
        }

        return builder.toManifest();
    }

    @Override
    public Boolean root() {
        return root != null && root;
    }

    public GameInstanceManifest withId(GameInstanceID id) {
        Objects.requireNonNull(id);

        if (Objects.equals(this.id, id)) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setId(id);
        return builder.toManifest();
    }

    public GameInstanceManifest withJar(@Nullable GameInstanceID jar) {
        if (Objects.equals(this.jar, jar)) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setJar(jar);
        return builder.toManifest();
    }

    /// Returns a manifest copy with the given parent instance id.
    ///
    /// @param inheritsFrom the parent instance id, or `null` if this manifest is independent
    /// @return a manifest with the requested parent instance id
    public GameInstanceManifest withInheritsFrom(@Nullable GameInstanceID inheritsFrom) {
        if (Objects.equals(this.inheritsFrom, inheritsFrom)) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setInheritsFrom(inheritsFrom);
        return builder.toManifest();
    }

    public GameInstanceManifest withPatches(@Nullable List<GameInstancePatch> patches) {
        if (patches == this.patches) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setPatches(patches);
        return builder.toManifest();
    }

    /// Converts this manifest into a hidden patch entry for preserving resolved inheritance.
    ///
    /// @return a patch containing this manifest's launch metadata
    public GameInstancePatch toPatch() {
        return new GameInstancePatch(
                "resolved." + id,
                null,
                Integer.MIN_VALUE,
                minecraftArguments,
                arguments,
                mainClass,
                inheritsFrom == null ? null : inheritsFrom.toString(),
                jar,
                assetIndex,
                assets,
                complianceLevel,
                javaVersion,
                libraries,
                compatibilityRules,
                downloads,
                logging,
                type,
                time,
                releaseTime,
                minimumLauncherVersion,
                true,
                null);
    }

    public JsonObject toJsonObject() {
        if (rawJson != null) {
            return rawJson.deepCopy();
        }

        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());

        if (minecraftArguments != null)
            json.addProperty("minecraftArguments", minecraftArguments);
        if (arguments != null)
            json.add("arguments", JsonUtils.GSON.toJsonTree(arguments));
        if (mainClass != null)
            json.addProperty("mainClass", mainClass);
        if (inheritsFrom != null)
            json.addProperty("inheritsFrom", inheritsFrom.toString());
        if (jar != null)
            json.addProperty("jar", jar.toString());
        if (assetIndex != null)
            json.add("assetIndex", JsonUtils.GSON.toJsonTree(assetIndex));
        if (assets != null)
            json.addProperty("assets", assets);
        if (complianceLevel != null)
            json.addProperty("complianceLevel", complianceLevel);
        if (javaVersion != null)
            json.add("javaVersion", JsonUtils.GSON.toJsonTree(javaVersion));
        if (libraries != null)
            json.add("libraries", JsonUtils.GSON.toJsonTree(libraries));
        if (compatibilityRules != null)
            json.add("compatibilityRules", JsonUtils.GSON.toJsonTree(compatibilityRules));
        if (downloads != null) {
            JsonObject downloadsObject = new JsonObject();
            for (Map.Entry<DownloadType, DownloadInfo> entry : downloads.entrySet()) {
                downloadsObject.add(entry.getKey().name(), JsonUtils.GSON.toJsonTree(entry.getValue()));
            }
            json.add("downloads", downloadsObject);
        }
        if (logging != null) {
            JsonObject loggingObject = new JsonObject();
            for (Map.Entry<DownloadType, LoggingInfo> entry : logging.entrySet()) {
                loggingObject.add(entry.getKey().name(), JsonUtils.GSON.toJsonTree(entry.getValue()));
            }
            json.add("logging", loggingObject);
        }
        if (type != null)
            json.addProperty("type", type.name());
        if (time != null)
            json.addProperty("time", time);
        if (releaseTime != null)
            json.addProperty("releaseTime", releaseTime);
        if (minimumLauncherVersion != null)
            json.addProperty("minimumLauncherVersion", minimumLauncherVersion);
        if (root != null)
            json.addProperty("root", root);
        if (hidden != null)
            json.addProperty("hidden", hidden);
        if (patches != null) {
            JsonArray patchesArray = new JsonArray(patches.size());
            for (GameInstancePatch patch : patches) {
                patchesArray.add(patch.toJsonObject());
            }
            json.add("patches", patchesArray);
        }

        return json;
    }

    private static final class Builder {
        // @formatter:off
        private @Nullable GameInstanceID id;
        private @Nullable String minecraftArguments;
        private @Nullable Arguments arguments;
        private @Nullable String mainClass;
        private @Nullable GameInstanceID inheritsFrom;
        private @Nullable GameInstanceID jar;
        private @Nullable AssetIndexInfo assetIndex;
        private @Nullable String assets;
        private @Nullable Integer complianceLevel;
        private @Nullable GameJavaVersion javaVersion;
        private @Nullable @Unmodifiable List<Library> libraries;
        private @Nullable @Unmodifiable List<CompatibilityRule> compatibilityRules;
        private @Nullable @Unmodifiable Map<DownloadType, DownloadInfo> downloads;
        private @Nullable @Unmodifiable Map<DownloadType, LoggingInfo> logging;
        private @Nullable ReleaseType type;
        private @Nullable String time;
        private @Nullable String releaseTime;
        private @Nullable Integer minimumLauncherVersion;
        private @Nullable Boolean root;
        private @Nullable Boolean hidden;
        private @Nullable @Unmodifiable List<GameInstancePatch> patches;
        private @Nullable JsonObject rawJson;
        // @formatter:on

        Builder() {
        }

        Builder(GameInstanceManifest manifest) {
            this.id = manifest.id;
            this.minecraftArguments = manifest.minecraftArguments;
            this.arguments = manifest.arguments;
            this.mainClass = manifest.mainClass;
            this.inheritsFrom = manifest.inheritsFrom;
            this.jar = manifest.jar;
            this.assetIndex = manifest.assetIndex;
            this.assets = manifest.assets;
            this.complianceLevel = manifest.complianceLevel;
            this.javaVersion = manifest.javaVersion;
            this.libraries = manifest.libraries;
            this.compatibilityRules = manifest.compatibilityRules;
            this.downloads = manifest.downloads;
            this.logging = manifest.logging;
            this.type = manifest.type;
            this.time = manifest.time;
            this.releaseTime = manifest.releaseTime;
            this.minimumLauncherVersion = manifest.minimumLauncherVersion;
            this.root = manifest.root;
            this.hidden = manifest.hidden;
            this.patches = manifest.patches;
            this.rawJson = manifest.rawJson != null ? manifest.rawJson.deepCopy() : null;
        }

        public void setId(GameInstanceID id) {
            this.id = id;
            if (rawJson != null) {
                rawJson.addProperty("id", id.toString());
            }
        }

        public void setJar(@Nullable GameInstanceID jar) {
            this.jar = jar;
            if (rawJson != null) {
                if (jar != null) {
                    rawJson.addProperty("jar", jar.toString());
                } else {
                    rawJson.remove("jar");
                }
            }
        }

        public void setInheritsFrom(@Nullable GameInstanceID inheritsFrom) {
            this.inheritsFrom = inheritsFrom;
            if (rawJson != null) {
                if (inheritsFrom != null) {
                    rawJson.addProperty("inheritsFrom", inheritsFrom.toString());
                } else {
                    rawJson.remove("inheritsFrom");
                }
            }
        }

        public void setPatches(@Nullable List<GameInstancePatch> patches) {
            if (patches != null) {
                this.patches = List.copyOf(patches);
                if (rawJson != null) {
                    JsonArray array = new JsonArray(this.patches.size());
                    for (GameInstancePatch patch : this.patches) {
                        array.add(patch.toJsonObject());
                    }
                    rawJson.add("patches", array);
                }
            } else {
                this.patches = null;
                if (rawJson != null) {
                    rawJson.remove("patches");
                }
            }
        }

        GameInstanceManifest toManifest() {
            if (id == null) {
                throw new IllegalStateException("id is null");
            }

            return new GameInstanceManifest(
                    id,
                    minecraftArguments,
                    arguments,
                    mainClass,
                    inheritsFrom,
                    jar,
                    assetIndex,
                    assets,
                    complianceLevel,
                    javaVersion,
                    libraries,
                    compatibilityRules,
                    downloads,
                    logging,
                    type,
                    time,
                    releaseTime,
                    minimumLauncherVersion,
                    root,
                    hidden,
                    patches,
                    rawJson
            );
        }
    }

    static final class Adapter extends TypeAdapter<@Nullable GameInstanceManifest> {

        @Override
        public @Nullable GameInstanceManifest read(JsonReader in) {
            JsonElement jsonElement = JsonParser.parseReader(in);
            if (jsonElement.isJsonNull()) {
                return null;
            }

            if (jsonElement instanceof JsonObject jsonObject) {
                return GameInstanceManifest.fromJson(jsonObject, false);
            }

            throw new JsonParseException("Expected JsonObject but got " + jsonElement.getClass().getName());
        }

        @Override
        public void write(JsonWriter out, @Nullable GameInstanceManifest value) throws IOException {
            if (value != null)
                JsonUtils.GSON.toJson(value.toJsonObject(), out);
            else
                out.nullValue();
        }
    }
}
