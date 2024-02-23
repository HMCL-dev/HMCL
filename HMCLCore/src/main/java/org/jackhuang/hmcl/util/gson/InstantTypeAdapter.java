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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

public final class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
    public static final InstantTypeAdapter INSTANCE = new InstantTypeAdapter();

    private InstantTypeAdapter() {
    }

    @Override
    public JsonElement serialize(Instant t, Type type, JsonSerializationContext jsc) {
        return new JsonPrimitive(serializeToString(t, ZoneId.systemDefault()));
    }

    @Override
    public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        if (!(json instanceof JsonPrimitive))
            throw new JsonParseException("The instant should be a string value");
        else {
            Instant time = deserializeToInstant(json.getAsString());
            if (type == Instant.class)
                return time;
            else
                throw new IllegalArgumentException(this.getClass() + " cannot be deserialized to " + type);
        }
    }

    private static final DateTimeFormatter EN_US_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM)
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter ISO_DATE_TIME = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
            .optionalStart().appendOffset("+HH", "Z").optionalEnd()
            .optionalStart().appendOffsetId().optionalEnd()
            .toFormatter();

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

    public static String serializeToString(Instant instant, ZoneId zone) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS));
    }
}
