/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 *
 * @author huangyuhui
 */
public final class DateTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {

    public static final DateTypeAdapter INSTANCE = new DateTypeAdapter();

    private DateTypeAdapter() {
    }

    @Override
    public JsonElement serialize(Date t, Type type, JsonSerializationContext jsc) {
        synchronized (EN_US_FORMAT) {
            return new JsonPrimitive(serializeToString(t));
        }
    }

    @Override
    public Date deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        if (!(json instanceof JsonPrimitive))
            throw new JsonParseException("The date should be a string value");
        else {
            Date date = deserializeToDate(json.getAsString());
            if (type == Date.class)
                return date;
            else
                throw new IllegalArgumentException(this.getClass().toString() + " cannot be deserialized to " + type);
        }
    }

    public static final DateFormat EN_US_FORMAT = DateFormat.getDateTimeInstance(2, 2, Locale.US);
    public static final DateFormat ISO_8601_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    public static Date deserializeToDate(String string) {
        synchronized (EN_US_FORMAT) {
            try {
                return EN_US_FORMAT.parse(string);
            } catch (ParseException ex1) {
                try {
                    return ISO_8601_FORMAT.parse(string);
                } catch (ParseException ex2) {
                    try {
                        String cleaned = string.replace("Z", "+00:00");
                        cleaned = cleaned.substring(0, 22) + cleaned.substring(23);
                        return ISO_8601_FORMAT.parse(cleaned);
                    } catch (Exception e) {
                        throw new JsonParseException("Invalid date: " + string, e);
                    }
                }
            }
        }
    }

    public static String serializeToString(Date date) {
        synchronized (EN_US_FORMAT) {
            String result = ISO_8601_FORMAT.format(date);
            return result.substring(0, 22) + ":" + result.substring(22);
        }
    }

}
