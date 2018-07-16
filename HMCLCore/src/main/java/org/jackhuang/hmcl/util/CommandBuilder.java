/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class CommandBuilder {
    private final OperatingSystem os;
    private List<Item> raw = new LinkedList<>();

    public CommandBuilder() {
        this(OperatingSystem.CURRENT_OS);
    }

    public CommandBuilder(OperatingSystem os) {
        this.os = os;
    }

    private String parse(String s) {
        if (OperatingSystem.WINDOWS == os) {
            return parseWindows(s);
        } else {
            return parseBash(s);
        }
    }

    public CommandBuilder add(String... args) {
        for (String s : args)
            raw.add(new Item(s, true));
        return this;
    }

    public CommandBuilder addAll(Collection<String> args) {
        for (String s : args)
            raw.add(new Item(s, true));
        return this;
    }

    public CommandBuilder addWithoutParsing(String... args) {
        for (String s : args)
            raw.add(new Item(s, false));
        return this;
    }

    public CommandBuilder addAllWithoutParsing(Collection<String> args) {
        for (String s : args)
            raw.add(new Item(s, false));
        return this;
    }

    public boolean removeIf(Predicate<String> pred) {
        return raw.removeIf(i -> pred.test(i.arg));
    }

    @Override
    public String toString() {
        return String.join(" ", raw.stream().map(i -> i.parse ? parse(i.arg) : i.arg).collect(Collectors.toList()));
    }

    public List<String> asList() {
        return raw.stream().map(i -> i.arg).collect(Collectors.toList());
    }

    private static class Item {
        String arg;
        boolean parse;

        Item(String arg, boolean parse) {
            this.arg = arg;
            this.parse = parse;
        }
    }

    private static String parseWindows(String s) {
        if (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0)
            if (s.charAt(0) != '"') {
                // The argument has not been quoted, add quotes.
                return '"' + s.replace("\\", "\\\\").replace("\"", "\"\"") + '"';
            } else if (s.endsWith("\"")) {
                // The argument has already been quoted.
                return s;
            } else {
                // Unmatched quote for the argument.
                throw new IllegalArgumentException();
            }
        else {
            return s;
        }
    }

    private static String parseBash(String s) {
        if (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0)
            if (s.charAt(0) != '"') {
                // The argument has not been quoted, add quotes.
                return '"' + s.replace("\"", "\\\"") + '"';
            } else if (s.endsWith("\"")) {
                // The argument has already been quoted.
                return s;
            } else {
                // Unmatched quote for the argument.
                throw new IllegalArgumentException();
            }
        else
            return s;
    }
}
