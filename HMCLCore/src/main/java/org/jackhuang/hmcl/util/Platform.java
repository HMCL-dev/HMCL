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

/**
 * The platform that indicates which the platform of operating system is, 64-bit or 32-bit.
 * Of course, 128-bit and 16-bit is not supported.
 *
 * @author huangyuhui
 */
public enum Platform {
    BIT_32("32"),
    BIT_64("64"),
    UNKNOWN("unknown");

    private final String bit;

    Platform(String bit) {
        this.bit = bit;
    }

    public String getBit() {
        return bit;
    }

    /**
     * True if current Java Environment is 64-bit.
     */
    public static final boolean IS_64_BIT = OperatingSystem.SYSTEM_ARCHITECTURE.contains("64");

    /**
     * The platform of current Java Environment.
     */
    public static final Platform PLATFORM = IS_64_BIT ? BIT_64 : BIT_32;

    /**
     * The json serializer to {@link Platform}.
     */
    public static class Serializer implements JsonSerializer<Platform>, JsonDeserializer<Platform> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public JsonElement serialize(Platform t, Type type, JsonSerializationContext jsc) {
            if (t == null)
                return null;
            else
                switch (t) {
                    case BIT_32:
                        return new JsonPrimitive(0);
                    case BIT_64:
                        return new JsonPrimitive(1);
                    default:
                        return new JsonPrimitive(-1);
                }
        }

        @Override
        public Platform deserialize(JsonElement je, Type type, JsonDeserializationContext jdc) throws JsonParseException {
            if (je == null)
                return null;
            else
                switch (je.getAsInt()) {
                    case 0:
                        return BIT_32;
                    case 1:
                        return BIT_64;
                    default:
                        return UNKNOWN;
                }
        }

    }

}
