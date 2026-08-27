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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.EnumMap;

/// Configures the components used to install or update a game instance.
///
/// @author huangyuhui
@NotNullByDefault
public abstract class GameBuilder {

    /// Components to install, keyed by their manifest patch type.
    protected final EnumMap<GameComponentType, Object /* String | RemoteVersion */> components = new EnumMap<>(GameComponentType.class);

    /// Enables instance isolation for the built instance.
    ///
    /// Component-provided run-directory files, such as loader-provided mods, are installed under
    /// the instance root. The concrete builder must also ensure subsequent launches use that same
    /// directory.
    ///
    /// @return this builder
    @Contract("-> this")
    public abstract GameBuilder enableIsolation();

    /// Configures a component by its remote version id.
    ///
    /// Reconfiguring the same component type replaces its previous value.
    ///
    /// @param componentType the component type
    /// @param version       the remote version id
    /// @return this builder
    @Contract("_, _ -> this")
    public GameBuilder component(GameComponentType componentType, String version) {
        components.put(componentType, version);
        return this;
    }

    /// Configures a component using an already resolved remote version.
    ///
    /// Reconfiguring the same component type replaces its previous value.
    ///
    /// @param remoteVersion the remote component version
    /// @return this builder
    @Contract("_ -> this")
    public GameBuilder component(ComponentRemoteVersion remoteVersion) {
        components.put(remoteVersion.getComponentType(), remoteVersion);
        return this;
    }

    /// Creates the task that installs the configured components and publishes the target instance.
    ///
    /// @return the instance build task
    public abstract Task<?> buildAsync();
}
