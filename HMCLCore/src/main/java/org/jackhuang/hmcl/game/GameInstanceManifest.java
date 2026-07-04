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
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

@NotNullByDefault
public record GameInstanceManifest(
        GameInstanceID id,
        @Nullable String minecraftArguments,
        @Nullable Arguments arguments,
        @Nullable String mainClass,
        @Nullable String inheritsFrom,
        @Nullable String jar,
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

    public static GameInstanceManifest fromJson(JsonObject json, boolean copyJson) throws JsonParseException {
        if (copyJson) {
            json = json.deepCopy();
        }

        Editor editor = new Editor();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String memberName = entry.getKey();
            JsonElement value = entry.getValue();

            switch (memberName) {
                case "id" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            editor.id = new GameInstanceID(primitive.getAsString());
                        } catch (IllegalArgumentException e) {
                            throw new JsonParseException(e);
                        }
                    }
                }
                case "minecraftArguments" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        editor.minecraftArguments = primitive.getAsString();
                    }
                }
                case "arguments" -> {
                    editor.arguments = JsonUtils.GSON.fromJson(value, Arguments.class);
                }
                case "mainClass" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        editor.mainClass = primitive.getAsString();
                    }
                }
                case "inheritsFrom" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        editor.inheritsFrom = primitive.getAsString();
                    }
                }
                case "jar" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        editor.jar = primitive.getAsString();
                    }
                }
                case "assetIndex" -> {
                    editor.assetIndex = JsonUtils.GSON.fromJson(value, AssetIndexInfo.class);
                }
                case "assets" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        editor.assets = primitive.getAsString();
                    }
                }
                case "complianceLevel" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
                        editor.complianceLevel = primitive.getAsInt();
                    }
                }
                case "javaVersion" -> {
                    editor.javaVersion = JsonUtils.GSON.fromJson(value, GameJavaVersion.class);
                }
                case "libraries" -> {
                    if (value instanceof JsonArray array) {
                        List<Library> list = new ArrayList<>(array.size());
                        for (JsonElement element : array) {
                            list.add(JsonUtils.GSON.fromJson(element, Library.class));
                        }
                        editor.libraries = Collections.unmodifiableList(list);
                    }
                }
                case "compatibilityRules" -> {
                    if (value instanceof JsonArray array) {
                        List<CompatibilityRule> list = new ArrayList<>(array.size());
                        for (JsonElement element : array) {
                            list.add(JsonUtils.GSON.fromJson(element, CompatibilityRule.class));
                        }
                        editor.compatibilityRules = Collections.unmodifiableList(list);
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
                        editor.downloads = Collections.unmodifiableMap(map);
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
                        editor.logging = Map.copyOf(map);
                    }
                }
                case "type" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        try {
                            editor.type = ReleaseType.valueOf(primitive.getAsString());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                case "time" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        editor.time = primitive.getAsString();
                    }
                }
                case "releaseTime" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        editor.releaseTime = primitive.getAsString();
                    }
                }
                case "minimumLauncherVersion" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
                        editor.minimumLauncherVersion = primitive.getAsInt();
                    }
                }
                case "root" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
                        editor.root = primitive.getAsBoolean();
                    }
                }
                case "hidden" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
                        editor.hidden = primitive.getAsBoolean();
                    }
                }
                case "patches" -> {
                    if (value instanceof JsonArray array) {
                        List<GameInstancePatch> list = new ArrayList<>(array.size());
                        for (JsonElement element : array) {
                            // TODO
                        }
                        editor.patches = List.copyOf(list);
                    }
                }
            }
        }

        editor.rawJson = json;
        return editor.toManifest();
    }


    private static final class Editor {
        // @formatter:off
        private @Nullable GameInstanceID id;
        private @Nullable String minecraftArguments;
        private @Nullable Arguments arguments;
        private @Nullable String mainClass;
        private @Nullable String inheritsFrom;
        private @Nullable String jar;
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
        private @Nullable @Unmodifiable JsonObject rawJson;
        // @formatter:on


        Editor() {
        }

        Editor(GameInstanceManifest manifest) {
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
}
