// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.function.BiConsumer;

/// @see attachListener
@NotNullByDefault
public final class ManagedProcessAttacher {
    private ManagedProcessAttacher() {
    }

    /// attach listener to managed process
    ///
    /// @param processListener the listener attached to managed process
    /// @param exitCallback    the managed process exit callback. [ProcessListener#onExit(int, ProcessListener.ExitType)] will always be called.
    public static void attachListener(ManagedProcess managedProcess, ProcessListener processListener, Charset encoding,
                                      @Nullable BiConsumer<Integer, ProcessListener.ExitType> exitCallback, boolean isDaemon) {
        processListener.setProcess(managedProcess);
        Thread stdout = Lang.thread(new StreamPump(managedProcess.getProcess().getInputStream(), it -> {
            processListener.onLog(it, false);
            managedProcess.addLine(it);
        }, encoding), "stdout-pump", isDaemon);
        managedProcess.addRelatedThread(stdout);
        Thread stderr = Lang.thread(new StreamPump(managedProcess.getProcess().getErrorStream(), it -> {
            processListener.onLog(it, true);
            managedProcess.addLine(it);
        }, encoding), "stderr-pump", isDaemon);
        managedProcess.addRelatedThread(stderr);
        managedProcess.addRelatedThread(Lang.thread(new ExitWaiter(managedProcess, Arrays.asList(stdout, stderr), (exitCode, exitType) -> {
            processListener.onExit(exitCode, exitType);
            if (exitCallback != null)
                exitCallback.accept(exitCode, exitType);
        }), "exit-waiter", isDaemon));
    }
}
