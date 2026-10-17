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

import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/// The result of a supervised game process, written by the monitor and consumed by a relaunched
/// launcher process through the `--crash-report` argument.
@NotNullByDefault
public final class ResultSpec {
    /// The process id of the game process; `-1` when unknown.
    long pid;
    /// The epoch millisecond at which the game process started.
    long processStartTime;
    /// The name of the [ProcessListener.ExitType] of the game process.
    @Nullable String exitType;
    /// The session log file collecting the game output, or `null` when unavailable.
    @Nullable String logFile;
    /// The persistent id of the registered game directory owning the launched instance, or `null`
    /// when unknown.
    @Nullable String gameDirectoryId;
    /// The command line of the game process, or `null` when unknown.
    @Nullable List<String> commands;

    @Nullable LaunchOptions launchOptions;

    ResultSpec(ProcessSpec spec, long pid, long processStartTime, ProcessListener.ExitType exitType, Path logFile) {
        this.pid = pid;
        this.processStartTime = processStartTime;
        this.exitType = exitType.name();
        this.logFile = logFile.toString();
        gameDirectoryId = spec.gameDirectoryId;
        commands = spec.command;
        launchOptions = spec.launchOptions;
    }
}
