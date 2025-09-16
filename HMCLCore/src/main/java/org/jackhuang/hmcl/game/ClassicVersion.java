/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

/// The Minecraft version for 1.5.x and earlier.
///
/// @author huangyuhui
public final class ClassicVersion extends Version {

    public ClassicVersion() {
        super(true, "Classic", null, null, "${auth_player_name} ${auth_session} --workDir ${game_directory}",
                null, "net.minecraft.client.Minecraft", null, null, null, null, null, null,
                Arrays.asList(new ClassicLibrary("lwjgl"), new ClassicLibrary("jinput"), new ClassicLibrary("lwjgl_util")),
                null, null, null, ReleaseType.UNKNOWN, Instant.now(), Instant.now(), 0, false, false, null);
    }

    private static final class ClassicLibrary extends Library {
        public ClassicLibrary(String name) {
            super(new Artifact("", "", ""), null,
                    new LibrariesDownloadInfo(new LibraryDownloadInfo("bin/" + name + ".jar"), null),
                    null, null, null, null, null, null);
        }
    }

    public static boolean hasClassicVersion(Path baseDirectory) {
        Path bin = baseDirectory.resolve("bin");
        return Files.isDirectory(bin)
                && Files.exists(bin.resolve("lwjgl.jar"))
                && Files.exists(bin.resolve("jinput.jar"))
                && Files.exists(bin.resolve("lwjgl_util.jar"));
    }
}
