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
    private final List<Item> raw = new ArrayList<>();

    public CommandBuilder() {
        this(OperatingSystem.CURRENT_OS);
    }

    public CommandBuilder(OperatingSystem os) {
        this.os = os;
    }

    private String parse(String s) {
        if (OperatingSystem.WINDOWS == os) {
            return toBatchStringLiteral(s);
        } else {
            return toShellStringLiteral(s);
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

    public void addAllDefault(Collection<String> args) {
        addAllDefault(args, true);
    }

    public void addAllDefaultWithoutParsing(Collection<String> args) {
        addAllDefault(args, false);
    }

    private void addAllDefault(Collection<String> args, boolean parse) {
        loop:
        for (String arg : args) {
            if (arg.startsWith("-D")) {
                int idx = arg.indexOf('=');
                if (idx >= 0) {
                    addDefault(arg.substring(0, idx + 1), arg.substring(idx + 1), parse);
                } else {
                    String opt = arg + "=";
                    for (Item item : raw) {
                        if (item.arg.startsWith(opt)) {
                            LOG.info("Default option '" + arg + "' is suppressed by '" + item.arg + "'");
                            continue loop;
                        } else if (item.arg.equals(arg)) {
                            continue loop;
                        }
                    }
                    raw.add(new Item(arg, parse));
                }
                continue;
            }

            if (arg.startsWith("-XX:")) {
                Matcher matcher = UNSTABLE_OPTION_PATTERN.matcher(arg);
                if (matcher.matches()) {
                    addUnstableDefault(matcher.group("key"), matcher.group("value"), parse);
                    continue;
                }

                matcher = UNSTABLE_BOOLEAN_OPTION_PATTERN.matcher(arg);
                if (matcher.matches()) {
                    addUnstableDefault(matcher.group("key"), "+".equals(matcher.group("value")), parse);
                    continue;
                }
            }

            if (arg.startsWith("-X")) {
                String opt = null;
                String value = null;

                for (String prefix : new String[]{"-Xmx", "-Xms", "-Xmn", "-Xss"}) {
                    if (arg.startsWith(prefix)) {
                        opt = prefix;
                        value = arg.substring(prefix.length());
                        break;
                    }
                }

                if (opt != null) {
                    addDefault(opt, value, parse);
                    continue;
                }
            }

            for (Item item : raw) {
                if (item.arg.equals(arg)) {
                    continue loop;
                }
            }
            raw.add(new Item(arg, parse));
        }
    }

    public String addDefault(String opt, String value) {
        return addDefault(opt, value, true);
    }

    private String addDefault(String opt, String value, boolean parse) {
        for (Item item : raw) {
            if (item.arg.startsWith(opt)) {
                LOG.info("Default option '" + opt + value + "' is suppressed by '" + item.arg + "'");
                return item.arg;
            }
        }
        raw.add(new Item(opt + value, parse));
        return null;
    }

    public String addUnstableDefault(String opt, boolean value) {
        return addUnstableDefault(opt, value, true);
    }

    private String addUnstableDefault(String opt, boolean value, boolean parse) {
        for (Item item : raw) {
            final Matcher matcher = UNSTABLE_BOOLEAN_OPTION_PATTERN.matcher(item.arg);
            if (matcher.matches()) {
                if (matcher.group("key").equals(opt)) {
                    return item.arg;
                }
            }
        }

        if (value) {
            raw.add(new Item("-XX:+" + opt, parse));
        } else {
            raw.add(new Item("-XX:-" + opt, parse));
        }
        return null;
    }

    public String addUnstableDefault(String opt, String value) {
        return addUnstableDefault(opt, value, true);
    }

    private String addUnstableDefault(String opt, String value, boolean parse) {
        for (Item item : raw) {
            final Matcher matcher = UNSTABLE_OPTION_PATTERN.matcher(item.arg);
            if (matcher.matches()) {
                if (matcher.group("key").equals(opt)) {
                    return item.arg;
                }
            }
        }

        raw.add(new Item("-XX:" + opt + "=" + value, parse));
        return null;
    }

    public boolean removeIf(Predicate<String> pred) {
        return raw.removeIf(i -> pred.test(i.arg));
    }

    public boolean noneMatch(Predicate<String> predicate) {
        return raw.stream().noneMatch(it -> predicate.test(it.arg));
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
        final String arg;
        final boolean parse;

        Item(String arg, boolean parse) {
            this.arg = arg;
            this.parse = parse;
        }

        @Override
        public String toString() {
            return parse ? (OperatingSystem.WINDOWS == OperatingSystem.CURRENT_OS ? toBatchStringLiteral(arg) : toShellStringLiteral(arg)) : arg;
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
            final Process process = Runtime.getRuntime().exec(new String[]{"powershell", "-Command", "Get-ExecutionPolicy"});
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroy();
                return false;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), OperatingSystem.NATIVE_CHARSET))) {
                String policy = reader.readLine();
                return "Unrestricted".equalsIgnoreCase(policy) || "RemoteSigned".equalsIgnoreCase(policy);
            }
        } catch (Throwable ignored) {
        }
        return false;
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

    private static boolean containsEscape(String str, String escapeChars) {
        for (int i = 0; i < escapeChars.length(); i++) {
            if (str.indexOf(escapeChars.charAt(i)) >= 0)
                return true;
        }
        return false;
    }

    private static String escape(String str, char... escapeChars) {
        for (char ch : escapeChars) {
            str = str.replace("" + ch, "\\" + ch);
        }
        return str;
    }

    public static String toBatchStringLiteral(String s) {
        return containsEscape(s, " \t\"^&<>|") ? '"' + escape(s, '\\', '"') : s;
    }

    public static String toShellStringLiteral(String s) {
        return containsEscape(s, " \t\"!#$&'()*,;<=>?[\\]^`{|}~") ? '"' + escape(s, '"', '$', '&', '`') + '"' : s;
    }
}
