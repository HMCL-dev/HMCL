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
package org.jackhuang.hmcl.util.gson;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.time.temporal.ChronoField.*;

/// Serializes and deserializes [Instant] values for Gson.
///
/// Serialized values are written as ISO offset date-time strings in the system default time zone,
/// truncated to whole seconds. Deserialization accepts the historical US localized date-time
/// format, ISO local date-time values with optional offsets, and ISO local date-time values
/// interpreted in the system default time zone.
///
/// @author  Glavo
public final class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
    /// Shared adapter instance for registering with Gson.
    public static final InstantTypeAdapter INSTANCE = new InstantTypeAdapter();

    /// Creates the singleton adapter instance.
    private InstantTypeAdapter() {
    }

    /// Serializes an [Instant] to a JSON string in the system default time zone.
    ///
    /// @param t    the instant to serialize
    /// @param type the requested source type
    /// @param jsc  the Gson serialization context
    /// @return a JSON primitive containing the serialized instant
    @Override
    public JsonElement serialize(Instant t, Type type, JsonSerializationContext jsc) {
        return new JsonPrimitive(serializeToString(t, ZoneId.systemDefault()));
    }

    /// Deserializes a JSON string into an [Instant].
    ///
    /// @param json    the JSON element to deserialize
    /// @param type    the requested target type
    /// @param context the Gson deserialization context
    /// @return the parsed instant
    /// @throws JsonParseException     if `json` is not a string or cannot be parsed as an instant
    /// @throws IllegalArgumentException if `type` is not [Instant]
    @Override
    public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        if (json instanceof JsonPrimitive) {
            Instant time = deserializeToInstant(json.getAsString());
            if (type == Instant.class)
                return time;
            else
                throw new IllegalArgumentException(this.getClass() + " cannot be deserialized to " + type);
        } else {
            throw new JsonParseException("The instant should be a string value");
        }
    }

    /// Formatter for the legacy US localized date-time representation.
    private static final DateTimeFormatter EN_US_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM)
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault());

    /// Formatter for ISO local date-time text followed by one of the supported offset forms.
    private static final DateTimeFormatter ISO_DATE_TIME = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
            .optionalStart().appendOffset("+H:MM", "+0:00").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
            .optionalStart().appendOffset("+HH", "Z").optionalEnd()
            .optionalStart().appendOffsetId().optionalEnd()
            .toFormatter();

    /// Parses an instant from one of the supported textual representations.
    ///
    /// @param string the text to parse
    /// @return the parsed instant
    /// @throws JsonParseException if `string` is not in a supported instant format
    public static Instant deserializeToInstant(String string) {
        try {
            return ZonedDateTime.parse(string, EN_US_FORMAT).toInstant();
        } catch (DateTimeParseException ex1) {
            try {
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(string, ISO_DATE_TIME);
                return zonedDateTime.toInstant();
            } catch (DateTimeParseException e) {
                try {
                    LocalDateTime localDateTime = LocalDateTime.parse(string, ISO_LOCAL_DATE_TIME);
                    return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
                } catch (DateTimeParseException e2) {
                    throw new JsonParseException("Invalid instant: " + string, e);
                }
            }
        }
    }

    /// Formats an instant as an ISO offset date-time string in the given time zone.
    ///
    /// The output is truncated to whole seconds.
    ///
    /// @param instant the instant to format
    /// @param zone    the time zone used to render the instant
    /// @return the formatted instant
    public static String serializeToString(Instant instant, ZoneId zone) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS));
    }
}
