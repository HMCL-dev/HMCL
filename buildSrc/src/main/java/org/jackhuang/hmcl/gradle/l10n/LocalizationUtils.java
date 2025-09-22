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
package org.jackhuang.hmcl.gradle.l10n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.gradle.api.GradleException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class LocalizationUtils {
    public static final Map<String, String> subLanguageToParent;

    static {
        InputStream file = LocalizationUtils.class.getResourceAsStream("sublanguages.json");
        if (file == null)
            throw new GradleException("Missing sublanguages.json file");


        Map<String, String> map = new HashMap<>();
        try (var reader = new InputStreamReader(file, StandardCharsets.UTF_8)) {
            new Gson().fromJson(reader, new TypeToken<Map<String, List<String>>>() {
            }).forEach((parent, subList) -> {
                for (String subLanguage : subList) {
                    map.put(subLanguage, parent);
                }
            });
        } catch (IOException e) {
            throw new GradleException(e.getMessage(), e);
        }

        subLanguageToParent = Collections.unmodifiableMap(map);
    }

    private static List<String> resolveLanguage(String language) {
        List<String> langList = new ArrayList<>();

        String lang = language;
        while (true) {
            langList.add(0, lang);

            String parent = subLanguageToParent.get(lang);
            if (parent != null) {
                lang = parent;
            } else {
                return langList;
            }
        }
    }

    public static int compareLanguage(String l1, String l2) {
        var list1 = resolveLanguage(l1);
        var list2 = resolveLanguage(l2);

        int n = Math.min(list1.size(), list2.size());
        for (int i = 0; i < n; i++) {
            int c = list1.get(i).compareTo(list2.get(i));
            if (c != 0)
                return c;
        }

        return Integer.compare(list1.size(), list2.size());
    }

    public static int compareLocale(Locale l1, Locale l2) {
        int c = compareLanguage(l1.getLanguage(), l2.getLanguage());
        if (c != 0)
            return c;

        c = l1.getScript().compareTo(l2.getScript());
        if (c != 0)
            return c;

        c = l1.getCountry().compareTo(l2.getCountry());
        if (c != 0)
            return c;

        c = l1.getVariant().compareTo(l2.getVariant());
        if (c != 0)
            return c;

        return l1.toString().compareTo(l2.toLanguageTag());
    }

    private LocalizationUtils() {
    }
}
