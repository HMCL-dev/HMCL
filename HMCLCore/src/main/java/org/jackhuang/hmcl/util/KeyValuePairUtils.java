/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Glavo
 */
public final class KeyValuePairUtils {
    public static Map<String, String> loadProperties(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return loadProperties(reader);
        }
    }

    public static Map<String, String> loadProperties(BufferedReader reader) throws IOException {
        try {
            return loadProperties(reader.lines().iterator());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public static Map<String, String> loadProperties(Iterator<String> lineIterator) {
        Map<String, String> result = new LinkedHashMap<>();
        while (lineIterator.hasNext()) {
            String line = lineIterator.next();

            if (line.startsWith("#"))
                continue;

            int idx = line.indexOf('=');
            if (idx <= 0)
                continue;

            String name = line.substring(0, idx);
            String value;

            if (line.length() > idx + 2 && line.charAt(idx + 1) == '"' && line.charAt(line.length() - 1) == '"') {
                if (line.indexOf('\\', idx + 1) < 0) {
                    value = line.substring(idx + 2, line.length() - 1);
                } else {
                    StringBuilder builder = new StringBuilder();
                    for (int i = idx + 2, end = line.length() - 1; i < end; i++) {
                        char ch = line.charAt(i);
                        if (ch == '\\' && i < end - 1) {
                            char nextChar = line.charAt(++i);
                            switch (nextChar) {
                                case 'n':
                                    builder.append('\n');
                                    break;
                                case 'r':
                                    builder.append('\r');
                                    break;
                                case 't':
                                    builder.append('\t');
                                    break;
                                case 'f':
                                    builder.append('\f');
                                    break;
                                case 'b':
                                    builder.append('\b');
                                    break;
                                default:
                                    builder.append(nextChar);
                                    break;
                            }
                        } else {
                            builder.append(ch);
                        }
                    }
                    value = builder.toString();
                }
            } else {
                value = line.substring(idx + 1);
            }

            result.put(name, value);
        }
        return result;
    }

    public static Map<String, String> loadPairs(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return loadPairs(reader);
        }
    }

    public static Map<String, String> loadPairs(BufferedReader reader) throws IOException {
        try {
            return loadPairs(reader.lines().iterator());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public static Map<String, String> loadPairs(Iterator<String> lineIterator) {
        Map<String, String> result = new LinkedHashMap<>();
        while (lineIterator.hasNext()) {
            String line = lineIterator.next();

            int idx = line.indexOf(':');
            if (idx > 0) {
                String name = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                result.put(name, value);
            }
        }
        return result;
    }

    public static List<Map<String, String>> loadList(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return loadList(reader);
        }
    }

    public static List<Map<String, String>> loadList(BufferedReader reader) throws IOException {
        try {
            return loadList(reader.lines().iterator());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public static List<Map<String, String>> loadList(Iterator<String> lineIterator) {
        ArrayList<Map<String, String>> result = new ArrayList<>();
        Map<String, String> current = new LinkedHashMap<>();

        while (lineIterator.hasNext()) {
            String line = lineIterator.next();
            int idx = line.indexOf(':');

            if (idx < 0) {
                if (!current.isEmpty()) {
                    result.add(current);
                    current = new LinkedHashMap<>();
                }
                continue;
            }

            String name = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();

            current.put(name, value);
        }

        if (!current.isEmpty())
            result.add(current);

        return result;
    }

    private KeyValuePairUtils() {
    }
}
