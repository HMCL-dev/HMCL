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
package org.jackhuang.hmcl.monitor;

import org.jackhuang.hmcl.util.platform.ProcessInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// A view of the game process supervised by the HMCL monitor.
///
/// <p>Unlike [org.jackhuang.hmcl.util.platform.ManagedProcess], the game process is not spawned nor
/// owned by this JVM; only its id, command line and start time are known, learned from the monitor's
/// handshake. [stop] destroys the game process through its [ProcessHandle] and interrupts the
/// monitor protocol reader thread, so that a canceled launch is not mistaken for a crashed game.
///
/// <p>Instances also represent already-exited games when presenting the crash window of a relaunched
/// launcher; their [handle][#getHandle] may be unresolvable in that case.
@NotNullByDefault
public final class MonitorGameProcess implements ProcessInfo {

    private final @Nullable ProcessHandle handle;
    private final long pid;
    private final List<String> commands;
    private final long processStartTime;
    private volatile @Nullable Thread readerThread;

    /// Creates a view of the monitor-supervised game process.
    ///
    /// @param handle           the handle of the game process, or `null` if it cannot be resolved anymore
    /// @param commands         the command line of the game process
    /// @param processStartTime the epoch millisecond at which the game process started
    MonitorGameProcess(@Nullable ProcessHandle handle, List<String> commands, long processStartTime) {
        this.handle = handle;
        this.pid = handle != null ? handle.pid() : -1;
        this.commands = List.copyOf(commands);
        this.processStartTime = processStartTime;
    }

    /// Attaches the monitor protocol reader thread so that [stop] can interrupt it.
    ///
    /// @param readerThread the thread draining the monitor's protocol stream
    void attachReaderThread(Thread readerThread) {
        this.readerThread = readerThread;
    }

    @Override
    public long getPid() {
        return pid;
    }

    @Override
    public @Nullable ProcessHandle getHandle() {
        return handle;
    }

    @Override
    public List<String> getCommands() {
        return commands;
    }

    @Override
    public @Nullable String getClasspath() {
        return null;
    }

    @Override
    public long getProcessStartTime() {
        return processStartTime;
    }

    @Override
    public boolean isRunning() {
        ProcessHandle handle = this.handle;
        return handle != null && handle.isAlive();
    }

    @Override
    public void stop() {
        ProcessHandle handle = this.handle;
        if (handle != null)
            handle.destroy();
        Thread readerThread = this.readerThread;
        if (readerThread != null)
            readerThread.interrupt();
    }
}
