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
/// A builder reserves its target until installation finishes or the builder is closed.
/// Use it in a try-with-resources block. Configuration ends when the build starts or the
/// builder is closed. Builders are single-use and are not thread-safe.
///
/// @author huangyuhui
@NotNullByDefault
public abstract class GameBuilder implements AutoCloseable {

    /// Components to install, keyed by their manifest patch type.
    protected final EnumMap<GameComponentType, Object /* String | RemoteVersion */> components = new EnumMap<>(GameComponentType.class);

    /// Uses the instance root as the run directory during installation and subsequent launches.
    @Contract("-> this")
    public abstract GameBuilder enableIsolation();

    /// Selects a remote version id, replacing the previous selection for this component.
    @Contract("_, _ -> this")
    public GameBuilder component(GameComponentType componentType, String version) {
        checkOpen();
        components.put(componentType, version);
        return this;
    }

    /// Selects a resolved version, replacing the previous selection for its component.
    @Contract("_ -> this")
    public GameBuilder component(ComponentRemoteVersion remoteVersion) {
        checkOpen();
        components.put(remoteVersion.getComponentType(), remoteVersion);
        return this;
    }

    /// Installs the configured game and components in the current task and returns the published instance.
    /// Failure or cancellation releases the reservation.
    public abstract GameInstance build(TaskExecutionContext context) throws Exception;

    /// Releases the reservation if the build has not run.
    @Override
    public abstract void close();

    /// Throws [IllegalStateException] if this builder is closed or the build has started.
    protected abstract void checkOpen();
}
