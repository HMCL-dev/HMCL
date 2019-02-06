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
import org.jackhuang.hmcl.task.TaskResult;

public class MaintainTask extends TaskResult<Version> {

    private final Version version;
    private final String id;

    public MaintainTask(Version version) {
        this(version, ID);
    }

    public MaintainTask(Version version, String id) {
        this.version = version;
        this.id = id;
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
            // Forge 1.13.2 support not implemented.
            return version;
        }
    }

    private static Version maintainGameWithLaunchWrapper(Version version) {
        LibraryAnalyzer libraryAnalyzer = LibraryAnalyzer.analyze(version);
        VersionLibraryBuilder builder = new VersionLibraryBuilder(version);

        if (!libraryAnalyzer.hasForge()) {
            builder.removeTweakClass("forge");
        }

        // Installing Forge will override the Minecraft arguments in json, so LiteLoader and OptiFine Tweaker are being re-added.

        builder.removeTweakClass("liteloader");
        if (libraryAnalyzer.hasLiteLoader()) {
            builder.addArgument("--tweakClass", "com.mumfrey.liteloader.launch.LiteLoaderTweaker");
        }

        builder.removeTweakClass("optifine");
        if (libraryAnalyzer.hasOptiFine()) {
            if (!libraryAnalyzer.hasLiteLoader() && !libraryAnalyzer.hasForge()) {
                builder.addArgument("--tweakClass", "optifine.OptiFineTweaker");
            } else {
                // If forge or LiteLoader installed, OptiFine Forge Tweaker is needed.
                builder.addArgument("--tweakClass", "optifine.OptiFineForgeTweaker");
            }
        }

        return builder.build();
    }

    @Override
    public String getId() {
        return id;
    }

    public static final String ID = "version";
}
