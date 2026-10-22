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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// A view of the game process supervised by the HMCL monitor.
///
/// Unlike [org.jackhuang.hmcl.util.platform.ManagedProcess], the game process is not spawned nor
/// owned by this JVM. Its handle is resolved from the monitor's handshake and may be unavailable
/// if the game has already exited. The original command line is supplied by the launch context.
///
/// @param handle           the handle of the game process, or `null` if it cannot be resolved anymore
/// @param commands         the command line of the game process
/// @param processStartTime the epoch millisecond at which the game process started
/// @param specFile The file the spec was exchanged with the monitor through
@NotNullByDefault
public record MonitorGameProcess(@Nullable ProcessHandle handle, long pid, List<String> commands, long processStartTime,
                                 Path specFile) implements ProcessInfo {

    /// Creates a view of the monitor-supervised game process.
    public MonitorGameProcess(@Nullable ProcessHandle handle, List<String> commands, long processStartTime, Path specFile) {
        this(handle, handle != null ? handle.pid() : -1, List.copyOf(commands), processStartTime, specFile);
    }

    @Override
    public @Nullable String classpath() {
        return null;
    }

    @Override
    public boolean isRunning() {
        ProcessHandle handle = this.handle;
        return handle != null && handle.isAlive();
    }

    @Override
    public void stop() {
        try { // notify the monitor
            Files.writeString(specFile, Monitor.CANCELED);
        } catch (IOException e) {
            LOG.warning("Failed to rewrite the monitor spec file " + specFile + " to mark the cancellation", e);
        }
        if (handle != null)
            handle.destroy();
    }
}
