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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The specification of the game process to supervise, exchanged with the monitor process as JSON.
@NotNullByDefault
public final class ProcessSpec {
    /// The command line to execute, passed to [ProcessBuilder] verbatim.
    public final List<String> command;
    /// The absolute working directory used for the game process and the post-exit command.
    public final String directory;
    /// Environment variables overlaid on the monitor's environment for both commands.
    public final Map<String, String> environment;
    /// The name of the charset used to decode the game process output.
    public final String encoding;
    /// Whether the game process should inherit the monitor's standard input; set when no process
    /// listener is present.
    public final boolean inheritStdin;
    /// The post-exit command to run after the game exits, already tokenized, or `null`.
    public final @Nullable List<String> postExitCommand;

    /// The policy for opening the launcher UI in the monitor JVM after the protocol stream fails.
    public final MonitorClient.RelaunchPolicy relaunchPolicy;
    /// The persistent id of the registered game directory owning the launched instance, or `null`
    /// when unknown.
    public final @Nullable String gameDirectoryId;

    /// The launch options retained for presenting the game crash window.
    public final LaunchOptions launchOptions;

    /// Creates a process specification from a launch context whose builder has a working directory.
    public ProcessSpec(MonitorClient.MonitorLaunchContext context) {
        ProcessBuilder builder = context.builder();

        command = new ArrayList<>(builder.command());
        directory = builder.directory().getAbsolutePath();
        environment = builder.environment();

        encoding = context.encoding().name();
        inheritStdin = context.listener() == null;
        postExitCommand = context.postExitCommand();
        relaunchPolicy = context.relaunchPolicy();
        gameDirectoryId = context.gameDirectoryId();

        launchOptions = context.options();
    }
}
