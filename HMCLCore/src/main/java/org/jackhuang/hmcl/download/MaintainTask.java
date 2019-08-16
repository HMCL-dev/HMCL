/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.Task;
import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;

public class MaintainTask extends Task<Version> {

    private final Version version;

    public MaintainTask(Version version) {
        this.version = version;
    }

    @Override
    public void execute() {
        setResult(maintain(version));
    }

    public static Version maintain(Version version) {
        if (version.getMainClass().contains("launchwrapper")) {
            return maintainGameWithLaunchWrapper(version);
        } else {
            // Vanilla Minecraft does not need maintain
            // Forge 1.13 support not implemented, not compatible with OptiFine currently.
            // Fabric does not need maintain, nothing compatible with fabric now.
            return version;
        }
    }

    private static Version maintainGameWithLaunchWrapper(Version version) {
        LibraryAnalyzer libraryAnalyzer = LibraryAnalyzer.analyze(version);
        VersionLibraryBuilder builder = new VersionLibraryBuilder(version);

        if (!libraryAnalyzer.has(FORGE)) {
            builder.removeTweakClass("forge");
        }

        // Installing Forge will override the Minecraft arguments in json, so LiteLoader and OptiFine Tweaker are being re-added.

        builder.removeTweakClass("liteloader");
        if (libraryAnalyzer.has(LITELOADER)) {
            builder.addArgument("--tweakClass", "com.mumfrey.liteloader.launch.LiteLoaderTweaker");
        }

        builder.removeTweakClass("optifine");
        if (libraryAnalyzer.has(OPTIFINE)) {
            if (!libraryAnalyzer.has(LITELOADER) && !libraryAnalyzer.has(FORGE)) {
                builder.addArgument("--tweakClass", "optifine.OptiFineTweaker");
            } else {
                // If forge or LiteLoader installed, OptiFine Forge Tweaker is needed.
                builder.addArgument("--tweakClass", "optifine.OptiFineForgeTweaker");
            }
        }

        return builder.build();
    }
}
