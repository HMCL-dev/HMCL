// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstance;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

/// Removes obsolete signature files from a legacy Forge instance's fixed client jar.
@NotNullByDefault
public final class GameVerificationFixTask extends Task<Void> {

    /// The snapshot-bound instance whose client jar may be modified.
    private final GameInstance instance;

    /// The detected Minecraft version.
    private final GameVersionNumber gameVersion;

    /// The effective launch manifest used to detect Forge.
    private final GameInstanceManifest manifest;

    /// Creates a task for a fixed instance and effective launch manifest.
    ///
    /// @param instance    the instance whose client jar may be modified
    /// @param gameVersion the detected Minecraft version
    /// @param manifest    the effective launch manifest used to detect Forge
    public GameVerificationFixTask(GameInstance instance, GameVersionNumber gameVersion, GameInstanceManifest manifest) {
        this.instance = instance;
        this.gameVersion = gameVersion;
        this.manifest = manifest;

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Removes legacy Mojang signature entries when this is a pre-1.6 Forge installation.
    ///
    /// @throws IOException if the client jar cannot be opened or modified
    @Override
    public void execute() throws IOException {
        Path jar = instance.getInstanceJarFile();

        if (Files.exists(jar) && gameVersion.compareTo("1.6") < 0 && instance.hasComponent(GameComponentType.FORGE)) {
            try (FileSystem fs = CompressingUtils.createWritableZipFileSystem(jar, StandardCharsets.UTF_8)) {
                Files.deleteIfExists(fs.getPath("META-INF/MOJANG_C.DSA"));
                Files.deleteIfExists(fs.getPath("META-INF/MOJANG_C.SF"));
            }
        }
    }
}
