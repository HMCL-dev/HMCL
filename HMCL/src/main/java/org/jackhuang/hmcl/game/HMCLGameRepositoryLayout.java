// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
