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
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.InstantTypeAdapter;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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
        @Nullable Instant time,
        @Nullable Instant releaseTime,
        @Nullable Integer minimumLauncherVersion,
        @Nullable Boolean root,
        @Nullable Boolean hidden,
        @Nullable @Unmodifiable List<GameInstancePatch> patches,
        @Nullable @Unmodifiable JsonObject rawJson
) {

    /// Resolved manifest views with inheritance folded.
    ///
    /// @param launchManifest     the final manifest data used by launch-time consumers
    /// @param standaloneManifest the standalone manifest data with pending patches preserved
    @NotNullByDefault
    public record Resolved(GameInstanceManifest launchManifest,
                           GameInstanceManifest standaloneManifest) {

        /// Creates a resolved manifest view.
        public Resolved {
            Objects.requireNonNull(launchManifest);
            Objects.requireNonNull(standaloneManifest);

            if (!launchManifest.id().equals(standaloneManifest.id())) {
                throw new IllegalArgumentException("Resolved manifest views must have the same id");
            }

            if (launchManifest.inheritsFrom() != null) {
                throw new IllegalArgumentException("Launch manifest cannot inherit from another manifest");
            }
            if (launchManifest.patches() != null && !launchManifest.patches().isEmpty()) {
                throw new IllegalArgumentException("Launch manifest cannot contain pending patches");
            }
            if (standaloneManifest.inheritsFrom() != null) {
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

    /// Creates an empty root manifest with the given id.
    public GameInstanceManifest(String id) {
        this(new GameInstanceID(id));
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
                        try {
                            builder.time = InstantTypeAdapter.deserializeToInstant(primitive.getAsString());
                        } catch (Exception ignored) {
                        }
                    }
                }
                case "releaseTime" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            builder.releaseTime = InstantTypeAdapter.deserializeToInstant(primitive.getAsString());
                        } catch (Exception ignored) {
                        }
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

    /// Returns legacy game arguments.
    ///
    /// @return the legacy game arguments
    public Optional<String> getMinecraftArguments() {
        return Optional.ofNullable(minecraftArguments);
    }

    /// Returns structured game arguments.
    ///
    /// @return the structured game arguments
    public Optional<Arguments> getArguments() {
        return Optional.ofNullable(arguments);
    }

    /// Returns the main class.
    ///
    /// @return the main class, or `null` when absent
    public @Nullable String getMainClass() {
        return mainClass;
    }

    /// Returns the patch version.
    ///
    /// @return always `null` for manifests
    public @Nullable String getVersion() {
        return null;
    }

    /// Returns the preferred Java version.
    ///
    /// @return the preferred Java version, or `null` when absent
    public @Nullable GameJavaVersion getJavaVersion() {
        return javaVersion;
    }

    /// Returns whether the manifest is hidden.
    ///
    /// @return whether the manifest is hidden
    public boolean isHidden() {
        return hidden != null && hidden;
    }

    /// Returns whether the manifest is a root manifest.
    ///
    /// @return whether the manifest is a root manifest
    public boolean isRoot() {
        return root();
    }

    /// Returns whether this manifest is already a standalone view.
    ///
    /// @return whether this manifest has no parent
    public boolean isResolvedPreservingPatches() {
        return inheritsFrom == null;
    }

    /// Returns the pending patches.
    ///
    /// @return the pending patches, or an empty list when absent
    public List<GameInstancePatch> getPatches() {
        return patches == null ? List.of() : patches;
    }

    /// Returns logging metadata.
    ///
    /// @return logging metadata
    public Map<DownloadType, LoggingInfo> getLogging() {
        return logging == null ? Map.of() : logging;
    }

    /// Returns libraries.
    ///
    /// @return libraries
    public List<Library> getLibraries() {
        return libraries == null ? List.of() : libraries;
    }

    /// Returns download metadata.
    ///
    /// @return download metadata
    public Map<DownloadType, DownloadInfo> getDownloads() {
        return downloads == null ? Map.of() : downloads;
    }

    /// Returns client jar download information.
    ///
    /// @return client jar download information
    public DownloadInfo getDownloadInfo() {
        DownloadInfo client = downloads == null ? null : downloads.get(DownloadType.CLIENT);
        String jarName = jar == null ? id.id() : jar.id();
        if (client == null) {
            return new DownloadInfo(String.format("%s%s/%s.jar", Constants.DEFAULT_VERSION_DOWNLOAD_URL, jarName, jarName));
        } else {
            return client;
        }
    }

    /// Returns the asset index metadata.
    ///
    /// @return asset index metadata
    public AssetIndexInfo getAssetIndex() {
        String assetsId = assets == null ? "legacy" : assets;

        if (assetIndex == null) {
            String hash;
            switch (assetsId) {
                case "1.8" -> hash = "f6ad102bcaa53b1a58358f16e376d548d44933ec";
                case "14w31a" -> hash = "10a2a0e75b03cfb5a7196abbdf43b54f7fa61deb";
                case "14w25a" -> hash = "32ff354a3be1c4dd83027111e6d79ee4d701d2c0";
                case "1.7.4" -> hash = "545510a60f526b9aa8a38f9c0bc7a74235d21675";
                case "1.7.10" -> hash = "1863782e33ce7b584fc45b037325a1964e095d3e";
                case "1.7.3" -> hash = "f6cf726f4747128d13887010c2cbc44ba83504d9";
                case "pre-1.6" -> hash = "3d8e55480977e32acd9844e545177e69a52f594b";
                case "legacy" -> hash = "770572e819335b6c0a053f8378ad88eda189fc14";
                default -> {
                    assetsId = "legacy";
                    hash = "770572e819335b6c0a053f8378ad88eda189fc14";
                }
            }

            String url = Constants.DEFAULT_INDEX_URL + hash + "/" + assetsId + ".json";
            return new AssetIndexInfo(assetsId, url);
        } else {
            return assetIndex;
        }
    }

    /// Returns whether this manifest applies to the current environment.
    ///
    /// @return whether this manifest applies to the current environment
    public boolean appliesToCurrentEnvironment() {
        return CompatibilityRule.appliesToCurrentEnvironment(compatibilityRules);
    }

    /// Resolves this manifest through the repository.
    ///
    /// @param repository the repository that provides parent manifests
    /// @return the resolved manifest
    public GameInstanceManifest resolve(GameRepository repository) throws NoSuchGameInstanceException {
        return repository.resolve(this).launchManifest();
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

    /// Returns a manifest copy with the given legacy game arguments.
    public GameInstanceManifest withMinecraftArguments(@Nullable String minecraftArguments) {
        if (Objects.equals(this.minecraftArguments, minecraftArguments)) {
            return this;
        }

        Builder builder = new Builder(this);
        builder.setMinecraftArguments(minecraftArguments);
        return builder.toManifest();
    }

    /// Returns a manifest copy with the given structured arguments.
    public GameInstanceManifest setArguments(@Nullable Arguments arguments) {
        Builder builder = new Builder(this);
        builder.setArguments(arguments);
        return builder.toManifest();
    }

    /// Returns a manifest copy with the given main class.
    public GameInstanceManifest setMainClass(@Nullable String mainClass) {
        Builder builder = new Builder(this);
        builder.setMainClass(mainClass);
        return builder.toManifest();
    }

    /// Returns a manifest copy with the given asset index.
    public GameInstanceManifest setAssetIndex(@Nullable AssetIndexInfo assetIndex) {
        Builder builder = new Builder(this);
        builder.setAssetIndex(assetIndex);
        return builder.toManifest();
    }

    /// Returns a manifest copy with the given Java version.
    public GameInstanceManifest setJavaVersion(@Nullable GameJavaVersion javaVersion) {
        Builder builder = new Builder(this);
        builder.setJavaVersion(javaVersion);
        return builder.toManifest();
    }

    /// Returns a manifest copy with the given libraries.
    public GameInstanceManifest setLibraries(@Nullable List<Library> libraries) {
        Builder builder = new Builder(this);
        builder.setLibraries(libraries);
        return builder.toManifest();
    }

    /// Returns a manifest copy with the given downloads.
    public GameInstanceManifest setDownload(@Nullable Map<DownloadType, DownloadInfo> downloads) {
        Builder builder = new Builder(this);
        builder.setDownloads(downloads);
        return builder.toManifest();
    }

    /// Returns a manifest copy with the given logging metadata.
    public GameInstanceManifest setLogging(@Nullable Map<DownloadType, LoggingInfo> logging) {
        Builder builder = new Builder(this);
        builder.setLogging(logging);
        return builder.toManifest();
    }

    /// Returns a manifest copy with the given root flag.
    public GameInstanceManifest setRoot(@Nullable Boolean root) {
        Builder builder = new Builder(this);
        builder.setRoot(root);
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

    /// Returns a manifest copy with the given patches.
    public GameInstanceManifest setPatches(@Nullable List<GameInstancePatch> patches) {
        return withPatches(patches);
    }

    /// Returns a manifest copy with additional patches.
    public GameInstanceManifest addPatch(GameInstancePatch additional) {
        return addPatches(List.of(additional));
    }

    /// Returns a manifest copy with additional patches.
    public GameInstanceManifest addPatches(@Nullable List<GameInstancePatch> additional) {
        Set<String> patchIds = new HashSet<>();
        if (additional != null) {
            for (GameInstancePatch patch : additional) {
                if (patch.id() != null) {
                    patchIds.add(patch.id());
                }
            }
        }

        List<GameInstancePatch> patches = new ArrayList<>();
        if (this.patches != null) {
            for (GameInstancePatch patch : this.patches) {
                if (patch.id() == null || !patchIds.contains(patch.id())) {
                    patches.add(patch);
                }
            }
        }
        if (additional != null) {
            patches.addAll(additional);
        }
        return withPatches(patches);
    }

    /// Returns a manifest copy without pending patches.
    public GameInstanceManifest clearPatches() {
        return withPatches(null);
    }

    /// Returns a manifest copy without the patch with the given id.
    public GameInstanceManifest removePatchById(String patchId) {
        if (patches == null) {
            return this;
        }

        List<GameInstancePatch> filtered = new ArrayList<>();
        for (GameInstancePatch patch : patches) {
            if (!patchId.equals(patch.id())) {
                filtered.add(patch);
            }
        }
        return withPatches(filtered);
    }

    /// Returns whether this manifest has a patch with the given id.
    public boolean hasPatch(String patchId) {
        return patches != null && patches.stream().anyMatch(patch -> patchId.equals(patch.id()));
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
            json.addProperty("time", InstantTypeAdapter.serializeToString(time, ZoneOffset.UTC));
        if (releaseTime != null)
            json.addProperty("releaseTime", InstantTypeAdapter.serializeToString(releaseTime, ZoneOffset.UTC));
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

    private static @Nullable Instant parseInstant(@Nullable String value) {
        if (value == null) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
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
        private @Nullable Instant time;
        private @Nullable Instant releaseTime;
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

        public void setMinecraftArguments(@Nullable String minecraftArguments) {
            this.minecraftArguments = minecraftArguments;
            rawJson = null;
        }

        public void setArguments(@Nullable Arguments arguments) {
            this.arguments = arguments;
            rawJson = null;
        }

        public void setMainClass(@Nullable String mainClass) {
            this.mainClass = mainClass;
            rawJson = null;
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

        public void setAssetIndex(@Nullable AssetIndexInfo assetIndex) {
            this.assetIndex = assetIndex;
            rawJson = null;
        }

        public void setJavaVersion(@Nullable GameJavaVersion javaVersion) {
            this.javaVersion = javaVersion;
            rawJson = null;
        }

        public void setLibraries(@Nullable List<Library> libraries) {
            this.libraries = libraries == null ? null : List.copyOf(libraries);
            rawJson = null;
        }

        public void setDownloads(@Nullable Map<DownloadType, DownloadInfo> downloads) {
            this.downloads = downloads == null ? null : Map.copyOf(downloads);
            rawJson = null;
        }

        public void setLogging(@Nullable Map<DownloadType, LoggingInfo> logging) {
            this.logging = logging == null ? null : Map.copyOf(logging);
            rawJson = null;
        }

        public void setRoot(@Nullable Boolean root) {
            this.root = root;
            rawJson = null;
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
