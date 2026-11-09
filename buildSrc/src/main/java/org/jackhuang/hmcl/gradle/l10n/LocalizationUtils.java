// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.l10n;

import org.gradle.api.GradleException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class LocalizationUtils {
    public static final Map<String, String> subLanguageToParent = loadCSV("sublanguages.csv");

    private static Map<String, String> loadCSV(String fileName) {
        InputStream resource = LocalizationUtils.class.getResourceAsStream(fileName);
        if (resource == null) {
            throw new GradleException("Resource not found: " + fileName);
        }

        HashMap<String, String> result = new HashMap<>();
        try (resource) {
            new String(resource.readAllBytes(), StandardCharsets.UTF_8).lines().forEach(line -> {
                if (line.startsWith("#") || line.isBlank())
                    return;

                String[] items = line.split(",");
                if (items.length < 2) {
                    throw new GradleException("Invalid line in sublanguages.csv: " + line);
                }

                String parent = items[0];
                for (int i = 1; i < items.length; i++) {
                    result.put(items[i], parent);
                }
            });
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new GradleException("Failed to load " + fileName, e);
        }

        return Map.copyOf(result);
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

    public static int compareScript(String s1, String s2) {
        return s1.compareTo(s2);
    }

    public static int compareLocale(Locale l1, Locale l2) {
        int c = compareLanguage(l1.getLanguage(), l2.getLanguage());
        if (c != 0)
            return c;

        c = compareScript(l1.getScript(), l2.getScript());
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
