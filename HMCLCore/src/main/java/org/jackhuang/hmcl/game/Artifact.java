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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import org.jackhuang.hmcl.util.Immutable;

import java.lang.reflect.Type;
import java.nio.file.Path;

@Immutable
@JsonAdapter(Artifact.Serializer.class)
public final class Artifact {

    private final String group;
    private final String name;
    private final String version;
    private final String classifier;
    private final String extension;

    private final String descriptor;
    private final String fileName;
    private final String path;

    public Artifact(String group, String name, String version) {
        this(group, name, version, null);
    }

    public Artifact(String group, String name, String version, String classifier) {
        this(group, name, version, classifier, null);
    }

    public Artifact(String group, String name, String version, String classifier, String extension) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.classifier = classifier;
        this.extension = extension == null ? "jar" : extension;

        String fileName = this.name + "-" + this.version;
        if (classifier != null) fileName += "-" + this.classifier;
        this.fileName = fileName + "." + this.extension;
        this.path = String.format("%s/%s/%s/%s", this.group.replace('.', '/'), this.name, this.version, this.fileName);

        // group:name:version:classifier@extension
        String descriptor = String.format("%s:%s:%s", group, name, version);
        if (classifier != null) descriptor += ":" + classifier;
        if (!"jar".equals(this.extension)) descriptor += "@" + this.extension;
        this.descriptor = descriptor;
    }

    public static Artifact fromDescriptor(String descriptor) {
        String[] arr = descriptor.split(":", 4);
        if (arr.length != 3 && arr.length != 4)
            throw new IllegalArgumentException("Artifact name is malformed");

        String ext = null;
        int last = arr.length - 1;
        String[] splitted = arr[last].split("@");
        if (splitted.length == 2) {
            arr[last] = splitted[0];
            ext = splitted[1];
        } else if (splitted.length > 2) {
            throw new IllegalArgumentException("Artifact name is malformed");
        }

        return new Artifact(arr[0].replace('\\', '/'), arr[1], arr[2], arr.length >= 4 ? arr[3] : null, ext);
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

    public Artifact setClassifier(String classifier) {
        return new Artifact(group, name, version, classifier, extension);
    }

    public String getExtension() {
        return extension;
    }

    public String getFileName() {
        return fileName;
    }

    public String getPath() { return path; }

    public Path getPath(Path root) {
        return root.resolve(path);
    }

    @Override
    public String toString() {
        return descriptor;
    }

    public static class Serializer implements JsonDeserializer<Artifact>, JsonSerializer<Artifact> {
        @Override
        public JsonElement serialize(Artifact src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toString());
        }

        @Override
        public Artifact deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return json.isJsonPrimitive() ? fromDescriptor(json.getAsJsonPrimitive().getAsString()) : null;
        }
    }
}
