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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.download.DefaultGameBuilder;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Builds game instances and applies HMCL-specific post-installation settings.
@NotNullByDefault
public class HMCLGameBuilder extends DefaultGameBuilder {
    /// Whether the current build must enable instance-local running-directory settings.
    private boolean enableIsolation;

    /// Creates a builder bound to the given dependency manager.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    public HMCLGameBuilder(HMCLDependencyManager dependencyManager) {
        super(dependencyManager);
    }

    /// {@inheritDoc}
    @Override
    public HMCLDependencyManager getDependencyManager() {
        return (HMCLDependencyManager) super.getDependencyManager();
    }

    /// {@inheritDoc}
    ///
    /// For an unregistered instance, applies the repository's default isolation policy to the
    /// configured component set. When isolation is selected, component-provided run-directory
    /// files are installed under the instance root and the corresponding instance setting is
    /// enabled after publication. Existing instances retain their current run-directory settings.
    @Override
    public Task<?> buildAsync() {
        GameInstanceID instanceId = Objects.requireNonNull(id, "GameBuilder.id must be set");
        HMCLGameRepository repository = getDependencyManager().getGameRepository();
        enableIsolation = !repository.hasInstance(instanceId)
                && repository.shouldIsolateNewInstance(
                        components.keySet().stream().anyMatch(GameComponentType::isModLoader));
        if (enableIsolation) {
            useInstanceRunDirectory();
        }

        return super.buildAsync();
    }

    /// {@inheritDoc}
    @Override
    protected void onInstanceCommitted(DefaultGameInstance instance) {
        if (enableIsolation) {
            ((HMCLGameInstance) instance).enableIsolation();
        }
    }
}
