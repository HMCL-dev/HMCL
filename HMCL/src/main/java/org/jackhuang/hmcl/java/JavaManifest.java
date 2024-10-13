/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.java;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.util.gson.JsonUtils.mapTypeOf;

/**
 * @author Glavo
 */
@JsonAdapter(JavaManifest.Serializer.class)
public final class JavaManifest {

    private final JavaInfo info;

    @Nullable
    private final Map<String, Object> update;

    @Nullable
    private final Map<String, JavaLocalFiles.Local> files;

    public JavaManifest(JavaInfo info, @Nullable Map<String, Object> update, @Nullable Map<String, JavaLocalFiles.Local> files) {
        this.info = info;
        this.update = update;
        this.files = files;
    }

    public JavaInfo getInfo() {
        return info;
    }

    public Map<String, Object> getUpdate() {
        return update;
    }

    public Map<String, JavaLocalFiles.Local> getFiles() {
        return files;
    }

    public static final class Serializer implements JsonSerializer<JavaManifest>, JsonDeserializer<JavaManifest> {

        private static final Type LOCAL_FILES_TYPE = mapTypeOf(String.class, JavaLocalFiles.Local.class).getType();

        @Override
        public JsonElement serialize(JavaManifest src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject res = new JsonObject();
            res.addProperty("os.name", src.getInfo().getPlatform().getOperatingSystem().getCheckedName());
            res.addProperty("os.arch", src.getInfo().getPlatform().getArchitecture().getCheckedName());
            res.addProperty("java.version", src.getInfo().getVersion());
            res.addProperty("java.vendor", src.getInfo().getVendor());

            if (src.getUpdate() != null)
                res.add("update", context.serialize(src.getUpdate()));

            if (src.getFiles() != null)
                res.add("files", context.serialize(src.getFiles(), LOCAL_FILES_TYPE));

            return res;
        }

        @Override
        public JavaManifest deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (!json.isJsonObject())
                throw new JsonParseException(json.toString());

            try {
                JsonObject jsonObject = json.getAsJsonObject();
                OperatingSystem osName = OperatingSystem.parseOSName(jsonObject.getAsJsonPrimitive("os.name").getAsString());
                Architecture osArch = Architecture.parseArchName(jsonObject.getAsJsonPrimitive("os.arch").getAsString());
                String javaVersion = jsonObject.getAsJsonPrimitive("java.version").getAsString();
                String javaVendor = Optional.ofNullable(jsonObject.get("java.vendor")).map(JsonElement::getAsString).orElse(null);

                Map<String, Object> update = jsonObject.has("update") ? context.deserialize(jsonObject.get("update"), Map.class) : null;
                Map<String, JavaLocalFiles.Local> files = jsonObject.has("files") ? context.deserialize(jsonObject.get("files"), LOCAL_FILES_TYPE) : null;

                if (osName == null || osArch == null || javaVersion == null)
                    throw new JsonParseException(json.toString());

                return new JavaManifest(new JavaInfo(Platform.getPlatform(osName, osArch), javaVersion, javaVendor), update, files);
            } catch (JsonParseException e) {
                throw e;
            } catch (Throwable e) {
                throw new JsonParseException(e);
            }
        }
    }
}
