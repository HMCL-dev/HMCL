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
import org.jetbrains.annotations.Nullable;

import java.util.*;

/// The builder which provide a task to build Minecraft environment.
///
/// @author huangyuhui
public abstract class GameBuilder {

    protected @Nullable GameInstanceID id;
    protected final Map<GameComponentType, Object /* String | RemoteVersion */> components = new EnumMap<>(GameComponentType.class);

    /// The new game version name, for `.minecraft/<instanceId>`.
    ///
    /// @param id the name of new game version.
    public GameBuilder id(GameInstanceID id) {
        this.id = Objects.requireNonNull(id);
        return this;
    }

    @Contract("_, _ -> this")
    public GameBuilder component(GameComponentType componentType, String version) {
        components.put(componentType, version);
        return this;
    }

    @Contract("_ -> this")
    public GameBuilder component(RemoteVersion remoteVersion) {
        components.put(remoteVersion.getComponentType(), remoteVersion);
        return this;
    }

    /**
     * @return the task that can build the whole Minecraft environment
     */
    public abstract Task<?> buildAsync();
}
