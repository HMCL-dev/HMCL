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
package org.jackhuang.hmcl.util.i18n;

import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class Locales {
    private Locales() {
    }

    public static final SupportedLocale DEFAULT = new SupportedLocale();

    public static List<SupportedLocale> getSupportedLocales() {
        List<SupportedLocale> list = new ArrayList<>();
        list.add(DEFAULT);

        InputStream locales = Locales.class.getResourceAsStream("/assets/lang/languages.json");
        if (locales != null) {
            try (locales) {
                list.addAll(JsonUtils.fromNonNullJsonFully(locales, JsonUtils.listTypeOf(SupportedLocale.class)));
            } catch (Throwable e) {
                LOG.warning("Failed to load languages.json", e);
            }
        }
        return List.copyOf(list);
    }

    private static final ConcurrentMap<Locale, SupportedLocale> LOCALES = new ConcurrentHashMap<>();

    public static SupportedLocale getLocale(Locale locale) {
        return LOCALES.computeIfAbsent(locale, SupportedLocale::new);
    }

    public static SupportedLocale getLocaleByName(String name) {
        if (name == null || name.isEmpty() || "def".equals(name) || "default".equals(name))
            return DEFAULT;

        return getLocale(Locale.forLanguageTag(name.trim()));
    }

}
