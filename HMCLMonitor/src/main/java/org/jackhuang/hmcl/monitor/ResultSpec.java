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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// The result of a supervised game process, written by the monitor and consumed by a relaunched
/// launcher process through the `--crash-report` argument.
@NotNullByDefault
public final class ResultSpec {
    /// The process id of the game process; `-1` when unknown.
    long pid;
    /// The epoch millisecond at which the game process started.
    long processStartTime;
    /// The exit code of the game process.
    @Nullable Integer exitCode;
    /// The name of the [ProcessListener.ExitType] of the game process.
    @Nullable String exitType;
    /// The session log file collecting the game output, or `null` when unavailable.
    @Nullable String logFile;
    /// The id of the launched instance, or `null` when unknown.
    @Nullable String instanceId;
    /// The display name of the launched version, or `null` when unknown.
    @Nullable String versionName;
    /// The game directory of the launched instance, or `null` when unknown.
    @Nullable String gameDir;
    /// The maximum memory of the game process in MiB, or `null` when unset.
    @Nullable Integer maxMemory;
    /// The binary path of the Java runtime the game runs on, or `null` when unknown.
    @Nullable String javaBinary;
    /// The version string of the Java runtime the game runs on, or `null` when unknown.
    @Nullable String javaVersion;
    /// The architecture name of the Java runtime the game runs on, or `null` when unknown.
    @Nullable String javaArchitecture;
    /// The command line of the game process, or `null` when unknown.
    @Nullable List<String> commands;
}
