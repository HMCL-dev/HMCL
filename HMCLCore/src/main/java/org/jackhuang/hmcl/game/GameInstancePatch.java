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
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
        @Nullable String time,
        @Nullable String releaseTime,
        @Nullable Integer minimumLauncherVersion,
        @Nullable Boolean hidden,
        @Nullable @Unmodifiable JsonObject rawJson
) {

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
        @Nullable String time = null;
        @Nullable String releaseTime = null;
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
                        Map<DownloadType, DownloadInfo> map = new EnumMap<>(DownloadType.class);
                        for (Map.Entry<String, JsonElement> downloadEntry : object.entrySet()) {
                            try {
                                DownloadType downloadType = DownloadType.valueOf(downloadEntry.getKey());
                                map.put(downloadType, JsonUtils.GSON.fromJson(downloadEntry.getValue(), DownloadInfo.class));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        downloads = Collections.unmodifiableMap(map);
                    }
                }
                case "logging" -> {
                    if (value instanceof JsonObject object) {
                        Map<DownloadType, LoggingInfo> map = new EnumMap<>(DownloadType.class);
                        for (Map.Entry<String, JsonElement> loggingEntry : object.entrySet()) {
                            try {
                                DownloadType downloadType = DownloadType.valueOf(loggingEntry.getKey());
                                map.put(downloadType, JsonUtils.GSON.fromJson(loggingEntry.getValue(), LoggingInfo.class));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        logging = Collections.unmodifiableMap(map);
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
                        time = primitive.getAsString();
                    }
                }
                case "releaseTime" -> {
                    if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                        releaseTime = primitive.getAsString();
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
            json.addProperty("time", time);
        if (releaseTime != null)
            json.addProperty("releaseTime", releaseTime);
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
