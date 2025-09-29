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
package org.jackhuang.hmcl.util.i18n;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.Map;

/// @author Glavo
public final class UpsideDownUtils {
    private static final Map<Integer, Integer> MAPPER = new LinkedHashMap<>();

    private static void putChars(char baseChar, String upsideDownChars) {
        for (int i = 0; i < upsideDownChars.length(); i++) {
            MAPPER.put(baseChar + i, (int) upsideDownChars.charAt(i));
        }
    }

    private static void putChars(String baseChars, String upsideDownChars) {
        if (baseChars.length() != upsideDownChars.length()) {
            throw new IllegalArgumentException("baseChars and upsideDownChars must have same length");
        }

        for (int i = 0; i < baseChars.length(); i++) {
            MAPPER.put((int) baseChars.charAt(i), (int) upsideDownChars.charAt(i));
        }
    }

    static {
        putChars('a', "ɐqɔpǝɟbɥıظʞןɯuodbɹsʇnʌʍxʎz");
        putChars('A', "ⱯᗺƆᗡƎℲ⅁HIſʞꞀWNOԀὉᴚS⟘∩ΛMXʎZ");
        putChars('0', "0ƖᘔƐㄣϛ9ㄥ86");
        putChars("_,;.?!/\\'", "‾'⸵˙¿¡/\\,");
    }

    public static String translate(String str) {
        StringBuilder builder = new StringBuilder(str.length());
        str.codePoints().forEach(ch -> builder.appendCodePoint(MAPPER.getOrDefault(ch, ch)));
        return builder.reverse().toString();
    }

    private static DateTimeFormatter BASE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a")
            .withZone(ZoneId.systemDefault());

    public static String formatDateTime(TemporalAccessor time) {
        return translate(BASE_FORMATTER.format(time));
    }

    private UpsideDownUtils() {
    }
}
