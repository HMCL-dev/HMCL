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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.util.ImmutableSequencedMap;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.InstantTypeAdapter;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@NotNullByDefault
public record GameInstancePatch(
        @Nullable String id,
        @Nullable String version,
        @Nullable Integer priority,
        @Nullable String minecraftArguments,
        @Nullable Arguments arguments,
        @Nullable String mainClass,
        @Nullable String inheritsFrom,
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
        @Nullable Instant time,
        @Nullable Instant releaseTime,
        @Nullable Integer minimumLauncherVersion,
        @Nullable Boolean hidden,
        @Nullable @Unmodifiable JsonObject rawJson
) {

    /// Priority for the Minecraft base patch.
    public static final int PRIORITY_MC = 0;

    /// Priority for loader patches.
    public static final int PRIORITY_LOADER = 30000;

    /// Creates an empty patch with the given id.
    public GameInstancePatch(String id) {
        this(
                id,
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
                null,
                null,
                null,
                null);
    }

    /// Creates a patch with launch metadata.
    public GameInstancePatch(
            String id,
            @Nullable String version,
            int priority,
            @Nullable Arguments arguments,
            @Nullable String mainClass,
            @Nullable List<Library> libraries) {
        this(
                id,
                version,
                priority,
                null,
                arguments,
                mainClass,
                null,
                null,
                null,
                null,
                null,
                null,
                libraries,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /// Creates a patch from manifest metadata.
    public static GameInstancePatch fromManifest(
            GameInstanceManifest manifest,
            String id,
            @Nullable String version,
            int priority) {
        return new GameInstancePatch(
                id,
                version,
                priority,
                manifest.minecraftArguments(),
                manifest.arguments(),
                manifest.mainClass(),
                manifest.inheritsFrom() == null ? null : manifest.inheritsFrom().id(),
                manifest.jar(),
                manifest.assetIndex(),
                manifest.assets(),
                manifest.complianceLevel(),
                manifest.javaVersion(),
                manifest.libraries(),
                manifest.compatibilityRules(),
                manifest.downloads(),
                manifest.logging(),
                manifest.type(),
                manifest.time(),
                manifest.releaseTime(),
                manifest.minimumLauncherVersion(),
                manifest.hidden(),
                null);
    }

    /// Returns the patch priority.
    public int getPriority() {
        return priority == null ? Integer.MIN_VALUE : priority;
    }

    /// Returns libraries.
    public List<Library> getLibraries() {
        return libraries == null ? List.of() : libraries;
    }

    /// Returns whether the patch is hidden.
    public boolean isHidden() {
        return hidden != null && hidden;
    }

    /// Returns a patch copy with the given id.
    public GameInstancePatch withId(@Nullable String id) {
        if (Objects.equals(this.id, id)) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setId(id);
        return builder.toPatch();
    }

    /// Returns a patch copy with the given version.
    public GameInstancePatch withVersion(@Nullable String version) {
        if (Objects.equals(this.version, version)) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setVersion(version);
        return builder.toPatch();
    }

    /// Returns a patch copy with the given priority.
    public GameInstancePatch withPriority(@Nullable Integer priority) {
        if (Objects.equals(this.priority, priority)) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setPriority(priority);
        return builder.toPatch();
    }

    /// Returns a patch copy with the given jar id.
    public GameInstancePatch withJar(@Nullable GameInstanceID jar) {
        if (Objects.equals(this.jar, jar)) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setJar(jar);
        return builder.toPatch();
    }

    /// Returns a patch copy with the given libraries.
    public GameInstancePatch withLibraries(@Nullable List<Library> libraries) {
        if (this.libraries == libraries) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setLibraries(libraries);
        return builder.toPatch();
    }

    /// Returns a patch copy with the given Java version.
    public GameInstancePatch withJavaVersion(@Nullable GameJavaVersion javaVersion) {
        if (this.javaVersion == javaVersion) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setJavaVersion(javaVersion);
        return builder.toPatch();
    }

    /// Returns a patch copy with the given arguments.
    public GameInstancePatch withArguments(@Nullable Arguments arguments) {
        if (this.arguments == arguments) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setArguments(arguments);
        return builder.toPatch();
    }

    /// Returns a patch copy with the given main class.
    public GameInstancePatch withMainClass(@Nullable String mainClass) {
        if (Objects.equals(this.mainClass, mainClass)) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setMainClass(mainClass);
        return builder.toPatch();
    }

    /// Returns a patch copy with the given asset index.
    public GameInstancePatch withAssetIndex(@Nullable AssetIndexInfo assetIndex) {
        if (this.assetIndex == assetIndex) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setAssetIndex(assetIndex);
        return builder.toPatch();
    }

    /// Returns a patch copy with the given downloads.
    public GameInstancePatch withDownload(@Nullable Map<DownloadType, DownloadInfo> downloads) {
        if (this.downloads == downloads) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setDownloads(downloads);
        return builder.toPatch();
    }

    /// Returns a patch copy with the given logging metadata.
    public GameInstancePatch withLogging(@Nullable Map<DownloadType, LoggingInfo> logging) {
        if (this.logging == logging) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setLogging(logging);
        return builder.toPatch();
    }

    private static final class Builder {
        private @Nullable String id;
        private @Nullable String version;
        private @Nullable Integer priority;
        private @Nullable String minecraftArguments;
        private @Nullable Arguments arguments;
        private @Nullable String mainClass;
        private @Nullable String inheritsFrom;
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
        private @Nullable Instant time;
        private @Nullable Instant releaseTime;
        private @Nullable Integer minimumLauncherVersion;
        private @Nullable Boolean hidden;
        private @Nullable JsonObject rawJson;

        private Builder(GameInstancePatch patch) {
            this.id = patch.id;
            this.version = patch.version;
            this.priority = patch.priority;
            this.minecraftArguments = patch.minecraftArguments;
            this.arguments = patch.arguments;
            this.mainClass = patch.mainClass;
            this.inheritsFrom = patch.inheritsFrom;
            this.jar = patch.jar;
            this.assetIndex = patch.assetIndex;
            this.assets = patch.assets;
            this.complianceLevel = patch.complianceLevel;
            this.javaVersion = patch.javaVersion;
            this.libraries = patch.libraries;
            this.compatibilityRules = patch.compatibilityRules;
            this.downloads = patch.downloads;
            this.logging = patch.logging;
            this.type = patch.type;
            this.time = patch.time;
            this.releaseTime = patch.releaseTime;
            this.minimumLauncherVersion = patch.minimumLauncherVersion;
            this.hidden = patch.hidden;
            this.rawJson = patch.rawJson != null ? patch.rawJson.deepCopy() : null;
        }

        private void setId(@Nullable String id) {
            this.id = id;
            if (rawJson != null) {
                if (id != null) {
                    rawJson.addProperty("id", id);
                } else {
                    rawJson.remove("id");
                }
            }
        }

        private void setVersion(@Nullable String version) {
            this.version = version;
            if (rawJson != null) {
                if (version != null) {
                    rawJson.addProperty("version", version);
                } else {
                    rawJson.remove("version");
                }
            }
        }

        private void setPriority(@Nullable Integer priority) {
            this.priority = priority;
            if (rawJson != null) {
                if (priority != null) {
                    rawJson.addProperty("priority", priority);
                } else {
                    rawJson.remove("priority");
                }
            }
        }

        private void setMinecraftArguments(@Nullable String minecraftArguments) {
            this.minecraftArguments = minecraftArguments;
            if (rawJson != null) {
                if (minecraftArguments != null) {
                    rawJson.addProperty("minecraftArguments", minecraftArguments);
                } else {
                    rawJson.remove("minecraftArguments");
                }
            }
        }

        private void setArguments(@Nullable Arguments arguments) {
            this.arguments = arguments;
            if (rawJson != null) {
                if (arguments != null) {
                    rawJson.add("arguments", JsonUtils.GSON.toJsonTree(arguments));
                } else {
                    rawJson.remove("arguments");
                }
            }
        }

        private void setMainClass(@Nullable String mainClass) {
            this.mainClass = mainClass;
            if (rawJson != null) {
                if (mainClass != null) {
                    rawJson.addProperty("mainClass", mainClass);
                } else {
                    rawJson.remove("mainClass");
                }
            }
        }

        private void setInheritsFrom(@Nullable String inheritsFrom) {
            this.inheritsFrom = inheritsFrom;
            if (rawJson != null) {
                if (inheritsFrom != null) {
                    rawJson.addProperty("inheritsFrom", inheritsFrom);
                } else {
                    rawJson.remove("inheritsFrom");
                }
            }
        }

        private void setJar(@Nullable GameInstanceID jar) {
            this.jar = jar;
            if (rawJson != null) {
                if (jar != null) {
                    rawJson.addProperty("jar", jar.toString());
                } else {
                    rawJson.remove("jar");
                }
            }
        }

        private void setAssetIndex(@Nullable AssetIndexInfo assetIndex) {
            this.assetIndex = assetIndex;
            if (rawJson != null) {
                if (assetIndex != null) {
                    rawJson.add("assetIndex", JsonUtils.GSON.toJsonTree(assetIndex));
                } else {
                    rawJson.remove("assetIndex");
                }
            }
        }

        private void setAssets(@Nullable String assets) {
            this.assets = assets;
            if (rawJson != null) {
                if (assets != null) {
                    rawJson.addProperty("assets", assets);
                } else {
                    rawJson.remove("assets");
                }
            }
        }

        private void setComplianceLevel(@Nullable Integer complianceLevel) {
            this.complianceLevel = complianceLevel;
            if (rawJson != null) {
                if (complianceLevel != null) {
                    rawJson.addProperty("complianceLevel", complianceLevel);
                } else {
                    rawJson.remove("complianceLevel");
                }
            }
        }

        private void setJavaVersion(@Nullable GameJavaVersion javaVersion) {
            this.javaVersion = javaVersion;
            if (rawJson != null) {
                if (javaVersion != null) {
                    rawJson.add("javaVersion", JsonUtils.GSON.toJsonTree(javaVersion));
                } else {
                    rawJson.remove("javaVersion");
                }
            }
        }

        private void setLibraries(@Nullable List<Library> libraries) {
            this.libraries = libraries == null ? null : List.copyOf(libraries);
            if (rawJson != null) {
                if (this.libraries != null) {
                    rawJson.add("libraries", JsonUtils.GSON.toJsonTree(this.libraries));
                } else {
                    rawJson.remove("libraries");
                }
            }
        }

        private void setCompatibilityRules(@Nullable List<CompatibilityRule> compatibilityRules) {
            this.compatibilityRules = compatibilityRules == null ? null : List.copyOf(compatibilityRules);
            if (rawJson != null) {
                if (this.compatibilityRules != null) {
                    rawJson.add("compatibilityRules", JsonUtils.GSON.toJsonTree(this.compatibilityRules));
                } else {
                    rawJson.remove("compatibilityRules");
                }
            }
        }

        private void setDownloads(@Nullable Map<DownloadType, DownloadInfo> downloads) {
            this.downloads = downloads == null ? null : ImmutableSequencedMap.copyOf(downloads);
            if (rawJson != null) {
                if (this.downloads != null) {
                    JsonObject downloadsObject = new JsonObject();
                    for (Map.Entry<DownloadType, DownloadInfo> entry : this.downloads.entrySet()) {
                        downloadsObject.add(entry.getKey().name(), JsonUtils.GSON.toJsonTree(entry.getValue()));
                    }
                    rawJson.add("downloads", downloadsObject);
                } else {
                    rawJson.remove("downloads");
                }
            }
        }

        private void setLogging(@Nullable Map<DownloadType, LoggingInfo> logging) {
            this.logging = logging == null ? null : ImmutableSequencedMap.copyOf(logging);
            if (rawJson != null) {
                if (this.logging != null) {
                    JsonObject loggingObject = new JsonObject();
                    for (Map.Entry<DownloadType, LoggingInfo> entry : this.logging.entrySet()) {
                        loggingObject.add(entry.getKey().name(), JsonUtils.GSON.toJsonTree(entry.getValue()));
                    }
                    rawJson.add("logging", loggingObject);
                } else {
                    rawJson.remove("logging");
                }
            }
        }

        private void setType(@Nullable ReleaseType type) {
            this.type = type;
            if (rawJson != null) {
                if (type != null) {
                    rawJson.addProperty("type", type.name());
                } else {
                    rawJson.remove("type");
                }
            }
        }

        private void setTime(@Nullable Instant time) {
            this.time = time;
            if (rawJson != null) {
                if (time != null) {
                    rawJson.addProperty("time", InstantTypeAdapter.serializeToString(time, ZoneOffset.UTC));
                } else {
                    rawJson.remove("time");
                }
            }
        }

        private void setReleaseTime(@Nullable Instant releaseTime) {
            this.releaseTime = releaseTime;
            if (rawJson != null) {
                if (releaseTime != null) {
                    rawJson.addProperty("releaseTime", InstantTypeAdapter.serializeToString(releaseTime, ZoneOffset.UTC));
                } else {
                    rawJson.remove("releaseTime");
                }
            }
        }

        private void setMinimumLauncherVersion(@Nullable Integer minimumLauncherVersion) {
            this.minimumLauncherVersion = minimumLauncherVersion;
            if (rawJson != null) {
                if (minimumLauncherVersion != null) {
                    rawJson.addProperty("minimumLauncherVersion", minimumLauncherVersion);
                } else {
                    rawJson.remove("minimumLauncherVersion");
                }
            }
        }

        private void setHidden(@Nullable Boolean hidden) {
            this.hidden = hidden;
            if (rawJson != null) {
                if (hidden != null) {
                    rawJson.addProperty("hidden", hidden);
                } else {
                    rawJson.remove("hidden");
                }
            }
        }

        private GameInstancePatch toPatch() {
            return new GameInstancePatch(
                    id,
                    version,
                    priority,
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
                    hidden,
                    rawJson);
        }
    }

    public static GameInstancePatch fromJson(JsonObject json) throws JsonParseException {
        @Nullable String id = null;
        @Nullable String version = null;
        @Nullable Integer priority = null;
        @Nullable String minecraftArguments = null;
        @Nullable Arguments arguments = null;
        @Nullable String mainClass = null;
        @Nullable String inheritsFrom = null;
        @Nullable GameInstanceID jar = null;
        @Nullable AssetIndexInfo assetIndex = null;
        @Nullable String assets = null;
        @Nullable Integer complianceLevel = null;
        @Nullable GameJavaVersion javaVersion = null;
        @Nullable List<Library> libraries = null;
        @Nullable List<CompatibilityRule> compatibilityRules = null;
        @Nullable Map<DownloadType, DownloadInfo> downloads = null;
        @Nullable Map<DownloadType, LoggingInfo> logging = null;
        @Nullable ReleaseType type = null;
        @Nullable Instant time = null;
        @Nullable Instant releaseTime = null;
        @Nullable Integer minimumLauncherVersion = null;
        @Nullable Boolean hidden = null;

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String memberName = entry.getKey();
            JsonElement value = entry.getValue();

            switch (memberName) {
                case "id" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        id = primitive.getAsString();
                    }
                }
                case "version" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        version = primitive.getAsString();
                    }
                }
                case "priority" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
                        priority = primitive.getAsInt();
                    }
                }
                case "minecraftArguments" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        minecraftArguments = primitive.getAsString();
                    }
                }
                case "arguments" -> arguments = JsonUtils.GSON.fromJson(value, Arguments.class);
                case "mainClass" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        mainClass = primitive.getAsString();
                    }
                }
                case "inheritsFrom" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        inheritsFrom = primitive.getAsString();
                    }
                }
                case "jar" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            jar = new GameInstanceID(primitive.getAsString());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                case "assetIndex" -> assetIndex = JsonUtils.GSON.fromJson(value, AssetIndexInfo.class);
                case "assets" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        assets = primitive.getAsString();
                    }
                }
                case "complianceLevel" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
                        complianceLevel = primitive.getAsInt();
                    }
                }
                case "javaVersion" -> javaVersion = JsonUtils.GSON.fromJson(value, GameJavaVersion.class);
                case "libraries" -> {
                    if (value instanceof JsonArray array) {
                        List<Library> list = new ArrayList<>(array.size());
                        for (JsonElement element : array) {
                            if (element instanceof JsonObject object) {
                                list.add(Library.fromJson(object));
                            }
                        }
                        libraries = List.copyOf(list);
                    }
                }
                case "compatibilityRules" -> {
                    if (value instanceof JsonArray array) {
                        List<CompatibilityRule> list = new ArrayList<>(array.size());
                        for (JsonElement element : array) {
                            list.add(JsonUtils.GSON.fromJson(element, CompatibilityRule.class));
                        }
                        compatibilityRules = List.copyOf(list);
                    }
                }
                case "downloads" -> {
                    if (value instanceof JsonObject object) {
                        Map<DownloadType, DownloadInfo> map = new LinkedHashMap<>();
                        for (Map.Entry<String, JsonElement> downloadEntry : object.entrySet()) {
                            try {
                                DownloadType downloadType = DownloadType.valueOf(downloadEntry.getKey());
                                map.put(downloadType, JsonUtils.GSON.fromJson(downloadEntry.getValue(), DownloadInfo.class));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        downloads = ImmutableSequencedMap.copyOf(map);
                    }
                }
                case "logging" -> {
                    if (value instanceof JsonObject object) {
                        Map<DownloadType, LoggingInfo> map = new LinkedHashMap<>();
                        for (Map.Entry<String, JsonElement> loggingEntry : object.entrySet()) {
                            try {
                                DownloadType downloadType = DownloadType.valueOf(loggingEntry.getKey());
                                map.put(downloadType, JsonUtils.GSON.fromJson(loggingEntry.getValue(), LoggingInfo.class));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        logging = ImmutableSequencedMap.copyOf(map);
                    }
                }
                case "type" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            type = ReleaseType.valueOf(primitive.getAsString());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                case "time" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            time = InstantTypeAdapter.deserializeToInstant(primitive.getAsString());
                        } catch (Exception ignored) {
                        }
                    }
                }
                case "releaseTime" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            releaseTime = InstantTypeAdapter.deserializeToInstant(primitive.getAsString());
                        } catch (Exception ignored) {
                        }
                    }
                }
                case "minimumLauncherVersion" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
                        minimumLauncherVersion = primitive.getAsInt();
                    }
                }
                case "hidden" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
                        hidden = primitive.getAsBoolean();
                    }
                }
            }
        }

        return new GameInstancePatch(
                id,
                version,
                priority,
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
                hidden,
                json.deepCopy());
    }

    public JsonObject toJsonObject() {
        if (rawJson != null) {
            return rawJson.deepCopy();
        }

        JsonObject json = new JsonObject();

        if (id != null)
            json.addProperty("id", id);
        if (version != null)
            json.addProperty("version", version);
        if (priority != null)
            json.addProperty("priority", priority);
        if (minecraftArguments != null)
            json.addProperty("minecraftArguments", minecraftArguments);
        if (arguments != null)
            json.add("arguments", JsonUtils.GSON.toJsonTree(arguments));
        if (mainClass != null)
            json.addProperty("mainClass", mainClass);
        if (inheritsFrom != null)
            json.addProperty("inheritsFrom", inheritsFrom);
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
            json.addProperty("time", InstantTypeAdapter.serializeToString(time, ZoneOffset.UTC));
        if (releaseTime != null)
            json.addProperty("releaseTime", InstantTypeAdapter.serializeToString(releaseTime, ZoneOffset.UTC));
        if (minimumLauncherVersion != null)
            json.addProperty("minimumLauncherVersion", minimumLauncherVersion);
        if (hidden != null)
            json.addProperty("hidden", hidden);

        return json;
    }

    GameInstanceManifest merge(GameInstanceManifest parent) {
        return new GameInstanceManifest(
                parent.id(),
                minecraftArguments == null ? parent.minecraftArguments() : minecraftArguments,
                Arguments.merge(parent.arguments(), arguments),
                mainClass == null ? parent.mainClass() : mainClass,
                null, // inheritsFrom
                parent.jar(),
                assetIndex == null ? parent.assetIndex() : assetIndex,
                assets == null ? parent.assets() : assets,
                complianceLevel,
                javaVersion == null ? parent.javaVersion() : javaVersion,
                Lang.merge(this.libraries, parent.libraries()),
                Lang.merge(parent.compatibilityRules(), this.compatibilityRules),
                downloads == null ? parent.downloads() : downloads,
                logging == null ? parent.logging() : logging,
                type == null ? parent.type() : type,
                time == null ? parent.time() : time,
                releaseTime == null ? parent.releaseTime() : releaseTime,
                Lang.merge(minimumLauncherVersion, parent.minimumLauncherVersion(), Math::max),
                true,
                hidden,
                parent.patches(),
                null);
    }
}
