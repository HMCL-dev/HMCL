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
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// The builder which provide a task to build Minecraft environment.
///
/// @author huangyuhui
@NotNullByDefault
public abstract class GameBuilder {

    protected @Nullable GameInstanceID id;
    protected final EnumMap<GameComponentType, Object /* String | RemoteVersion */> components = new EnumMap<>(GameComponentType.class);

    /// Whether new-instance component installers write run-directory content under the instance root.
    protected boolean useInstanceRunDirectory;

    /// The new game instance id, for `.minecraft/<instanceId>`.
    ///
    /// @param id the instance id of new game instance.
    public GameBuilder id(GameInstanceID id) {
        this.id = Objects.requireNonNull(id);
        return this;
    }

    /// Uses the instance root as the run directory while installing a new instance.
    ///
    /// This affects files installed by components, such as loader-provided mods. It does not write
    /// instance settings; callers must persist the corresponding isolation setting after the
    /// instance is published.
    ///
    /// @return this builder
    @Contract("-> this")
    public GameBuilder useInstanceRunDirectory() {
        useInstanceRunDirectory = true;
        return this;
    }

    @Contract("_, _ -> this")
    public GameBuilder component(GameComponentType componentType, String version) {
        components.put(componentType, version);
        return this;
    }

    @Contract("_ -> this")
    public GameBuilder component(ComponentRemoteVersion remoteVersion) {
        components.put(remoteVersion.getComponentType(), remoteVersion);
        return this;
    }

    /**
     * @return the task that can build the whole Minecraft environment
     */
    public abstract Task<?> buildAsync();
}
