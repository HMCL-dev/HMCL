// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Log4jLevel;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;

public final class Log {
    public static final int DEFAULT_LOG_LINES = 2000;

    public static int getLogLines() {
        Integer lines = settings().logLinesProperty().get();
        return lines != null && lines > 0 ? lines : DEFAULT_LOG_LINES;
    }

    private final String log;
    private Log4jLevel level;

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

    @Override
    public String toString() {
        return log;
    }
}
