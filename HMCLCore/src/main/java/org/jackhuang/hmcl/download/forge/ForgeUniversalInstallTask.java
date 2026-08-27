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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Path;
import java.util.List;

public class ForgeUniversalInstallTask extends Task<GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final Path universal;
    private final String selfVersion;

    ForgeUniversalInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, String selfVersion, Path universal) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.universal = universal;
        this.selfVersion = selfVersion;

        setSignificance(TaskSignificance.MAJOR);
    }

    @Override
    public void execute() throws Exception {
        var lib = new Library(
                new Artifact(
                        "net.minecraftforge",
                        "minecraftforge",
                        selfVersion,
                        null,
                        FileUtils.getExtension(universal)
                ),
                null,
                null,
                List.of(DigestUtils.digestToString("SHA-1", universal)),
                null,
                null,
                null,
                null,
                null
        );
        Path target = dependencyManager.getGameRepository().getLayout().getLibraryFile(manifest.id(), lib);
        FileUtils.copyFile(universal, target);

        setResult(GameInstancePatch.fromLibraries(
                List.of(lib),
                GameComponentType.FORGE.getPatchId(),
                selfVersion,
                GameInstancePatch.PRIORITY_LOADER
        ));
    }
}
