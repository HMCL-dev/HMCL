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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Log4jLevel;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

public final class Log {
    public static final int DEFAULT_LOG_LINES = 2000;

    public static int getLogLines() {
        Integer lines = config().getLogLines();
        return lines != null && lines > 0 ? lines : DEFAULT_LOG_LINES;
    }

    private final String log;
    private Log4jLevel level;
    private boolean selected = false;

    public Log(String log) {
        this.log = log;
    }

    public Log(String log, Log4jLevel level) {
        this.log = log;
        this.level = level;
    }

    public String getLog() {
        return log;
    }

    public Log4jLevel getLevel() {
        Log4jLevel level = this.level;
        if (level == null) {
            level = Log4jLevel.guessLevel(log);
            if (level == null)
                level = Log4jLevel.INFO;
            this.level = level;
        }
        return level;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public String toString() {
        return log;
    }
}
