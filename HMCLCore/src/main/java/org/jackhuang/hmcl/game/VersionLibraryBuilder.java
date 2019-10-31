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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.CommandBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
public final class VersionLibraryBuilder {
    private final Version version;
    private final List<String> mcArgs;
    private final List<Argument> game;
    private final boolean useMcArgs;

    public VersionLibraryBuilder(Version version) {
        this.version = version;
        this.mcArgs = version.getMinecraftArguments().map(StringUtils::tokenize).map(ArrayList::new).orElse(null);
        this.game = version.getArguments().map(Arguments::getGame).map(ArrayList::new).orElseGet(ArrayList::new);
        this.useMcArgs = mcArgs != null;
    }

    public Version build() {
        Version ret = version;
        if (useMcArgs) {
            // Since $ will be escaped in linux, and our maintain of minecraftArgument will not cause escaping,
            // so we regenerate the minecraftArgument without escaping.
            ret = ret.setMinecraftArguments(new CommandBuilder().addAllWithoutParsing(mcArgs).toString());
        }
        return ret.setArguments(ret.getArguments().map(args -> args.withGame(game)).orElse(new Arguments(game, null)));
    }

    public void removeTweakClass(String target) {
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
        }

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

    public void addArgument(String... args) {
        for (String arg : args)
            game.add(new StringArgument(arg));
    }
}
