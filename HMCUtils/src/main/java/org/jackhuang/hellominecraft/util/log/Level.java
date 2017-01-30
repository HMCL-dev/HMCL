/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.util.log;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author huangyuhui
 */
public enum Level {

    FATAL(1, Color.red),
    ERROR(2, Color.red),
    WARN(3, Color.orange),
    INFO(4, Color.black),
    DEBUG(5, Color.blue),
    TRACE(6, Color.blue),
    ALL(2147483647, Color.black);

    public final int level;
    public final Color COLOR;

    private Level(int i, Color c) {
        level = i;
        COLOR = c;
    }

    public boolean lessOrEqual(Level level) {
        return this.level <= level.level;
    }

    public static final Pattern MINECRAFT_LOGGER = Pattern.compile("\\[(?<timestamp>[0-9:]+)\\] \\[[^/]+/(?<level>[^\\]]+)\\]");
    public static final String JAVA_SYMBOL = "([a-zA-Z_$][a-zA-Z\\d_$]*\\.)+[a-zA-Z_$][a-zA-Z\\d_$]*";

    public static Level guessLevel(String line, Level level) {
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
            return FATAL;

        if (line.contains("Exception in thread")
            || line.matches("\\s+at " + JAVA_SYMBOL)
            || line.matches("Caused by: " + JAVA_SYMBOL)
            || line.matches("([a-zA-Z_$][a-zA-Z\\d_$]*\\.)+[a-zA-Z_$]?[a-zA-Z\\d_$]*(Exception|Error|Throwable)")
            || line.matches("... \\d+ more$"))
            return ERROR;
        return level;
    }

}
