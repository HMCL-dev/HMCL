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
package org.jackhuang.hmcl.gradle.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// @author Glavo
public record Document(DocumentFileTree directory,
                       Path file,
                       String name, DocumentLocale locale,
                       List<Item> items) {

    private static final Pattern MACRO_BEGIN = Pattern.compile(
            "<!-- #BEGIN (?<name>\\w+) -->"
    );

    private static final Pattern MACRO_PROPERTY_LINE = Pattern.compile(
            "<!-- #PROPERTY (?<name>\\w+)=(?<value>.*) -->"
    );

    private static String parsePropertyValue(String value) {
        int i = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (ch == '\\')
                break;
            i++;
        }

        if (i == value.length())
            return value;

        StringBuilder builder = new StringBuilder(value.length());
        builder.append(value, 0, i);
        for (; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i < value.length() - 1) {
                char next = value.charAt(++i);
                switch (next) {
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case '\\' -> builder.append('\\');
                    default -> builder.append(next);
                }
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    static void writePropertyValue(StringBuilder builder, String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);

            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '\r' -> builder.append("\\r");
                case '\n' -> builder.append("\\n");
                default -> builder.append(ch);
            }
        }
    }

    public static Document load(DocumentFileTree directory, Path file, String name, DocumentLocale locale) throws IOException {
        var items = new ArrayList<Item>();
        try (var reader = Files.newBufferedReader(file)) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("<!-- #")) {
                    items.add(new Line(line));
                } else {
                    Matcher matcher = MACRO_BEGIN.matcher(line);
                    if (!matcher.matches())
                        throw new IOException("Invalid macro begin line: " + line);

                    String macroName = matcher.group("name");
                    String endLine = "<!-- #END " + macroName + " -->";
                    var lines = new ArrayList<String>();
                    while (true) {
                        line = reader.readLine();

                        if (line == null)
                            throw new IOException("Missing end line for macro: " + macroName);
                        else if (line.equals(endLine)) {
                            break;
                        } else {
                            lines.add(line);
                        }
                    }

                    var properties = new LinkedHashMap<String, List<String>>();
                    int propertiesCount = 0;

                    // Handle properties
                    for (String macroBodyLine : lines) {
                        if (!macroBodyLine.startsWith("<!-- #"))
                            break;

                        Matcher propertyMatcher = MACRO_PROPERTY_LINE.matcher(macroBodyLine);
                        if (propertyMatcher.matches()) {
                            String propertyName = propertyMatcher.group("name");
                            String propertyValue = parsePropertyValue(propertyMatcher.group("value"));

                            properties.computeIfAbsent(propertyName, k -> new ArrayList<>(1))
                                    .add(propertyValue);
                            propertiesCount++;
                        } else {
                            throw new IOException("Invalid macro property line: " + macroBodyLine);
                        }
                    }

                    if (propertiesCount > 0)
                        lines.subList(0, propertiesCount).clear();

                    items.add(new MacroBlock(macroName,
                            Collections.unmodifiableMap(properties),
                            Collections.unmodifiableList(lines)));
                }
            }
        }
        return new Document(directory, file, name, locale, items);
    }

    public sealed interface Item {
    }

    public record MacroBlock(String name, Map<String, List<String>> properties,
                             List<String> contentLines) implements Item {
    }

    public record Line(String content) implements Item {
    }
}
