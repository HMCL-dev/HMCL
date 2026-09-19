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

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;

@NotNullByDefault
public final class HMCLGameRepositoryLayout extends DefaultGameRepositoryLayout {
    /// Directory under the instance root that stores HMCL-managed instance metadata.
    private static final String INSTANCE_METADATA_DIRECTORY = ".hmcl";

    /// Directory under the instance metadata directory that stores instance configuration files.
    private static final String INSTANCE_CONFIG_DIRECTORY = "config";

    /// Directory under the instance metadata directory that stores instance state files.
    private static final String INSTANCE_STATE_DIRECTORY = "state";

    /// Current file name for instance-specific game settings.
    private static final String INSTANCE_GAME_SETTINGS_FILENAME = "instance-game-settings.json";

    public HMCLGameRepositoryLayout(Path baseDirectory) {
        super(baseDirectory);
    }

    /// Returns the HMCL-managed metadata directory under the instance root.
    ///
    /// This directory stores instance-scoped files owned by HMCL.
    public Path getInstanceMetadataDirectory(GameInstanceID instanceId) {
        return getInstanceRoot(instanceId).resolve(INSTANCE_METADATA_DIRECTORY);
    }

    /// Returns the HMCL-managed configuration directory under the instance metadata directory.
    public Path getInstanceConfigDirectory(GameInstanceID instanceId) {
        return getInstanceMetadataDirectory(instanceId).resolve(INSTANCE_CONFIG_DIRECTORY);
    }

    /// Returns the HMCL-managed state directory under the instance metadata directory.
    public Path getInstanceStateDirectory(GameInstanceID instanceId) {
        return getInstanceMetadataDirectory(instanceId).resolve(INSTANCE_STATE_DIRECTORY);
    }

    /// Returns the current local game settings path under the instance configuration directory.
    public Path getInstanceGameSettingsFile(GameInstanceID instanceId) {
        return getInstanceConfigDirectory(instanceId).resolve(INSTANCE_GAME_SETTINGS_FILENAME);
    }
}
