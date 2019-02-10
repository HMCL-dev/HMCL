package org.jackhuang.hmcl.game;

import com.google.gson.*;
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

    public Artifact(String descriptor) {
        this.descriptor = descriptor;

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

        this.group = arr[0].replace("\\", "/");
        this.name = arr[1];
        this.version = arr[2];
        this.classifier = arr.length >= 4 ? arr[3] : null;
        this.extension = ext == null ? "jar" : ext;

        String fileName = this.name + "-" + this.version;
        if (classifier != null) fileName += "-" + this.classifier;
        this.fileName = fileName + "." + this.extension;
        this.path = String.format("%s/%s/%s/%s", this.group.replace(".", "/"), this.name, this.version, this.fileName);
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

    public String getExtension() {
        return extension;
    }

    public String getFileName() {
        return fileName;
    }

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
            return json.isJsonPrimitive() ? new Artifact(json.getAsJsonPrimitive().getAsString()) : null;
        }
    }
}
