/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class CommandBuilder {
    private static final Pattern UNSTABLE_OPTION_PATTERN = Pattern.compile("-XX:(?<key>[a-zA-Z0-9]+)=(?<value>.*)");
    private static final Pattern UNSTABLE_BOOLEAN_OPTION_PATTERN = Pattern.compile("-XX:(?<value>[+\\-])(?<key>[a-zA-Z0-9]+)");

    private final OperatingSystem os;
    private final List<Item> raw = new LinkedList<>();

    public CommandBuilder() {
        this(OperatingSystem.CURRENT_OS);
    }

    public CommandBuilder(OperatingSystem os) {
        this.os = os;
    }

    private String parse(String s) {
        if (OperatingSystem.WINDOWS == os) {
            return parseBatch(s);
        } else {
            return parseShell(s);
        }
    }

    /**
     * Parsing will ignore your manual escaping
     *
     * @param args commands
     * @return this
     */
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

    public CommandBuilder addDefault(String opt) {
        for (Item item : raw) {
            if (item.arg.equals(opt)) {
                return this;
            }
        }
        raw.add(new Item(opt, true));
        return this;
    }

    public CommandBuilder addDefault(String opt, String value) {
        for (Item item : raw) {
            if (item.arg.startsWith(opt)) {
                LOG.info("Default option '" + opt + value + "' is suppressed by '" + item.arg + "'");
                return this;
            }
        }
        raw.add(new Item(opt + value, true));
        return this;
    }

    public CommandBuilder addUnstableDefault(String opt, boolean value) {
        for (Item item : raw) {
            final Matcher matcher = UNSTABLE_BOOLEAN_OPTION_PATTERN.matcher(item.arg);
            if (matcher.matches()) {
                if (matcher.group("key").equals(opt)) {
                    return this;
                }
            }
        }

        if (value) {
            raw.add(new Item("-XX:+" + opt, true));
        } else {
            raw.add(new Item("-XX:-" + opt, true));
        }
        return this;
    }

    public CommandBuilder addUnstableDefault(String opt, String value) {
        for (Item item : raw) {
            final Matcher matcher = UNSTABLE_OPTION_PATTERN.matcher(item.arg);
            if (matcher.matches()) {
                if (matcher.group("key").equals(opt)) {
                    return this;
                }
            }
        }

        raw.add(new Item("-XX:" + opt + "=" + value, true));
        return this;
    }

    public boolean removeIf(Predicate<String> pred) {
        return raw.removeIf(i -> pred.test(i.arg));
    }

    @Override
    public String toString() {
        return raw.stream().map(i -> i.parse ? parse(i.arg) : i.arg).collect(Collectors.joining(" "));
    }

    public List<String> asList() {
        return raw.stream().map(i -> i.arg).collect(Collectors.toList());
    }

    public List<String> asMutableList() {
        return raw.stream().map(i -> i.arg).collect(Collectors.toCollection(ArrayList::new));
    }

    private static class Item {
        String arg;
        boolean parse;

        Item(String arg, boolean parse) {
            this.arg = arg;
            this.parse = parse;
        }

        @Override
        public String toString() {
            return parse ? (OperatingSystem.WINDOWS == OperatingSystem.CURRENT_OS ? parseBatch(arg) : parseShell(arg)) : arg;
        }
    }

    public static String pwshString(String str) {
        return "'" + str.replace("'", "''") + "'";
    }

    public static boolean hasExecutionPolicy() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS) {
            return true;
        }
        try {
            final Process process = Runtime.getRuntime().exec("powershell -Command Get-ExecutionPolicy");
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroy();
                return false;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), OperatingSystem.NATIVE_CHARSET))) {
                String policy = reader.readLine();
                return "Unrestricted".equalsIgnoreCase(policy) || "RemoteSigned".equalsIgnoreCase(policy);
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setExecutionPolicy() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS) {
            return true;
        }
        try {
            final Process process = Runtime.getRuntime().exec(new String[]{"powershell", "-Command", "Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser"});
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroy();
                return false;
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static String parseBatch(String s) {
        String escape = " \t\"^&<>|";
        if (StringUtils.containsOne(s, escape.toCharArray()))
            // The argument has not been quoted, add quotes.
            return '"' + s
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    + '"';
        else {
            return s;
        }
    }

    private static String parseShell(String s) {
        String escaping = " \t\"!#$&'()*,;<=>?[\\]^`{|}~";
        String escaped = "\"$&`";
        if (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0 || StringUtils.containsOne(s, escaping.toCharArray())) {
            // The argument has not been quoted, add quotes.
            for (char ch : escaped.toCharArray())
                s = s.replace("" + ch, "\\" + ch);
            return '"' + s + '"';
        } else
            return s;
    }
}
