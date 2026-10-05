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
import java.util.Map;

/// The specification of the game process to supervise, exchanged with the monitor process as JSON.
///
/// ```json
/// {
///   "command": ["javaw", "-Xmx4G", "net.minecraft.client.main.Main"],
///   "directory": "D:\\Game\\.minecraft",
///   "environment": { "APPDATA": "C:\\Users\\user\\AppData\\Roaming" },
///   "encoding": "UTF-8",
///   "relaunchAlways": false,
///   "relaunchOnCrash": true
/// }
/// ```
@NotNullByDefault
final class ProcessSpec {
    /// The command line to execute, passed to [ProcessBuilder] verbatim.
    @Nullable List<String> command;
    /// The working directory of the process, or `null` to inherit the monitor's working directory.
    @Nullable String directory;
    /// Environment variables overlaid on the monitor's environment, or `null` to keep it unchanged.
    @Nullable Map<String, String> environment;
    /// The charset of the game process output; required.
    @Nullable String encoding;
    /// Whether the game process should inherit the monitor's standard input; set when no process
    /// listener is present.
    boolean inheritStdin;
    /// The post-exit command to run after the game exits, already tokenized, or `null`.
    @Nullable List<String> postExitCommand;
    /// Whether the main launcher manually canceled the game process; the monitor reports such an
    /// exit as interrupted.
    boolean canceled;
    /// Whether to relaunch the launcher after the game exits unconditionally.
    boolean relaunchAlways;
    /// Whether to relaunch the launcher after the game exits abnormally.
    boolean relaunchOnCrash;
    /// The id of the launched instance, or `null` when unknown.
    @Nullable String instanceId;
    /// The persistent id of the registered game directory owning the launched instance, or `null`
    /// when unknown.
    @Nullable String gameDirectoryId;
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
}
