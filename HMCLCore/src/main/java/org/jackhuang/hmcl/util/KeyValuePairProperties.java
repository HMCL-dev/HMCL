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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/**
 * @author Glavo
 */
public final class KeyValuePairProperties extends LinkedHashMap<String, String> {
    public static KeyValuePairProperties load(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return load(reader);
        }
    }

    public static KeyValuePairProperties load(BufferedReader reader) throws IOException {
        KeyValuePairProperties result = new KeyValuePairProperties();

        String line;
        while ((line = reader.readLine()) != null) {
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
}
