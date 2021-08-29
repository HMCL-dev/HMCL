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

import javafx.scene.paint.Color;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author huangyuhui
 */
public enum Log4jLevel {
    FATAL(1, Color.web("#F7A699")),
    ERROR(2, Color.web("#FFCCBB")),
    WARN(3, Color.web("#FFEECC")),
    INFO(4, Color.web("#FBFBFB")),
    DEBUG(5, Color.web("#EEE9E0")),
    TRACE(6, Color.BLUE),
    ALL(2147483647, Color.BLACK);

    private final int level;
    private final Color color;

    Log4jLevel(int level, Color color) {
        this.level = level;
        this.color = color;
    }

    public int getLevel() {
        return level;
    }

    public Color getColor() {
        return color;
    }

    public boolean lessOrEqual(Log4jLevel level) {
        return this.level <= level.level;
    }

    public static final Pattern MINECRAFT_LOGGER = Pattern.compile("\\[(?<timestamp>[0-9:]+)] \\[[^/]+/(?<level>[^]]+)]");
    public static final Pattern MINECRAFT_LOGGER_CATEGORY = Pattern.compile("\\[(?<timestamp>[0-9:]+)] \\[[^/]+/(?<level>[^]]+)] \\[(?<category>[^]]+)]");
    public static final String JAVA_SYMBOL = "([a-zA-Z_$][a-zA-Z\\d_$]*\\.)+[a-zA-Z_$][a-zA-Z\\d_$]*";

    public static Log4jLevel guessLevel(String line) {
        Log4jLevel level = null;
        Matcher m = MINECRAFT_LOGGER.matcher(line);
        if (m.find()) {
            // New style logs from log4j
            String levelStr = m.group("level");
            if (null != levelStr)
                switch (levelStr) {
                    case "INFO":
                        level = INFO;
                        break;
                    case "WARN":
                        level = WARN;
                        break;
                    case "ERROR":
                        level = ERROR;
                        break;
                    case "FATAL":
                        level = FATAL;
                        break;
                    case "TRACE":
                        level = TRACE;
                        break;
                    case "DEBUG":
                        level = DEBUG;
                        break;
                    default:
                        break;
                }
            Matcher m2 = MINECRAFT_LOGGER_CATEGORY.matcher(line);
            if (m2.find()) {
                String level2Str = m2.group("category");
                if (null != level2Str)
                    switch (level2Str) {
                        case "STDOUT":
                            level = INFO;
                            break;
                        case "STDERR":
                            level = ERROR;
                            break;
                    }
            }

            if (line.contains("STDERR]") || line.contains("[STDERR/]")) {
                level = ERROR;
            }
        } else {
            if (line.contains("[INFO]") || line.contains("[CONFIG]") || line.contains("[FINE]")
                    || line.contains("[FINER]") || line.contains("[FINEST]"))
                level = INFO;
            if (line.contains("[SEVERE]") || line.contains("[STDERR]"))
                level = ERROR;
            if (line.contains("[WARNING]"))
                level = WARN;
            if (line.contains("[DEBUG]"))
                level = DEBUG;
        }
        if (line.contains("overwriting existing"))
            level = FATAL;

        /*if (line.contains("Exception in thread")
                || line.matches("\\s+at " + JAVA_SYMBOL)
                || line.matches("Caused by: " + JAVA_SYMBOL)
                || line.matches("([a-zA-Z_$][a-zA-Z\\d_$]*\\.)+[a-zA-Z_$]?[a-zA-Z\\d_$]*(Exception|Error|Throwable)")
                || line.matches("... \\d+ more$"))
            return ERROR;*/
        return level;
    }

    public static boolean isError(Log4jLevel a) {
        return a != null && a.lessOrEqual(Log4jLevel.ERROR);
    }

    public static Log4jLevel mergeLevel(Log4jLevel a, Log4jLevel b) {
        if (a == null)
            return b;
        else if (b == null)
            return a;
        else
            return a.level < b.level ? a : b;
    }

    public static boolean guessLogLineError(String log) {
        return isError(guessLevel(log));
    }
}
