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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// Utility class providing shared [Gson] instances and convenience methods for
/// JSON serialization and deserialization.
///
/// Two pre-configured [Gson] instances are exposed:
/// - [GSON] — pretty-printing instance with all standard HMCL type adapters registered.
/// - [UGLY_GSON] — compact (non-pretty) instance with the same adapter set minus
///   complex-map-key serialization and the built-in type adapters for [java.time.Instant],
///   [java.util.UUID], and [java.nio.file.Path].
///
/// All `fromJson` / `fromJsonFile` / `fromJsonFully` overloads return `null` when the
/// JSON literal `null` is encountered.  The `fromNonNull*` variants throw
/// [JsonParseException] instead of returning `null`.  The `fromMaybeMalformed*` variants
/// additionally swallow [com.google.gson.JsonSyntaxException] and return `null` for
/// syntactically invalid input.
///
/// @author yushijinhun
@SuppressWarnings("unchecked")
@NotNullByDefault
public final class JsonUtils {

    /// The default shared [Gson] instance.
    ///
    /// Configured with:
    /// - Pretty printing enabled.
    /// - Complex map key serialization enabled.
    /// - Type adapters for [java.time.Instant], [java.util.UUID], and [java.nio.file.Path].
    /// - [ValidationTypeAdapterFactory], [LowerCaseEnumTypeAdapterFactory], and
    ///   [JsonTypeAdapterFactory].
    public static final Gson GSON = defaultGsonBuilder().create();

    /// A compact [Gson] instance without pretty printing and without the extra
    /// type adapters for [java.time.Instant], [java.util.UUID], and [java.nio.file.Path].
    ///
    /// Configured with:
    /// - [JsonTypeAdapterFactory]
    /// - [ValidationTypeAdapterFactory]
    /// - [LowerCaseEnumTypeAdapterFactory]
    public static final Gson UGLY_GSON = new GsonBuilder()
            .registerTypeAdapterFactory(JsonTypeAdapterFactory.INSTANCE)
            .registerTypeAdapterFactory(ValidationTypeAdapterFactory.INSTANCE)
            .registerTypeAdapterFactory(LowerCaseEnumTypeAdapterFactory.INSTANCE)
            .create();

    /// Not instantiable.
    private JsonUtils() {
    }

    /// Returns a [TypeToken] representing `List<T>` parameterized with the given element class.
    ///
    /// @param <T>         the element type
    /// @param elementType the runtime [Class] of the list element
    /// @return a [TypeToken] for `List<T>`
    public static <T extends @UnknownNullability Object> TypeToken<List<@Nullable T>> listTypeOf(Class<T> elementType) {
        return (TypeToken<List<T>>) TypeToken.getParameterized(List.class, elementType);
    }

    /// Returns a [TypeToken] representing `List<T>` parameterized with the given element token.
    ///
    /// @param <T>         the element type
    /// @param elementType a [TypeToken] describing the element type (may itself be generic)
    /// @return a [TypeToken] for `List<T>`
    public static <T extends @UnknownNullability Object> TypeToken<List<T>> listTypeOf(TypeToken<T> elementType) {
        return (TypeToken<List<T>>) TypeToken.getParameterized(List.class, elementType.getType());
    }

    /// Returns a [TypeToken] representing `Map<K, V>` parameterized with the given key and
    /// value classes.
    ///
    /// @param <K>       the key type
    /// @param <V>       the value type
    /// @param keyType   the runtime [Class] of the map key
    /// @param valueType the runtime [Class] of the map value
    /// @return a [TypeToken] for `Map<K, V>`
    public static <K, V extends @UnknownNullability Object> TypeToken<Map<K, V>> mapTypeOf(Class<K> keyType, Class<V> valueType) {
        return (TypeToken<Map<K, V>>) TypeToken.getParameterized(Map.class, keyType, valueType);
    }

