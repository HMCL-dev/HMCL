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

import org.jackhuang.hmcl.game.Argument;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.StringArgument;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.CommandBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        LibraryAnalyzer libraryAnalyzer = LibraryAnalyzer.analyze(version);
        List<String> mcArgs = version.getMinecraftArguments().map(StringUtils::tokenize).map(ArrayList::new).orElse(null);
        List<Argument> game = version.getArguments().map(Arguments::getGame).map(ArrayList::new).orElseGet(ArrayList::new);
        boolean useMcArgs = mcArgs != null;

        if (!libraryAnalyzer.hasForge()) {
            removeTweakClass(useMcArgs, mcArgs, game, "forge");
        }

        // Installing Forge will override the Minecraft arguments in json, so LiteLoader and OptiFine Tweaker are being re-added.

        removeTweakClass(useMcArgs, mcArgs, game, "liteloader");
        if (libraryAnalyzer.hasLiteLoader()) {
            addArgument(useMcArgs, mcArgs, game, "--tweakClass", "com.mumfrey.liteloader.launch.LiteLoaderTweaker");
        }

        removeTweakClass(useMcArgs, mcArgs, game, "optifine");
        if (libraryAnalyzer.hasOptiFine()) {
            if (!libraryAnalyzer.hasLiteLoader() && !libraryAnalyzer.hasForge()) {
                addArgument(useMcArgs, mcArgs, game, "--tweakClass", "optifine.OptiFineTweaker");
            } else {
                // If forge or LiteLoader installed, OptiFine Forge Tweaker is needed.
                addArgument(useMcArgs, mcArgs, game, "--tweakClass", "optifine.OptiFineForgeTweaker");
            }
        }

        Version result;
        if (useMcArgs) {
            // Since $ will be escaped in linux, and our maintain of minecraftArgument will not cause escaping,
            // so we regenerate the minecraftArgument without escaping.
            result = version.setMinecraftArguments(new CommandBuilder().addAllWithoutParsing(mcArgs).toString());
        } else {
            result = version.setArguments(version.getArguments().map(args -> args.withGame(game)).orElse(new Arguments(game, Collections.emptyList())));
        }
        return result;
    }

    @Override
    public String getId() {
        return id;
    }

    private static void removeTweakClass(boolean useMcArgs, List<String> mcArgs, List<Argument> game, String target) {
        if (useMcArgs) {
            for (int i = 0; i + 1 < mcArgs.size(); ++i) {
                String arg0Str = mcArgs.get(i);
                String arg1Str = mcArgs.get(i + 1);
                if (arg0Str.equals("--tweakClass") && arg1Str.toLowerCase().contains(target)) {
                    mcArgs.remove(i);
                    mcArgs.remove(i);
                    --i;
                }
            }
        } else {
            for (int i = 0; i + 1 < game.size(); ++i) {
                Argument arg0 = game.get(i);
                Argument arg1 = game.get(i + 1);
                if (arg0 instanceof StringArgument && arg1 instanceof StringArgument) {
                    // We need to preserve the tokens
                    String arg0Str = arg0.toString();
                    String arg1Str = arg1.toString();
                    if (arg0Str.equals("--tweakClass") && arg1Str.toLowerCase().contains(target)) {
                        game.remove(i);
                        game.remove(i);
                        --i;
                    }
                }
            }
        }
    }

    private static void addArgument(boolean useMcArgs, List<String> mcArgs, List<Argument> game, String... args) {
        if (useMcArgs) {
            mcArgs.addAll(Arrays.asList(args));
        } else {
            for (String arg : args)
                game.add(new StringArgument(arg));
        }
    }

    public static final String ID = "version";
}
