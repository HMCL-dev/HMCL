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
package org.jackhuang.hmcl.util;

import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author huangyuhui
 */
public enum Log4jLevel {
    FATAL(1),
    ERROR(2),
    WARN(3),
    INFO(4),
    DEBUG(5),
    TRACE(6),
    ALL(2147483647);

    private final int level;

    Log4jLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean lessOrEqual(Log4jLevel level) {
        return this.level <= level.level;
    }

    public static final Pattern MINECRAFT_LOGGER = Pattern.compile("\\[(?<timestamp>[0-9:]+)] \\[[^/]+/(?<level>[^]]+)]");
    public static final Pattern MINECRAFT_LOGGER_CATEGORY = Pattern.compile("\\[(?<timestamp>[0-9:]+)] \\[[^/]+/(?<level>[^]]+)] \\[(?<category>[^]]+)]");
    public static final String JAVA_SYMBOL = "([a-zA-Z_$][a-zA-Z\\d_$]*\\.)+[a-zA-Z_$][a-zA-Z\\d_$]*";
    private static final String WRAPPED_PRINT_STREAM = "[java.lang.Throwable$WrappedPrintStream:println";

    private static final String[] INFO_MARKERS = markers(
            Level.INFO,
            Level.CONFIG,
            Level.FINE,
            Level.FINER,
            Level.FINEST
    );
    private static final String[] ERROR_MARKERS = markers(Level.SEVERE);
    private static final String[] WARN_MARKERS = markers(Level.WARNING);

    public static Log4jLevel guessLevel(String line) {
        Log4jLevel level = null;
        Matcher m = MINECRAFT_LOGGER.matcher(line);
        if (m.find()) {
            level = parseLevel(m.group("level"));
            Matcher m2 = MINECRAFT_LOGGER_CATEGORY.matcher(line);
            if (m2.find()) {
                String level2Str = m2.group("category");
                if (level2Str != null) {
                    level = switch (level2Str) {
                        case "STDOUT" -> INFO;
                        case "STDERR" -> guessStderrLevel(line, level);
                        default -> level;
                    };
                }
            } else if (line.contains("STDERR]") || line.contains("[STDERR/]")) {
                level = guessStderrLevel(line, level);
            }
        } else {
            if (containsAny(line, INFO_MARKERS)) {
                level = INFO;
            }
            if (containsAny(line, ERROR_MARKERS)) {
                level = ERROR;
            }
            if (containsAny(line, WARN_MARKERS)) {
                level = WARN;
            }
            if (line.contains("[DEBUG]")) {
                level = DEBUG;
            }
        }

        if (line.contains("overwriting existing")) {
            level = FATAL;
        }

        /*if (line.contains("Exception in thread")
                || line.matches("\\s+at " + JAVA_SYMBOL)
                || line.matches("Caused by: " + JAVA_SYMBOL)
                || line.matches("([a-zA-Z_$][a-zA-Z\\d_$]*\\.)+[a-zA-Z_$]?[a-zA-Z\\d_$]*(Exception|Error|Throwable)")
                || line.matches("... \\d+ more$"))
            return ERROR;*/
        return level;
    }

    public static Log4jLevel guessLevel(String line, boolean isErrorStream) {
        Log4jLevel level = guessLevel(line);
        return level != null || !isErrorStream ? level : ERROR;
    }

    private static Log4jLevel parseLevel(String level) {
        return switch (level) {
            case "FATAL" -> FATAL;
            case "ERROR" -> ERROR;
            case "WARN" -> WARN;
            case "INFO" -> INFO;
            case "DEBUG" -> DEBUG;
            case "TRACE" -> TRACE;
            case "ALL" -> ALL;
            default -> null;
        };
    }

    private static Log4jLevel guessStderrLevel(String line, Log4jLevel fallback) {
        if (line.contains(WRAPPED_PRINT_STREAM) && fallback != null) {
            return fallback;
        }
        return ERROR;
    }

    private static String[] markers(Level... levels) {
        String[] markers = new String[levels.length * 2];
        int i = 0;
        for (Level level : levels) {
            markers[i++] = '[' + level.getName() + ']';
            markers[i++] = '[' + level.getLocalizedName() + ']';
        }
        return markers;
    }

    private static boolean containsAny(String line, String[] markers) {
        for (String marker : markers) {
            if (line.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isError(Log4jLevel a) {
        return a != null && a.lessOrEqual(Log4jLevel.ERROR);
    }

    public static boolean guessLogLineError(String log) {
        return isError(guessLevel(log));
    }
}
