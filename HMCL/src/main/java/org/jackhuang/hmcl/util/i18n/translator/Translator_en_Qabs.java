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
package org.jackhuang.hmcl.util.i18n.translator;

import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public class Translator_en_Qabs extends Translator {
    private static final Map<Integer, Integer> MAPPER;

    static {
        var map = new LinkedHashMap<Integer, Integer>();
        InputStream inputStream = Translator_en_Qabs.class.getResourceAsStream("/assets/lang/upside_down.txt");
        if (inputStream != null) {
            try (inputStream) {
                new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).lines().forEach(line -> {
                    if (line.isBlank() || line.startsWith("#"))
                        return;

                    if (line.length() != 2) {
                        LOG.warning("Invalid line: " + line);
                        return;
                    }

                    map.put((int) line.charAt(0), (int) line.charAt(1));
                });
            } catch (IOException e) {
                LOG.warning("Failed to load upside_down.txt", e);
            }
        } else {
            LOG.warning("upside_down.txt not found");
        }
        MAPPER = Collections.unmodifiableMap(map);
    }

    public static String translate(String str) {
        StringBuilder builder = new StringBuilder(str.length());
        str.codePoints().forEach(ch -> builder.appendCodePoint(MAPPER.getOrDefault(ch, ch)));
        return builder.reverse().toString();
    }

    private final SupportedLocale originalLocale = SupportedLocale.getLocale(Locale.ENGLISH);

    public Translator_en_Qabs(SupportedLocale locale) {
        super(locale);
    }

    @Override
    public String getDisplayVersion(RemoteVersion remoteVersion) {
        return translate(remoteVersion.getSelfVersion());
    }

    @Override
    public String formatDateTime(TemporalAccessor time) {
        return translate(originalLocale.getTranslator().formatDateTime(time));
    }

    @Override
    public String formatSpeed(long bytes) {
        return translate(originalLocale.getTranslator().formatSpeed(bytes));
    }
}
