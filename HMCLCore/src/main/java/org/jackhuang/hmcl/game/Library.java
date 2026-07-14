/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/// A class that describes a Minecraft dependency.
///
/// @author huangyuhui
@JsonAdapter(Library.Adapter.class)
@JsonSerializable
@NotNullByDefault
public record Library(
        Artifact artifact,
        @Nullable String url,
        @Nullable LibrariesDownloadInfo downloads,
        @Nullable List<String> checksums,
        @Nullable ExtractRules extract,
        @Nullable Map<String, String> natives,
        @Nullable List<CompatibilityRule> rules,
        @Nullable String hint,
        @Nullable String filename
) implements Comparable<Library> {
    /// A possible native descriptors can be: [variant-]os[-key]
    ///
    /// Variant can be an empty string, 'native', or 'natives'.
    /// Key can be an empty string, system arch, or system arch bit count.
    private static final String[] POSSIBLE_NATIVE_DESCRIPTORS;

    static {
        String[] keys = {
                "",
                Architecture.SYSTEM_ARCH.name().toLowerCase(Locale.ROOT),
                Architecture.SYSTEM_ARCH.getBits().getBit()
        };
        String[] variants = {"", "native", "natives"};

        POSSIBLE_NATIVE_DESCRIPTORS = new String[keys.length * variants.length];
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            for (int j = 0; j < variants.length; j++) {
                if (!variants[j].isEmpty()) {
                    builder.append(variants[j]).append('-');
                }
                builder.append(OperatingSystem.CURRENT_OS.getMojangName());
                if (!keys[i].isEmpty()) {
                    builder.append('-').append(keys[i]);
                }

                POSSIBLE_NATIVE_DESCRIPTORS[i * variants.length + j] = builder.toString();
                builder.setLength(0);
            }
        }
    }

    /// Parses a library from either the standard version JSON format or TLauncher library format.
    public static Library fromJson(JsonObject json) {
        //noinspection ConstantValue
        if (json == null) {
            throw new JsonParseException("Library must be a JSON object");
        }

        Artifact artifact = fromJsonMember(json, "name", Artifact.class);
        if (artifact == null)
            throw new JsonParseException("Library.name cannot be null");

        String url = JsonUtils.getString(json, "url");
        LibrariesDownloadInfo downloads = fromJsonMember(json, "downloads", LibrariesDownloadInfo.class);

        if (downloads == null && (json.has("artifact") || json.has("classifies"))) {
            LibraryDownloadInfo downloadArtifact = fromJsonMember(json, "artifact", LibraryDownloadInfo.class);
            Map<String, LibraryDownloadInfo> classifiers = fromJsonMember(
                    json,
                    "classifies",
                    JsonUtils.mapTypeOf(String.class, LibraryDownloadInfo.class)
            );
            downloads = new LibrariesDownloadInfo(downloadArtifact, classifiers);
        }

        List<String> checksums = fromJsonMember(json, "checksums", JsonUtils.listTypeOf(String.class));
        ExtractRules extract = fromJsonMember(json, "extract", ExtractRules.class);
        Map<String, String> natives = fromJsonMember(json, "natives", JsonUtils.mapTypeOf(String.class, String.class));
        List<CompatibilityRule> rules = fromJsonMember(json, "rules", JsonUtils.listTypeOf(CompatibilityRule.class));

        String hint = JsonUtils.getString(json, "hint");
        if (hint == null) {
            hint = JsonUtils.getString(json, "MMC-hint");
        }

        String fileName = JsonUtils.getString(json, "filename");
        if (fileName == null) {
            fileName = JsonUtils.getString(json, "MMC-filename");
        }

        return new Library(artifact, url, downloads, checksums, extract, natives, rules, hint, fileName);
    }

    /// Reads a JSON member using HMCL's configured Gson instance.
    private static <T> @Nullable T fromJsonMember(JsonObject json, String name, Class<T> type) {
        return fromJsonMember(json, name, TypeToken.get(type));
    }

    /// Reads a JSON member using HMCL's configured Gson instance.
    private static <T> @Nullable T fromJsonMember(JsonObject json, String name, TypeToken<T> type) {
        JsonElement element = json.get(name);
        return element == null || element.isJsonNull() ? null : JsonUtils.GSON.fromJson(element, type);
    }

    public Library(Artifact artifact) {
        this(artifact, null, null);
    }

    public Library(Artifact artifact, @Nullable String url, @Nullable LibrariesDownloadInfo downloads) {
        this(artifact, url, downloads, null, null, null, null, null, null);
    }

    public String groupId() {
        return artifact.getGroup();
    }

    public String artifactId() {
        return artifact.getName();
    }

    public String name() {
        return artifact.toString();
    }

    public String version() {
        return artifact.getVersion();
    }

    public @Nullable String classifier() {
        if (artifact.getClassifier() == null) {
            if (natives != null) {
                for (String nativeDescriptor : POSSIBLE_NATIVE_DESCRIPTORS) {
                    String nd = natives.get(nativeDescriptor);
                    if (nd != null) {
                        return nd.replace("${arch}", Architecture.SYSTEM_ARCH.getBits().getBit());
                    }
                }
            } else if (downloads != null && downloads.classifiers() != null) {
                for (String nativeDescriptor : POSSIBLE_NATIVE_DESCRIPTORS) {
                    LibraryDownloadInfo info = downloads.classifiers().get(nativeDescriptor);
                    if (info != null) {
                        return nativeDescriptor;
                    }
                }
            }

            return null;
        } else {
            return artifact.getClassifier();
        }
    }

    public ExtractRules getExtract() {
        return extract == null ? ExtractRules.EMPTY : extract;
    }

    public boolean appliesToCurrentEnvironment() {
        return CompatibilityRule.appliesToCurrentEnvironment(rules);
    }

    public boolean isNative() {
        if (!appliesToCurrentEnvironment()) {
            return false;
        }
        if (natives != null) {
            return true;
        }

        return downloads != null && downloads.classifiers().keySet().stream().anyMatch(s -> s.startsWith("native"));
    }

    public @Nullable LibraryDownloadInfo getRawDownloadInfo() {
        if (downloads != null) {
            if (isNative())
                return downloads.classifiers().get(classifier());
            else
                return downloads.artifact();
        } else {
            return null;
        }
    }

    public String getPath() {
        LibraryDownloadInfo temp = getRawDownloadInfo();
        if (temp != null && temp.getPath() != null)
            return temp.getPath();
        else
            return artifact.setClassifier(classifier()).getPath();
    }

    public LibraryDownloadInfo getDownload() {
        LibraryDownloadInfo temp = getRawDownloadInfo();
        String path = getPath();
        return new LibraryDownloadInfo(path,
                computePath(temp, path),
                temp != null ? temp.getSha1() : null,
                temp != null ? temp.getSize() : 0
        );
    }

    private String computePath(@Nullable LibraryDownloadInfo raw, String path) {
        if (raw != null) {
            String url = raw.getUrl();
            if (url != null) {
                return url;
            }
        }

        String repo = Lang.requireNonNullElse(url, Constants.DEFAULT_LIBRARY_URL);
        if (!repo.endsWith("/")) {
            repo += '/';
        }

        return repo + path;
    }

    public boolean hasDownloadURL() {
        LibraryDownloadInfo temp = getRawDownloadInfo();
        if (temp != null) return temp.getUrl() != null;
        else return url != null;
    }

    public Library withoutCommunityFields() {
        return new Library(artifact, url, downloads, checksums, extract, natives, rules, null, null);
    }

    public boolean is(String groupId, String artifactId) {
        return groupId().equals(groupId) && artifactId().equals(artifactId);
    }

    /// Serializes this library to the standard Minecraft version JSON format.
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("name", artifact.toString());

        if (url != null)
            json.addProperty("url", url);
        if (downloads != null)
            json.add("downloads", JsonUtils.GSON.toJsonTree(downloads));
        if (checksums != null)
            json.add("checksums", JsonUtils.GSON.toJsonTree(checksums));
        if (extract != null)
            json.add("extract", JsonUtils.GSON.toJsonTree(extract));
        if (natives != null)
            json.add("natives", JsonUtils.GSON.toJsonTree(natives));
        if (rules != null)
            json.add("rules", JsonUtils.GSON.toJsonTree(rules));
        if (hint != null)
            json.addProperty("hint", hint);
        if (filename != null)
            json.addProperty("filename", filename);

        return json;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("name", name()).toString();
    }

    @Override
    public int compareTo(Library o) {
        if (name().compareTo(o.name()) == 0)
            return Boolean.compare(isNative(), o.isNative());
        else
            return name().compareTo(o.name());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Library other && name().equals(other.name()) && (isNative() == other.isNative());

    }

    @Override
    public int hashCode() {
        return Objects.hash(name(), isNative());
    }

    public Library setClassifier(String classifier) {
        return new Library(artifact.setClassifier(classifier), url, downloads, checksums, extract, natives, rules, hint, filename);
    }

    /// Gson adapter for library JSON variants.
    public static final class Adapter extends TypeAdapter<@Nullable Library> {
        @Override
        public @Nullable Library read(JsonReader in) throws IOException {
            JsonElement element = JsonParser.parseReader(in);
            if (element == null || element.isJsonNull())
                return null;
            if (element instanceof JsonObject jsonObject)
                return Library.fromJson(jsonObject);

            throw new JsonParseException("Expected a JSON object for Library, but got: " + element.getClass());
        }

        @Override
        public void write(JsonWriter out, @Nullable Library value) throws IOException {
            if (value != null)
                JsonUtils.GSON.toJson(value.toJsonObject(), out);
            else
                out.nullValue();
        }
    }
}