    /// Returns a [TypeToken] representing `Map<K, V>` parameterized with the given key class
    /// and value token.
    ///
    /// @param <K>       the key type
    /// @param <V>       the value type
    /// @param keyType   the runtime [Class] of the map key
    /// @param valueType a [TypeToken] describing the value type (may itself be generic)
    /// @return a [TypeToken] for `Map<K, V>`
    public static <K, V extends @UnknownNullability Object> TypeToken<Map<K, V>> mapTypeOf(
            Class<K> keyType, TypeToken<V> valueType) {
        return (TypeToken<Map<K, V>>) TypeToken.getParameterized(Map.class, keyType, valueType.getType());
    }

    /// Deserializes the JSON string into an object of the given class using the provided [Gson]
    /// instance.
    ///
    /// @param <T>  the target type
    /// @param gson the [Gson] instance to use
    /// @param json the JSON string to parse
    /// @param type the target class
    /// @return the deserialized object, or `null` if the JSON is `null`
    /// @throws com.google.gson.JsonSyntaxException if `json` is not valid JSON for `type`
    public static <T> @Nullable T fromJson(Gson gson, String json, Class<T> type) {
        return gson.<@Nullable T>fromJson(json, type);
    }

    /// Deserializes the JSON string into an object of the given class using [GSON].
    ///
    /// @param <T>  the target type
    /// @param json the JSON string to parse
    /// @param type the target class
    /// @return the deserialized object, or `null` if the JSON is `null`
    /// @throws com.google.gson.JsonSyntaxException if `json` is not valid JSON for `type`
    public static <T> @Nullable T fromJson(String json, Class<T> type) {
        return fromJson(GSON, json, type);
    }

    /// Deserializes the JSON string into an object described by the given [TypeToken] using the
    /// provided [Gson] instance.
    ///
    /// @param <T>  the target type
    /// @param gson the [Gson] instance to use
    /// @param json the JSON string to parse
    /// @param type a [TypeToken] describing the target type
    /// @return the deserialized object, or `null` if the JSON is `null`
    /// @throws com.google.gson.JsonSyntaxException if `json` is not valid JSON for `type`
    public static <T> @Nullable T fromJson(Gson gson, String json, TypeToken<T> type) {
        return gson.<@Nullable T>fromJson(json, type);
    }

    /// Deserializes the JSON string into an object described by the given [TypeToken] using
    /// [GSON].
    ///
    /// @param <T>  the target type
    /// @param json the JSON string to parse
    /// @param type a [TypeToken] describing the target type
    /// @return the deserialized object, or `null` if the JSON is `null`
    /// @throws com.google.gson.JsonSyntaxException if `json` is not valid JSON for `type`
    public static <T> @Nullable T fromJson(String json, TypeToken<T> type) {
        return fromJson(GSON, json, type);
    }

    /// Deserializes JSON from a [Reader] into an object of the given class using the provided
    /// [Gson] instance.
    ///
    /// @param <T>    the target type
    /// @param gson   the [Gson] instance to use
    /// @param reader the [Reader] supplying JSON content
    /// @param type   the target class
    /// @return the deserialized object, or `null` if the JSON is `null`
    /// @throws com.google.gson.JsonIOException     if there is a problem reading from `reader`
    /// @throws com.google.gson.JsonSyntaxException if the JSON is malformed
    public static <T> @Nullable T fromJson(Gson gson, Reader reader, Class<T> type) {
        return gson.<@Nullable T>fromJson(reader, type);
    }

    /// Deserializes JSON from a [Reader] into an object of the given class using [GSON].
    ///
    /// @param <T>    the target type
    /// @param reader the [Reader] supplying JSON content
    /// @param type   the target class
    /// @return the deserialized object, or `null` if the JSON is `null`
    /// @throws com.google.gson.JsonIOException     if there is a problem reading from `reader`
    /// @throws com.google.gson.JsonSyntaxException if the JSON is malformed
    public static <T> @Nullable T fromJson(Reader reader, Class<T> type) {
        return fromJson(GSON, reader, type);
    }

