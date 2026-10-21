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
///
/// @param command         The command line to execute, passed to [ProcessBuilder] verbatim.
/// @param directory       The absolute working directory used for the game process and the post-exit command.
/// @param environment     Environment variables overlaid on the monitor's environment for both commands.
/// @param encoding        The name of the charset used to decode the game process output.
/// @param inheritStdin    Whether the game process should inherit the monitor's standard input; set when no process
/// listener is present.
/// @param postExitCommand The post-exit command to run after the game exits, already tokenized, or `null`.
/// @param relaunchPolicy  The policy for opening the launcher UI in the monitor JVM after the protocol stream fails.
/// @param gameDirectoryId The persistent id of the registered game directory owning the launched instance, or `null`
/// when unknown.
/// @param launchOptions   The launch options retained for presenting the game crash window.
@NotNullByDefault
public record ProcessSpec(List<String> command, String directory, Map<String, String> environment, String encoding,
                          boolean inheritStdin, @Nullable List<String> postExitCommand,
                          MonitorLauncher.RelaunchPolicy relaunchPolicy, @Nullable String gameDirectoryId,
                          LaunchOptions launchOptions) {
    /// Creates a process specification from a launch context whose builder has a working directory.
    public ProcessSpec(MonitorLauncher.MonitorLaunchContext context) {
        this(new ArrayList<>(context.builder().command()),
                context.builder().directory().getAbsolutePath(),
                context.builder().environment(),
                context.encoding().name(),
                context.listener() == null,
                context.postExitCommand(),
                context.relaunchPolicy(),
                context.gameDirectoryId(),
                context.options());
    }
}
