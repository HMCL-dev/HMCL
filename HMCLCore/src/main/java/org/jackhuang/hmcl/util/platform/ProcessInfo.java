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
package org.jackhuang.hmcl.util.platform;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// The information about a game process that process listeners and UIs can rely on, regardless of
/// whether the process is owned by this JVM ([ManagedProcess]) or supervised by another process,
/// e.g. the HMCL monitor.
@NotNullByDefault
public interface ProcessInfo {

    /// The process id of the game process.
    long getPid();

    /// The handle of the game process, or `null` if it cannot be resolved anymore, e.g. a dead
    /// process whose id has already been reaped by the operating system.
    @Nullable ProcessHandle getHandle();

    /// The command line of the game process.
    List<String> getCommands();

    /// The classpath of the game process, or `null` when unknown.
    @Nullable String getClasspath();

    /// The epoch millisecond at which the game process started.
    long getProcessStartTime();

    /// Returns whether the game process is still running.
    boolean isRunning();

    /// Destroys the game process.
    ///
    /// <p>Implementations that monitor the process with extra threads must also interrupt them, so
    /// that a cancelled launch is not mistaken for a crashed game.
    void stop();
}
