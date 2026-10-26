// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.i18n.translator;

import org.jackhuang.hmcl.download.ComponentRemoteVersion;
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
    public String getDisplayVersion(ComponentRemoteVersion remoteVersion) {
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
