// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstance;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.EnumMap;

/// Configures the components used to install or update a game instance.
///
/// A builder reserves its target at construction. Keep it in a try-with-resources block through
/// configuration and installation. Builders are single-use and are not thread-safe.
///
/// @author huangyuhui
@NotNullByDefault
public abstract class GameBuilder implements AutoCloseable {

    /// Components to install, keyed by their manifest patch type.
    protected final EnumMap<GameComponentType, Object /* String | RemoteVersion */> components = new EnumMap<>(GameComponentType.class);

    /// Enables instance isolation for the built instance.
    ///
    /// Component-provided run-directory files, such as loader-provided mods, are installed under
    /// the instance root. The concrete builder must also ensure subsequent launches use that same
    /// directory.
    ///
    /// @return this builder
    /// @throws IllegalStateException if this builder is closed or the build has started
    @Contract("-> this")
    public abstract GameBuilder enableIsolation();

    /// Configures a component by its remote version id.
    ///
    /// Reconfiguring the same component type replaces its previous value.
    ///
    /// @param componentType the component type
    /// @param version       the remote version id
    /// @return this builder
    /// @throws IllegalStateException if this builder is closed or the build has started
    @Contract("_, _ -> this")
    public GameBuilder component(GameComponentType componentType, String version) {
        checkOpen();
        components.put(componentType, version);
        return this;
    }

    /// Configures a component using an already resolved remote version.
    ///
    /// Reconfiguring the same component type replaces its previous value.
    ///
    /// @param remoteVersion the remote component version
    /// @return this builder
    /// @throws IllegalStateException if this builder is closed or the build has started
    @Contract("_ -> this")
    public GameBuilder component(ComponentRemoteVersion remoteVersion) {
        checkOpen();
        components.put(remoteVersion.getComponentType(), remoteVersion);
        return this;
    }

    /// Installs the configured components as children of the current task and publishes the instance.
    /// Releases the reservation on return, including when cancellation prevents a child from starting.
    ///
    /// @return the instance from the committed snapshot
    /// @throws Exception if installation, publication, or cleanup fails
    public abstract GameInstance build(TaskExecutionContext context) throws Exception;

    /// Releases the reservation if the build has not run.
    @Override
    public abstract void close();

    /// Ensures this builder still accepts configuration or task creation.
    ///
    /// @throws IllegalStateException if this builder is closed or the build has started
    protected abstract void checkOpen();
}
