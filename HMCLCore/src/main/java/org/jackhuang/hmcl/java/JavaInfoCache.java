/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@JsonSerializable
@JsonAdapter(JavaInfoCache.Serializer.class)
public record JavaInfoCache(String realPath, String cacheKey, JavaInfo javaInfo) {
    public static final long FORMAT_VERSION = 0L;

    public static LinkedHashMap<Path, JavaInfoCache> loadCacheMap(Path cacheFile) throws IOException {
        if (Files.notExists(cacheFile))
            throw new FileNotFoundException("Cache file does not exist: " + cacheFile);

        JsonObject jsonObject = JsonUtils.fromJsonFile(cacheFile, JsonObject.class);

        JsonElement fileVersion = jsonObject.get("version");
        if (!(fileVersion instanceof JsonPrimitive))
            throw new IOException("Invalid version JSON: " + fileVersion);

        int version = fileVersion.getAsJsonPrimitive().getAsInt();
        if (version != FORMAT_VERSION)
            throw new IOException("Unsupported cache file, version: %d".formatted(version));

        List<JavaInfoCache> caches = JsonUtils.GSON.fromJson(
                jsonObject.getAsJsonArray("cache"),
                JsonUtils.listTypeOf(JavaInfoCache.class));

        LinkedHashMap<Path, JavaInfoCache> result = new LinkedHashMap<>();
        for (JavaInfoCache item : caches) {
            try {
                Path path = Path.of(item.realPath);
                result.put(path, item);
            } catch (Exception e) {
                LOG.warning("Java info cache invalid: " + item.realPath, e);
            }
        }
        return result;
    }

    public static final class Serializer implements JsonSerializer<JavaInfoCache>, JsonDeserializer<JavaInfoCache> {

        @Override
        public JavaInfoCache deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return null;
        }

        @Override
        public JsonElement serialize(JavaInfoCache javaInfoCache, Type type, JsonSerializationContext jsonSerializationContext) {
            return null;
        }
    }
}
