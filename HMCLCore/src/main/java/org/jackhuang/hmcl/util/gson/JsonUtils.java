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
package org.jackhuang.hmcl.util.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author yushijinhun
 */
@SuppressWarnings("unchecked")
public final class JsonUtils {

    public static final Gson GSON = defaultGsonBuilder().create();

    public static final Gson UGLY_GSON = new GsonBuilder()
            .registerTypeAdapterFactory(JsonTypeAdapterFactory.INSTANCE)
            .registerTypeAdapterFactory(ValidationTypeAdapterFactory.INSTANCE)
            .registerTypeAdapterFactory(LowerCaseEnumTypeAdapterFactory.INSTANCE)
            .create();

    private JsonUtils() {
    }

    public static <T> TypeToken<List<T>> listTypeOf(Class<T> elementType) {
        return (TypeToken<List<T>>) TypeToken.getParameterized(List.class, elementType);
    }

    public static <T> TypeToken<List<T>> listTypeOf(TypeToken<T> elementType) {
        return (TypeToken<List<T>>) TypeToken.getParameterized(List.class, elementType.getType());
    }

    public static <K, V> TypeToken<Map<K, V>> mapTypeOf(Class<K> keyType, Class<V> valueType) {
        return (TypeToken<Map<K, V>>) TypeToken.getParameterized(Map.class, keyType, valueType);
    }

    public static <K, V> TypeToken<Map<K, V>> mapTypeOf(Class<K> keyType, TypeToken<V> valueType) {
        return (TypeToken<Map<K, V>>) TypeToken.getParameterized(Map.class, keyType, valueType.getType());
    }

    public static <T> T fromJsonFile(Path file, Class<T> classOfT) throws IOException {
        return fromJsonFile(file, TypeToken.get(classOfT));
    }

    public static <T> T fromJsonFile(Path file, TypeToken<T> type) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, type.getType());
        }
    }

    public static <T> T fromJsonFully(InputStream json, Class<T> classOfT) throws IOException, JsonParseException {
        try (InputStreamReader reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, classOfT);
        }
    }

    public static <T> T fromJsonFully(InputStream json, TypeToken<T> type) throws IOException, JsonParseException {
        try (InputStreamReader reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static <T> T fromNonNullJson(String json, Class<T> classOfT) throws JsonParseException {
        T parsed = GSON.fromJson(json, classOfT);
        if (parsed == null)
            throw new JsonParseException("Json object cannot be null.");
        return parsed;
    }

    public static <T> T fromNonNullJson(String json, TypeToken<T> type) throws JsonParseException {
        T parsed = GSON.fromJson(json, type);
        if (parsed == null)
            throw new JsonParseException("Json object cannot be null.");
        return parsed;
    }

    public static <T> T fromNonNullJsonFully(InputStream json, Class<T> classOfT) throws IOException, JsonParseException {
        try (InputStreamReader reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            T parsed = GSON.fromJson(reader, classOfT);
            if (parsed == null)
                throw new JsonParseException("Json object cannot be null.");
            return parsed;
        }
    }

    public static <T> T fromNonNullJsonFully(InputStream json, TypeToken<T> type) throws IOException, JsonParseException {
        try (InputStreamReader reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            T parsed = GSON.fromJson(reader, type);
            if (parsed == null)
                throw new JsonParseException("Json object cannot be null.");
            return parsed;
        }
    }

    public static <T> T fromMaybeMalformedJson(String json, Class<T> classOfT) throws JsonParseException {
        try {
            return GSON.fromJson(json, classOfT);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    public static <T> T fromMaybeMalformedJson(String json, TypeToken<T> type) throws JsonParseException {
        try {
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    public static void writeToJsonFile(Path file, Object value) throws IOException {
        try (var writer = Files.newBufferedWriter(file)) {
            GSON.toJson(value, writer);
        }
    }

    public static GsonBuilder defaultGsonBuilder() {
        return new GsonBuilder()
                .enableComplexMapKeySerialization()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, InstantTypeAdapter.INSTANCE)
                .registerTypeAdapter(UUID.class, UUIDTypeAdapter.INSTANCE)
                .registerTypeAdapter(Path.class, PathTypeAdapter.INSTANCE)
                .registerTypeAdapterFactory(ValidationTypeAdapterFactory.INSTANCE)
                .registerTypeAdapterFactory(LowerCaseEnumTypeAdapterFactory.INSTANCE)
                .registerTypeAdapterFactory(JsonTypeAdapterFactory.INSTANCE);
    }
}
