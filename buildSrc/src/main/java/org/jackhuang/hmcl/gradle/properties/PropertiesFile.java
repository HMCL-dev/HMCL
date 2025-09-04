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
package org.jackhuang.hmcl.gradle.properties;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// @author Glavo
public record PropertiesFile(Map<String, Item> items) {

    private static final Pattern PATTERN = Pattern.compile("^\\s*(?<key>[^\\s:=]+)\\s*[=:]");

    public static PropertiesFile load(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            var result = new LinkedHashMap<String, Item>();
            var itemBuilder = new ArrayList<String>();

            String currentKey = null;

            String line;
            while ((line = reader.readLine()) != null) {
                itemBuilder.add(line);

                if (currentKey == null) {
                    if (!line.startsWith("#") && !line.startsWith("!") && !line.isBlank()) {
                        Matcher matcher = PATTERN.matcher(line);
                        if (matcher.find()) {
                            String key = matcher.group("key");
                            if (line.endsWith("\\")) {
                                currentKey = key;
                            } else {
                                if (result.put(key, new Item(List.copyOf(itemBuilder))) != null) {
                                    throw new IOException("Duplicate key: " + key);
                                }
                                itemBuilder.clear();
                            }
                        } else {
                            throw new IOException("Line \"" + line + "\" is invalid");
                        }
                    }
                } else if (!line.endsWith("\\")) {
                    if (result.put(currentKey, new Item(List.copyOf(itemBuilder))) != null) {
                        throw new IOException("Duplicate key: " + currentKey);
                    }
                    itemBuilder.clear();
                    currentKey = null;
                }
            }

            if (!itemBuilder.isEmpty()) {
                if (currentKey == null)
                    currentKey = "";

                if (result.put(currentKey, new Item(List.copyOf(itemBuilder))) != null) {
                    throw new IOException("Duplicate key: " + currentKey);
                }
            }

            return new PropertiesFile(Collections.unmodifiableMap(result));
        }
    }

    public record Item(List<String> lines) {

    }
}
