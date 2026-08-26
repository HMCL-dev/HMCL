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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;

/// Builds game instances and applies HMCL-specific post-installation settings.
@NotNullByDefault
public class HMCLGameBuilder extends DefaultGameBuilder {
    /// Whether the current build must enable instance-local running-directory settings.
    private boolean isolationEnabled;

    /// Creates a builder bound to the given dependency manager.
    ///
    /// @param dependencyManager the dependency manager for the target repository
    public HMCLGameBuilder(HMCLDependencyManager dependencyManager) {
        super(dependencyManager);
    }

    /// Requests instance-local running-directory settings for the instance built by this builder.
    ///
    /// Component-provided run-directory files are installed under the instance root, and the
    /// corresponding instance setting is enabled after publication when it can be written safely.
    /// A read-only instance retains its existing setting.
    ///
    /// @return this builder
    @Contract("-> this")
    public HMCLGameBuilder enableIsolation() {
        useInstanceRunDirectory();
        isolationEnabled = true;
        return this;
    }

    /// {@inheritDoc}
    @Override
    protected void onInstanceCommitted(DefaultGameInstance instance) {
        if (isolationEnabled) {
            ((HMCLGameInstance) instance).enableIsolation();
        }
    }
}
