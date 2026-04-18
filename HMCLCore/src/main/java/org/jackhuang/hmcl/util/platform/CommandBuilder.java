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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class CommandBuilder {
    private static final Pattern UNSTABLE_OPTION_PATTERN = Pattern.compile("-XX:(?<key>[a-zA-Z0-9]+)=(?<value>.*)");
    private static final Pattern UNSTABLE_BOOLEAN_OPTION_PATTERN = Pattern.compile("-XX:(?<value>[+\\-])(?<key>[a-zA-Z0-9]+)");

    private final OperatingSystem os;
    private final List<Item> raw = new ArrayList<>();

    /// Java 9+ supports passing JVM options stored in external files using the `@<options-file>` option.
    ///
    /// This list stores options loaded from those external option files when they are encountered,
    /// so they can be treated as existing arguments and not overwritten by methods like addDefault.
    private final List<String> external = new ArrayList<>();

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

    private Stream<String> allExistingArgs() {
        return Stream.concat(raw.stream().map(i -> i.arg), external.stream());
    }

    /**
     * Parsing will ignore your manual escaping
     *
     * @param arg command
     * @return this
     */
    public CommandBuilder add(String arg) {
        raw.add(new Item(arg, true));
        return this;
    }

    /**
     * Parsing will ignore your manual escaping
     *
     * @param args commands
     * @return this
     */
    public CommandBuilder addAll(String... args) {
        for (String s : args)
            raw.add(new Item(s, true));
        return this;
    }

    public CommandBuilder addAll(Collection<String> args) {
        for (String s : args)
            raw.add(new Item(s, true));
        return this;
    }

    public CommandBuilder addWithoutParsing(String arg) {
        raw.add(new Item(arg, false));
        return this;
    }

    public CommandBuilder addAllWithoutParsing(Collection<String> args) {
        for (String s : args)
            raw.add(new Item(s, false));
        return this;
    }

    public CommandBuilder addAllWithoutParsingAndReadExternal(Collection<String> args) {
        for (String s : args) {
            raw.add(new Item(s, false));

            if (s.startsWith("@")) {
                try {
                    Path file = Path.of(s.substring(1));
                    if (Files.isRegularFile(file)) {
                        try (Stream<String> lines = Files.lines(file)) {
                            external.addAll(parseArgumentFile(lines));
                        }
                    }
                } catch (Exception e) {
                    LOG.warning("Failed to read external JVM options file: " + s.substring(1), e);
                }
            }
        }
        return this;
    }

    public void addAllDefault(Collection<String> args) {
        addAllDefault(args, true);
    }

    public void addAllDefaultWithoutParsing(Collection<String> args) {
        addAllDefault(args, false);
    }

    private void addAllDefault(Collection<String> args, boolean parse) {
        for (String arg : args) {
            if (arg.startsWith("-D")) {
                int idx = arg.indexOf('=');
                if (idx >= 0) {
                    addDefault(arg.substring(0, idx + 1), arg.substring(idx + 1), parse);
                } else {
                    String opt = arg + "=";

                    Optional<String> first = allExistingArgs()
                            .filter(it -> it.startsWith(opt) || it.equals(arg))
                            .findFirst();
                    if (first.isPresent()) {
                        String overrideArg = first.get();
                        if (!overrideArg.equals(arg)) {
                            LOG.info("Default option '" + arg + "' is suppressed by '" + overrideArg + "'");
                        }
                        continue;
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

            if (allExistingArgs().noneMatch(arg::equals)) {
                raw.add(new Item(arg, parse));
            }
        }
    }

    public String addDefault(String opt, String value) {
        return addDefault(opt, value, true);
    }

    private String addDefault(String opt, String value, boolean parse) {
        Optional<String> first = allExistingArgs().filter(arg -> arg.startsWith(opt)).findFirst();
        if (first.isPresent()) {
            LOG.info("Default option '" + opt + value + "' is suppressed by '" + first.get() + "'");
            return first.get();
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

        for (String arg : external) {
            final Matcher matcher = UNSTABLE_BOOLEAN_OPTION_PATTERN.matcher(arg);
            if (matcher.matches()) {
                if (matcher.group("key").equals(opt)) {
                    return arg;
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

        for (String arg : external) {
            final Matcher matcher = UNSTABLE_OPTION_PATTERN.matcher(arg);
            if (matcher.matches()) {
                if (matcher.group("key").equals(opt)) {
                    return arg;
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
        if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS)
            return true;
        if (!OperatingSystem.isWindows7OrLater())
            return false;

        try {
            String policy = SystemUtils.run("powershell.exe", "-NoProfile", "-Command", "Get-ExecutionPolicy").trim();
            return "Unrestricted".equalsIgnoreCase(policy) || "RemoteSigned".equalsIgnoreCase(policy);
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean setExecutionPolicy() {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS)
            return true;
        if (!OperatingSystem.isWindows7OrLater())
            return false;

        try {
            SystemUtils.run("powershell.exe", "-NoProfile", "-Command", "Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
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
        return containsEscape(s, " \t\"^&<>|") ? '"' + escape(s, '\\', '"') + '"' : s;
    }

    /// Parses a Java command-line argument file content according to the specification:
    ///
    /// [java Command-Line Argument Files](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html#java-command-line-argument-files)
    static List<String> parseArgumentFile(Stream<String> lines) {
        List<String> result = new ArrayList<>();

        // State across lines: when a quoted string spans multiple lines
        StringBuilder pending = null;
        char pendingQuote = 0;

        for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
            String line = it.next();
            int len = line.length();
            int i = 0;

            if (pending != null) {
                // We're continuing a multi-line quoted argument
                pending.append('\n');
                while (i < len) {
                    char ch = line.charAt(i);
                    if (ch == pendingQuote) {
                        i++; // closing quote
                        pendingQuote = 0;
                        break;
                    } else if (ch == '\\' && i + 1 < len) {
                        pending.append(unescapeChar(line.charAt(++i)));
                        i++;
                    } else {
                        pending.append(ch);
                        i++;
                    }
                }
                if (pendingQuote != 0) {
                    // Still not closed, continue to next line
                    continue;
                }
                // Quote closed, fall through to continue parsing tokens on this line
            }

            while (i < len) {
                char ch = line.charAt(i);

                if (ch == ' ' || ch == '\t') {
                    // Whitespace ends the current token (if any) or is skipped
                    if (pending != null) {
                        result.add(pending.toString());
                        pending = null;
                    }
                    i++;
                    continue;
                }

                if (ch == '#' && pending == null) {
                    // Comment - skip rest of line
                    break;
                }

                if (pending == null) {
                    pending = new StringBuilder();
                }

                if (ch == '\'' || ch == '"') {
                    pendingQuote = ch;
                    i++; // skip opening quote
                    while (i < len) {
                        ch = line.charAt(i);
                        if (ch == pendingQuote) {
                            pendingQuote = 0;
                            i++;
                            break;
                        } else if (ch == '\\' && i + 1 < len) {
                            pending.append(unescapeChar(line.charAt(++i)));
                            i++;
                        } else {
                            pending.append(ch);
                            i++;
                        }
                    }
                    if (pendingQuote != 0) {
                        // Unclosed quote, continue on next line
                        break;
                    }
                } else if (ch == '\\' && i + 1 < len) {
                    pending.append(unescapeChar(line.charAt(++i)));
                    i++;
                } else {
                    pending.append(ch);
                    i++;
                }
            }

            // End of line: if not inside a quote, finish the token
            if (pending != null && pendingQuote == 0) {
                result.add(pending.toString());
                pending = null;
            }
        }

        // Handle unclosed quote at end of file
        if (pending != null) {
            result.add(pending.toString());
        }

        return result;
    }

    private static char unescapeChar(char ch) {
        return switch (ch) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case 'f' -> '\f';
            default -> ch;
        };
    }

    public static String toShellStringLiteral(String s) {
        return containsEscape(s, " \t\"!#$&'()*,;<=>?[\\]^`{|}~") ? '"' + escape(s, '"', '$', '&', '`') + '"' : s;
    }
}