    /// Deserializes JSON from a [Reader] into an object described by the given [TypeToken]
    /// using the provided [Gson] instance.
    ///
    /// @param <T>    the target type
    /// @param gson   the [Gson] instance to use
    /// @param reader the [Reader] supplying JSON content
    /// @param type   a [TypeToken] describing the target type
    /// @return the deserialized object, or `null` if the JSON is `null`
    /// @throws com.google.gson.JsonIOException     if there is a problem reading from `reader`
    /// @throws com.google.gson.JsonSyntaxException if the JSON is malformed
    public static <T> @Nullable T fromJson(Gson gson, Reader reader, TypeToken<T> type) {
        return gson.<@Nullable T>fromJson(reader, type);
    }

    /// Deserializes JSON from a [Reader] into an object described by the given [TypeToken]
    /// using [GSON].
    ///
    /// @param <T>    the target type
    /// @param reader the [Reader] supplying JSON content
    /// @param type   a [TypeToken] describing the target type
    /// @return the deserialized object, or `null` if the JSON is `null`
    /// @throws com.google.gson.JsonIOException     if there is a problem reading from `reader`
    /// @throws com.google.gson.JsonSyntaxException if the JSON is malformed
    public static <T> @Nullable T fromJson(Reader reader, TypeToken<T> type) {
        return fromJson(GSON, reader, type);
    }

    /// Reads and deserializes a JSON file at the given [Path] into an object of the given class
    /// using the provided [Gson] instance.
    ///
    /// @param <T>      the target type
    /// @param gson     the [Gson] instance to use
    /// @param file     the path to the JSON file
    /// @param classOfT the target class
    /// @return the deserialized object, or `null` if the JSON root is `null`
    /// @throws IOException                         if an I/O error occurs reading the file
    /// @throws com.google.gson.JsonSyntaxException if the file content is not valid JSON for `classOfT`
    public static <T> @Nullable T fromJsonFile(Gson gson, Path file, Class<T> classOfT) throws IOException {
        return fromJsonFile(gson, file, TypeToken.get(classOfT));
    }

    /// Reads and deserializes a JSON file at the given [Path] into an object of the given class
    /// using [GSON].
    ///
    /// @param <T>      the target type
    /// @param file     the path to the JSON file
    /// @param classOfT the target class
    /// @return the deserialized object, or `null` if the JSON root is `null`
    /// @throws IOException                         if an I/O error occurs reading the file
    /// @throws com.google.gson.JsonSyntaxException if the file content is not valid JSON for `classOfT`
    public static <T> @Nullable T fromJsonFile(Path file, Class<T> classOfT) throws IOException {
        return fromJsonFile(GSON, file, classOfT);
    }

