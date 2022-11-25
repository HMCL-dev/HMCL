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
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.util.platform.ManagedProcess;

/**
 *
 * @author huangyuhui
 */
public interface ProcessListener {

    /**
     * When a game launched, this method will be called to get the new process.
     * You should not override this method when your ProcessListener is shared with all processes.
     */
    default void setProcess(ManagedProcess process) {
    }

    /**
     * Called when receiving a log from stdout/stderr.
     *
     * Does not guarantee that this method is thread safe.
     *
     * @param log the log
     */
    void onLog(String log, boolean isErrorStream);

    /**
     * Called when the game process stops.
     *
     * @param exitCode the exit code
     */
    void onExit(int exitCode, ExitType exitType);

    enum ExitType {
        JVM_ERROR,
        APPLICATION_ERROR,
        NORMAL,
        INTERRUPTED
    }
}
