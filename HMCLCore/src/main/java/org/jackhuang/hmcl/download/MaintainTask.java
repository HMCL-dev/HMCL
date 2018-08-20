/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.CommandBuilder;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

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
        Library forge = null, liteLoader = null, optiFine = null;
        List<String> args = new ArrayList<>(StringUtils.tokenize(version.getMinecraftArguments().orElse("")));

        for (Library library : version.getLibraries()) {
            if (library.getGroupId().equalsIgnoreCase("net.minecraftforge") && library.getArtifactId().equalsIgnoreCase("forge"))
                forge = library;

            if (library.getGroupId().equalsIgnoreCase("com.mumfrey") && library.getArtifactId().equalsIgnoreCase("liteloader"))
                liteLoader = library;

            if (library.getGroupId().equalsIgnoreCase("net.optifine") && library.getArtifactId().equalsIgnoreCase("optifine"))
                optiFine = library;

        }

        if (forge == null) {
            removeTweakClass(args, "forge");
        }

        // Installing Forge will override the Minecraft arguments in json, so LiteLoader and OptiFine Tweaker are being re-added.

        if (liteLoader == null) {
            removeTweakClass(args, "liteloader");
        } else {
            if (!StringUtils.containsOne(args, "LiteLoaderTweaker")) {
                args.add("--tweakClass");
                args.add("com.mumfrey.liteloader.launch.LiteLoaderTweaker");
            }
        }

        if (optiFine == null) {
            removeTweakClass(args, "optifine");
        }

        if (liteLoader == null && forge == null && optiFine != null) {
            if (!StringUtils.containsOne(args, "optifine.OptiFineTweaker")) {
                args.add("--tweakClass");
                args.add("optifine.OptiFineTweaker");
            }
        }

        if ((liteLoader != null || forge != null) && optiFine != null) {
            // If forge or LiteLoader installed, OptiFine Forge Tweaker is needed.
            removeTweakClass(args, "optifine");
            args.add("--tweakClass");
            args.add("optifine.OptiFineForgeTweaker");
        }

        // Since $ will be escaped in linux, and our maintain of minecraftArgument will not cause escaping,
        // so we regenerate the minecraftArgument without escaping.
        return version.setMinecraftArguments(new CommandBuilder().addAllWithoutParsing(args).toString());
    }

    @Override
    public String getId() {
        return id;
    }

    private static void removeTweakClass(List<String> args, String target) {
        for (int i = 0; i < args.size(); ++i) {
            if (args.get(i).toLowerCase().contains(target)) {
                if (i > 0 && args.get(i - 1).equals("--tweakClass")) {
                    args.remove(i - 1);
                    args.remove(i - 1);
                    i -= 2;
                }
            }
        }
    }

    public static final String ID = "version";
}
