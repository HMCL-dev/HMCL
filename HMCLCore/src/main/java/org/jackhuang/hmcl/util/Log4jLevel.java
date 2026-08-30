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
    private static final String WRAPPED_PRINT_STREAM = "[java.lang.Throwable$WrappedPrintStream:println";

    private static final String [] INFO_MARKERS = {
            "[INFO]", "[INFORMATION]", "[信息]", "[情報]",
            "[CONFIG]", "[KONFIGURATION]", "[설정]", "[配置]",
            "[FINE]", "[FEIN]", "[普通]", "[详细]",
            "[FINER]", "[FEINER]", "[詳細]", "[较详细]",
            "[FINEST]", "[AM FEINSTEN]", "[最も詳細]", "[非常详细]"
    };
    private static final String [] ERROR_MARKERS = {
            "[SEVERE]", "[SCHWERWIEGEND]", "[严重]", "[重大]"
    };
    private static final String [] WARN_MARKERS = {
            "[WARNING]", "[WARNUNG]", "[警告]"
    };

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
            if (containsAny(line, ERROR_MARKERS) || line.contains("[STDERR]")) {
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

    private static boolean containsAny(String line, String[] markers) {
        for (String marker : markers) {
            if (line.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isError(Log4jLevel level) {
        return level != null && level.lessOrEqual(Log4jLevel.ERROR);
    }

    public static boolean guessLogLineError(String log) {
        return isError(guessLevel(log));
    }
}