    /// Reads and deserializes a JSON file at the given [Path] into an object described by the
    /// given [TypeToken] using the provided [Gson] instance.
    ///
    /// @param <T>  the target type
    /// @param gson the [Gson] instance to use
    /// @param file the path to the JSON file
    /// @param type a [TypeToken] describing the target type
    /// @return the deserialized object, or `null` if the JSON root is `null`
    /// @throws IOException                         if an I/O error occurs reading the file
    /// @throws com.google.gson.JsonSyntaxException if the file content is not valid JSON for `type`
    public static <T> @Nullable T fromJsonFile(Gson gson, Path file, TypeToken<T> type) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            return gson.fromJson(reader, type.getType());
        }
    }

    /// Reads and deserializes a JSON file at the given [Path] into an object described by the
    /// given [TypeToken] using [GSON].
    ///
    /// @param <T>  the target type
    /// @param file the path to the JSON file
    /// @param type a [TypeToken] describing the target type
    /// @return the deserialized object, or `null` if the JSON root is `null`
    /// @throws IOException                         if an I/O error occurs reading the file
    /// @throws com.google.gson.JsonSyntaxException if the file content is not valid JSON for `type`
    public static <T> @Nullable T fromJsonFile(Path file, TypeToken<T> type) throws IOException {
        return fromJsonFile(GSON, file, type);
    }

    /// Reads and deserializes JSON from an [InputStream] (UTF-8) into an object of the given
    /// class using [GSON].  The stream is closed after reading.
    ///
    /// @param <T>      the target type
    /// @param json     the input stream containing UTF-8 JSON
    /// @param classOfT the target class
    /// @return the deserialized object, or `null` if the JSON root is `null`
    /// @throws IOException        if an I/O error occurs reading the stream
    /// @throws JsonParseException if the JSON is malformed or cannot be deserialized
    public static <T> @Nullable T fromJsonFully(InputStream json, Class<T> classOfT) throws IOException, JsonParseException {
        try (InputStreamReader reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            return GSON.<@Nullable T>fromJson(reader, classOfT);
        }
    }

    /// Reads and deserializes JSON from an [InputStream] (UTF-8) into an object described by
    /// the given [TypeToken] using [GSON].  The stream is closed after reading.
    ///
    /// @param <T>  the target type
    /// @param json the input stream containing UTF-8 JSON
    /// @param type a [TypeToken] describing the target type
    /// @return the deserialized object, or `null` if the JSON root is `null`
    /// @throws IOException        if an I/O error occurs reading the stream
    /// @throws JsonParseException if the JSON is malformed or cannot be deserialized
    public static <T> @Nullable T fromJsonFully(InputStream json, TypeToken<T> type) throws IOException, JsonParseException {
        try (InputStreamReader reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            return GSON.<@Nullable T>fromJson(reader, type);
        }
    }

    /// Deserializes a JSON string into a non-null object of the given class using the provided
    /// [Gson] instance.  Throws [JsonParseException] if the result would be `null`.
    ///
    /// @param <T>      the target type
    /// @param gson     the [Gson] instance to use
    /// @param json     the JSON string to parse
    /// @param classOfT the target class
    /// @return the deserialized object, never `null`
    /// @throws JsonParseException if the JSON is `null` or cannot be deserialized
    public static <T> T fromNonNullJson(Gson gson, String json, Class<T> classOfT) throws JsonParseException {
        return fromNonNullJson(gson, json, TypeToken.get(classOfT));
    }

    /// Deserializes a JSON string into a non-null object of the given class using [GSON].
    /// Throws [JsonParseException] if the result would be `null`.
    ///
    /// @param <T>      the target type
    /// @param json     the JSON string to parse
    /// @param classOfT the target class
    /// @return the deserialized object, never `null`
    /// @throws JsonParseException if the JSON is `null` or cannot be deserialized
    public static <T> T fromNonNullJson(String json, Class<T> classOfT) throws JsonParseException {
        return fromNonNullJson(GSON, json, classOfT);
    }

    /// Deserializes a JSON string into a non-null object described by the given [TypeToken]
    /// using the provided [Gson] instance.  Throws [JsonParseException] if the result would be
    /// `null`.
    ///
    /// @param <T>  the target type
    /// @param gson the [Gson] instance to use
    /// @param json the JSON string to parse
    /// @param type a [TypeToken] describing the target type
    /// @return the deserialized object, never `null`
    /// @throws JsonParseException if the JSON is `null` or cannot be deserialized
    public static <T> T fromNonNullJson(Gson gson, String json, TypeToken<T> type) throws JsonParseException {
        T parsed = fromJson(gson, json, type);
        if (parsed == null)
            throw new JsonParseException("Json object cannot be null.");
        return parsed;
    }

    /// Deserializes a JSON string into a non-null object described by the given [TypeToken]
    /// using [GSON].  Throws [JsonParseException] if the result would be `null`.
    ///
    /// @param <T>  the target type
    /// @param json the JSON string to parse
    /// @param type a [TypeToken] describing the target type
    /// @return the deserialized object, never `null`
    /// @throws JsonParseException if the JSON is `null` or cannot be deserialized
    public static <T> T fromNonNullJson(String json, TypeToken<T> type) throws JsonParseException {
        return fromNonNullJson(GSON, json, type);
    }

    /// Reads and deserializes JSON from an [InputStream] (UTF-8) into a non-null object of the
    /// given class using the provided [Gson] instance.  The stream is closed after reading.
    /// Throws [JsonParseException] if the result would be `null`.
    ///
    /// @param <T>         the target type
    /// @param gson        the [Gson] instance to use
    /// @param inputStream the input stream containing UTF-8 JSON
    /// @param classOfT    the target class
    /// @return the deserialized object, never `null`
    /// @throws IOException        if an I/O error occurs reading the stream
    /// @throws JsonParseException if the JSON is `null` or cannot be deserialized
    public static <T> T fromNonNullJsonFully(Gson gson, InputStream inputStream, Class<T> classOfT) throws IOException, JsonParseException {
        return fromNonNullJsonFully(gson, inputStream, TypeToken.get(classOfT));
    }

    /// Reads and deserializes JSON from an [InputStream] (UTF-8) into a non-null object of the
    /// given class using [GSON].  The stream is closed after reading.
    /// Throws [JsonParseException] if the result would be `null`.
    ///
    /// @param <T>         the target type
    /// @param inputStream the input stream containing UTF-8 JSON
    /// @param classOfT    the target class
    /// @return the deserialized object, never `null`
    /// @throws IOException        if an I/O error occurs reading the stream
    /// @throws JsonParseException if the JSON is `null` or cannot be deserialized
    public static <T> T fromNonNullJsonFully(InputStream inputStream, Class<T> classOfT) throws IOException, JsonParseException {
        return fromNonNullJsonFully(GSON, inputStream, classOfT);
    }

    /// Reads and deserializes JSON from an [InputStream] (UTF-8) into a non-null object
    /// described by the given [TypeToken] using the provided [Gson] instance.  The stream is
    /// closed after reading.  Throws [JsonParseException] if the result would be `null`.
    ///
    /// @param <T>         the target type
    /// @param gson        the [Gson] instance to use
    /// @param inputStream the input stream containing UTF-8 JSON
    /// @param type        a [TypeToken] describing the target type
    /// @return the deserialized object, never `null`
    /// @throws IOException        if an I/O error occurs reading the stream
    /// @throws JsonParseException if the JSON is `null` or cannot be deserialized
    public static <T> T fromNonNullJsonFully(Gson gson, InputStream inputStream, TypeToken<T> type) throws IOException, JsonParseException {
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            T parsed = fromJson(gson, reader, type);
            if (parsed == null)
                throw new JsonParseException("Json object cannot be null.");
            return parsed;
        }
    }

    /// Reads and deserializes JSON from an [InputStream] (UTF-8) into a non-null object
    /// described by the given [TypeToken] using [GSON].  The stream is closed after reading.
    /// Throws [JsonParseException] if the result would be `null`.
    ///
    /// @param <T>         the target type
    /// @param inputStream the input stream containing UTF-8 JSON
    /// @param type        a [TypeToken] describing the target type
    /// @return the deserialized object, never `null`
    /// @throws IOException        if an I/O error occurs reading the stream
    /// @throws JsonParseException if the JSON is `null` or cannot be deserialized
    public static <T> T fromNonNullJsonFully(InputStream inputStream, TypeToken<T> type) throws IOException, JsonParseException {
        return fromNonNullJsonFully(GSON, inputStream, type);
    }

    /// Deserializes a possibly malformed JSON string into an object of the given class using
    /// the provided [Gson] instance.  Returns `null` if the JSON is syntactically invalid
    /// ([com.google.gson.JsonSyntaxException] is swallowed).
    ///
    /// @param <T>      the target type
    /// @param gson     the [Gson] instance to use
    /// @param json     the JSON string to parse
    /// @param classOfT the target class
    /// @return the deserialized object, or `null` if the JSON is `null` or syntactically invalid
    /// @throws JsonParseException if the JSON is well-formed but semantically invalid
    public static <T> @Nullable T fromMaybeMalformedJson(Gson gson, String json, Class<T> classOfT) throws JsonParseException {
        return fromMaybeMalformedJson(gson, json, TypeToken.get(classOfT));
    }

    /// Deserializes a possibly malformed JSON string into an object of the given class using
    /// [GSON].  Returns `null` if the JSON is syntactically invalid
    /// ([com.google.gson.JsonSyntaxException] is swallowed).
    ///
    /// @param <T>      the target type
    /// @param json     the JSON string to parse
    /// @param classOfT the target class
    /// @return the deserialized object, or `null` if the JSON is `null` or syntactically invalid
    /// @throws JsonParseException if the JSON is well-formed but semantically invalid
    public static <T> @Nullable T fromMaybeMalformedJson(String json, Class<T> classOfT) throws JsonParseException {
        return fromMaybeMalformedJson(GSON, json, classOfT);
    }

    /// Deserializes a possibly malformed JSON string into an object described by the given
    /// [TypeToken] using the provided [Gson] instance.  Returns `null` if the JSON is
    /// syntactically invalid ([com.google.gson.JsonSyntaxException] is swallowed).
    ///
    /// @param <T>  the target type
    /// @param gson the [Gson] instance to use
    /// @param json the JSON string to parse
    /// @param type a [TypeToken] describing the target type
    /// @return the deserialized object, or `null` if the JSON is `null` or syntactically invalid
    /// @throws JsonParseException if the JSON is well-formed but semantically invalid
    public static <T> @Nullable T fromMaybeMalformedJson(Gson gson, String json, TypeToken<T> type) throws JsonParseException {
        try {
            return gson.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /// Deserializes a possibly malformed JSON string into an object described by the given
    /// [TypeToken] using [GSON].  Returns `null` if the JSON is syntactically invalid
    /// ([com.google.gson.JsonSyntaxException] is swallowed).
    ///
    /// @param <T>  the target type
    /// @param json the JSON string to parse
    /// @param type a [TypeToken] describing the target type
    /// @return the deserialized object, or `null` if the JSON is `null` or syntactically invalid
    /// @throws JsonParseException if the JSON is well-formed but semantically invalid
    public static <T> @Nullable T fromMaybeMalformedJson(String json, TypeToken<T> type) throws JsonParseException {
        return fromMaybeMalformedJson(GSON, json, type);
    }

    /// Serializes `value` to JSON and writes it to the file at the given [Path] using [GSON].
    /// The file is created or overwritten atomically by the underlying [java.nio.file.Files]
    /// API.
    ///
    /// @param file  the path to the output file
    /// @param value the object to serialize; may be `null`, in which case the JSON `null`
    ///              literal is written
    /// @throws IOException if an I/O error occurs creating or writing the file
    public static void writeToJsonFile(Path file, @Nullable Object value) throws IOException {
        try (var writer = Files.newBufferedWriter(file)) {
            GSON.toJson(value, writer);
        }
    }

    /// Creates and returns a pre-configured [GsonBuilder] used to construct [GSON].
    ///
    /// The builder has the following configuration applied:
    /// - Complex map key serialization enabled.
    /// - Pretty printing enabled.
    /// - [InstantTypeAdapter] registered for [java.time.Instant].
    /// - [UUIDTypeAdapter] registered for [java.util.UUID].
    /// - [PathTypeAdapter] registered for [java.nio.file.Path].
    /// - [ValidationTypeAdapterFactory], [LowerCaseEnumTypeAdapterFactory], and
    ///   [JsonTypeAdapterFactory] registered as type adapter factories.
    ///
    /// Callers may further customize the returned builder before calling
    /// [GsonBuilder#create()].
    ///
    /// @return a new [GsonBuilder] with the default HMCL JSON configuration
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
