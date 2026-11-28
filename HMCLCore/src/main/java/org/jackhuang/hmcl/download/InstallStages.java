// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.TaskStage;
import org.jetbrains.annotations.NotNullByDefault;

/// Identifies the stages shared by game and modpack installation tasks.
@NotNullByDefault
public final class InstallStages {
    /// Verification and download of game assets.
    public static final TaskStage ASSETS = new TaskStage("hmcl.install.assets");

    /// Verification and download of game libraries.
    public static final TaskStage LIBRARIES = new TaskStage("hmcl.install.libraries");

    /// Installation of a modpack.
    public static final TaskStage MODPACK = new TaskStage("hmcl.modpack");

    /// Download of files required by a modpack.
    public static final TaskStage MODPACK_DOWNLOAD = new TaskStage("hmcl.modpack.download");

    /// Identifies installation of the given component version.
    public static TaskStage component(GameComponentType type, String version) {
        return new TaskStage("hmcl.install." + type.getPatchId(), version);
    }

    /// Prevents instantiation.
    private InstallStages() {
    }

}
