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
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// The in-memory result of a supervised game process, retained when the monitor JVM continues
/// into the launcher UI to present the game crash window.
///
/// @param process         The supervised game process, which has already exited.
/// @param exitType        The reported exit type, including `INTERRUPTED` for a recognized cancellation.
/// @param gameDirectoryId The persistent id of the registered game directory owning the launched
///                        instance, or `null` when unknown.
/// @param commands        The command line of the game process, or `null` when unknown.
/// @param launchOptions   The launch options used to identify the instance and present its crash window.
@NotNullByDefault
public record ResultSpec(ManagedProcess process, ProcessListener.ExitType exitType,
                         @Nullable String gameDirectoryId, @Nullable List<String> commands, LaunchOptions launchOptions) {
    /// Combines the launch specification with the supervised process and its reported exit result.
    public ResultSpec(ProcessSpec spec, ManagedProcess process, ProcessListener.ExitType exitType) {
        this(process, exitType, spec.gameDirectoryId, spec.command, spec.launchOptions);
    }
}
