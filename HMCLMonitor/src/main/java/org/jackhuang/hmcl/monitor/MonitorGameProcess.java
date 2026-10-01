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

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.ProcessInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// A view of the game process supervised by the HMCL monitor.
///
/// <p>Unlike [org.jackhuang.hmcl.util.platform.ManagedProcess], the game process is not spawned nor
/// owned by this JVM; only its id, command line and start time are known, learned from the monitor's
/// handshake. [stop] rewrites the spec file to mark the game process as canceled before destroying
/// it through its [ProcessHandle], so the monitor reports the exit as interrupted rather than a
/// crash.
///
/// <p>Instances also represent already-exited games when presenting the crash window of a relaunched
/// launcher; their [handle][#getHandle] may be unresolvable and their spec file is unknown in that
/// case.
@NotNullByDefault
public final class MonitorGameProcess implements ProcessInfo {

    private final @Nullable ProcessHandle handle;
    private final long pid;
    private final List<String> commands;
    private final long processStartTime;
    /// The file the spec was exchanged with the monitor through, rewritten by [stop] to mark the
    /// cancellation, or `null` when the game process is a dead one presented by a relaunched launcher.
    private final @Nullable Path specFile;

    /// Creates a view of the monitor-supervised game process.
    ///
    /// @param handle           the handle of the game process, or `null` if it cannot be resolved anymore
    /// @param commands         the command line of the game process
    /// @param processStartTime the epoch millisecond at which the game process started
    /// @param specFile         the file the spec was exchanged with the monitor through, or `null` if unknown
    MonitorGameProcess(@Nullable ProcessHandle handle, List<String> commands, long processStartTime,
                       @Nullable Path specFile) {
        this.handle = handle;
        this.pid = handle != null ? handle.pid() : -1;
        this.commands = List.copyOf(commands);
        this.processStartTime = processStartTime;
        this.specFile = specFile;
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
        // The monitor lives in another JVM, so its exit classification cannot be interrupted the way
        // the direct launch path interrupts its ExitWaiter; the rewritten spec file is the signal
        // telling the monitor that this exit is a manual cancellation. It is written before the game
        // process is destroyed, so the file is guaranteed to be present when the monitor checks it.
        Path specFile = this.specFile;
        if (specFile != null)
            writeCancelSpec(specFile);
        ProcessHandle handle = this.handle;
        if (handle != null)
            handle.destroy();
    }

    /// Rewrites the consumed spec file to mark the game process as manually canceled. Best-effort:
    /// on a failure the monitor classifies the exit as it does for any other abnormal exit.
    private static void writeCancelSpec(Path specFile) {
        try {
            ProcessSpec spec = new ProcessSpec();
            spec.canceled = true;
            JsonUtils.writeToJsonFile(specFile, spec);
        } catch (IOException e) {
            LOG.warning("Failed to rewrite the monitor spec file " + specFile + " to mark the cancellation", e);
        }
    }
}
