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

import javafx.application.Platform;
import org.jackhuang.hmcl.countly.CrashReport;
import org.jackhuang.hmcl.ui.CrashWindow;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
public final class CrashReporter implements Thread.UncaughtExceptionHandler {
    private final boolean showCrashWindow;

    public CrashReporter(boolean showCrashWindow) {
        this.showCrashWindow = showCrashWindow;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        LOG.error("Uncaught exception in thread " + t.getName(), e);

        try {
            CrashReport report = new CrashReport(t, e, StringUtils.getStackTrace(e));
            if (!report.shouldBeReport())
                return;

            LOG.error(report.getDisplayText());
            Platform.runLater(() -> {
                if (showCrashWindow) {
                    var window = CrashWindow.getInstance();
                    window.addCrashReport(report);
                    window.show();
                }
            });
        } catch (Throwable handlingException) {
            LOG.error("Unable to handle uncaught exception", handlingException);
        }
    }
}
