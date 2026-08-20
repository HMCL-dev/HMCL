/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.game.GameInstance;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.platform.ManagedProcess;

import java.io.IOException;
import java.nio.file.Path;

/// Builds a process or script that launches a game instance.
///
/// The [GameInstance] identifies the instance being launched (paths, repository layout, version
/// cache). [#manifest] is the effective launch-time manifest after maintenance and native
/// patching; it must not be assumed equal to [GameInstance#getManifest()] or
/// [GameInstance#getLaunchManifest()].
public abstract class Launcher {

    /// The instance being launched.
    protected final GameInstance instance;

    /// The effective launch manifest for this launch attempt.
    protected final GameInstanceManifest manifest;

    /// Authentication information passed to the game process.
    protected final AuthInfo authInfo;

    /// JVM, game, and process launch options.
    protected final LaunchOptions options;

    /// Optional process output listener, or `null` when output is inherited.
    protected final ProcessListener listener;

    /// Whether process monitors should run as daemon threads.
    protected final boolean daemon;

    /// Creates a launcher for the given instance and launch plan.
    ///
    /// @param instance  the instance being launched
    /// @param manifest  the effective launch-time manifest (may differ from the instance storage)
    /// @param authInfo  authentication information for the game process
    /// @param options   launch options
    /// @param listener  process listener, or `null` to inherit IO
    /// @param daemon    whether monitors should be daemon threads
    public Launcher(GameInstance instance, GameInstanceManifest manifest, AuthInfo authInfo, LaunchOptions options, ProcessListener listener, boolean daemon) {
        this.instance = instance;
        this.manifest = manifest;
        this.authInfo = authInfo;
        this.options = options;
        this.listener = listener;
        this.daemon = daemon;
    }

    /// Returns the instance being launched.
    ///
    /// @return the bound [GameInstance]
    public GameInstance getInstance() {
        return instance;
    }

    /// Writes a launch script to the given path.
    ///
    /// @param file the script path
    /// @throws IOException if the script cannot be written
    public abstract void makeLaunchScript(Path file) throws IOException;

    /// Starts the game process.
    ///
    /// @return the managed process
    /// @throws IOException          if the process cannot be created or launch preparation fails
    /// @throws InterruptedException if interrupted while preparing or starting the process
    public abstract ManagedProcess launch() throws IOException, InterruptedException;

}
