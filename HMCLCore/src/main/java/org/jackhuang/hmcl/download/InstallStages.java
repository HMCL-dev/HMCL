// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.StageId;
import org.jetbrains.annotations.NotNullByDefault;

/// Identifies the stages shared by game and modpack installation tasks.
@NotNullByDefault
public final class InstallStages {
    /// Verification and download of game assets.
    public static final StageId ASSETS = new StageId("hmcl.install.assets");

    /// Verification and download of game libraries.
    public static final StageId LIBRARIES = new StageId("hmcl.install.libraries");

    /// Installation of a modpack.
    public static final StageId MODPACK = new StageId("hmcl.modpack");

    /// Download of files required by a modpack.
    public static final StageId MODPACK_DOWNLOAD = new StageId("hmcl.modpack.download");

    /// Prevents instantiation.
    private InstallStages() {
    }

    /// Identifies installation of the given component version.
    public static StageId component(GameComponentType type, String version) {
        return new StageId("hmcl.install." + type.getPatchId(), version);
    }
}
