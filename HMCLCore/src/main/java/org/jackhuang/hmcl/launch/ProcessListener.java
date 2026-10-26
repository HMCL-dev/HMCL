// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
        SIGKILL,
        NORMAL,
        INTERRUPTED
    }
}
