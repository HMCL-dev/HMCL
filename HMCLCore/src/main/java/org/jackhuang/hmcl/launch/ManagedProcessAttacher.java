/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.function.BiConsumer;

/// @see attachListener
@NotNullByDefault
public class ManagedProcessAttacher {
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
