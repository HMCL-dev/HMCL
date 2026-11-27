// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DefaultGameBuilder;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.DefaultGameRepositoryLayout;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Builds an isolated game instance and installs modpack files into its reserved directory.
/// The reservation is acquired when execution starts, before any file installation task runs.
/// Execution fails if another draft owns the repository, a new target cannot be reserved,
/// the repository layout has been replaced, or the update target is no longer the published instance.
@NotNullByDefault
public abstract class ModpackGameInstallTask extends Task<Void> {
    /// Dependency manager used to build the game and resolve modpack files.
    protected final DefaultDependencyManager dependencyManager;
    /// Repository containing the installation target.
    protected final DefaultGameRepository repository;
    /// Target instance id for both installations and updates.
    protected final GameInstanceID instanceId;
    /// Published instance required for an update, or `null` to reserve a new instance.
    protected final @Nullable DefaultGameInstance updateTarget;
    /// Component versions requested by the manifest, populated by the format-specific constructor.
    protected final Map<GameComponentType, String> components = new EnumMap<>(GameComponentType.class);
    /// Tasks to run alongside the game build after reservation. Populate during construction.
    protected final List<Task<?>> installTasks = new ArrayList<>();

    /// Layout whose paths the file installation tasks captured during construction.
    private final DefaultGameRepositoryLayout layout;

    /// The prerequisite that reserves the target and runs all installation tasks.
    private final Task<Void> installGame = new Task<Void>() {
        /// Reserves the directory before the executor starts any tasks that can write to it.
        @Override
        public void execute() {
            try (DefaultGameBuilder builder = updateTarget == null
                    ? dependencyManager.newGameBuilder(instanceId)
                    : dependencyManager.newGameBuilder(updateTarget)) {
                if (repository.getLayout() != layout)
                    throw new IllegalStateException("Game directory has changed: " + layout.getBaseDirectory());
                builder.enableIsolation();
                components.forEach(builder::component);
                installTasks.add(0, builder.buildAsync());
                reservedBuilder = builder;
            }
        }

        /// Returns the tasks to run together after reservation succeeds.
        @Override
        public @Unmodifiable List<Task<?>> getDependencies() {
            return List.copyOf(installTasks);
        }
    }.setSignificance(TaskSignificance.MODERATE);

    /// Builder that reserved the target and transferred its draft to the installation.
    private @Nullable DefaultGameBuilder reservedBuilder;

    /// Creates a task for a new instance or an update of the given published instance.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    /// @param instanceId the target id, which must match the update target's id when supplied
    /// @param updateTarget the instance to update, or `null` for a new installation
    /// @throws IllegalArgumentException if the update target belongs to another repository
    protected ModpackGameInstallTask(DefaultDependencyManager dependencyManager, GameInstanceID instanceId,
                                     @Nullable DefaultGameInstance updateTarget) {
        this.dependencyManager = dependencyManager;
        this.repository = dependencyManager.getGameRepository();
        this.layout = repository.getLayout();
        this.instanceId = instanceId;
        this.updateTarget = updateTarget;
        if (updateTarget != null)
            dependencyManager.validateGameInstance(updateTarget);

        // Cancellation can skip the build task after it takes ownership of the draft.
        // The enclosing installation still receives its completion event in that case.
        onDone().register(event -> {
            if (event.isFailed() && reservedBuilder != null)
                cleanupFailedInstallation(reservedBuilder, event.getTask().getException());
        });
    }

    /// Returns whether this installation acquired its target and prepared the tasks that may modify it.
    final boolean hasReservedInstance() {
        return reservedBuilder != null;
    }

    /// Returns whether a failed new installation should be removed after its draft is released.
    protected boolean shouldRemoveFailedInstance(@Nullable Exception failure) {
        return true;
    }

    /// Releases any remaining reservation and applies the format's policy for a failed new instance.
    private void cleanupFailedInstallation(DefaultGameBuilder builder, @Nullable Exception failure) {
        try {
            builder.abort();
        } catch (IOException e) {
            if (failure != null)
                failure.addSuppressed(e);
            LOG.warning("Failed to abort modpack installation draft for " + instanceId, e);
        }
        if (updateTarget == null && repository.getLayout() == layout && shouldRemoveFailedInstance(failure))
            repository.removeInstanceFromDisk(instanceId);
    }

    /// Returns the prerequisite that builds the game and installs files before format-specific completion.
    @Override
    public @Unmodifiable List<Task<?>> getDependents() {
        return List.of(installGame);
    }
}
