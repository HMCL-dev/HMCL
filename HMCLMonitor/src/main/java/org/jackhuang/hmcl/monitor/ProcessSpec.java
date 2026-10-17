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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The specification of the game process to supervise, exchanged with the monitor process as JSON.
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
    /// The persistent id of the registered game directory owning the launched instance, or `null`
    /// when unknown.
    @Nullable String gameDirectoryId;

    @Nullable LaunchOptions launchOptions;

    public static ProcessSpec canceledSpec() {
        ProcessSpec spec = new ProcessSpec();
        spec.canceled = true;
        return spec;
    }

    public ProcessSpec() {}

    public ProcessSpec(MonitorClient.MonitorLaunchContext context) {
        ProcessBuilder builder = context.builder();

        command = new ArrayList<>(builder.command());
        if (builder.directory() != null)
            directory = builder.directory().getAbsolutePath();
        environment = new LinkedHashMap<>(builder.environment());

        encoding = context.encoding().name();
        inheritStdin = context.listener() == null;
        postExitCommand = context.postExitCommand();
        relaunchAlways = context.relaunchPolicy() == MonitorClient.RelaunchPolicy.ALWAYS;
        relaunchOnCrash = context.relaunchPolicy() == MonitorClient.RelaunchPolicy.ON_CRASH;
        gameDirectoryId = context.gameDirectoryId();

        launchOptions = context.options();
    }
}
