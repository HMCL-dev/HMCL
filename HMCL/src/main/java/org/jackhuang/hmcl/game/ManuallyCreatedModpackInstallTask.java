/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jackhuang.hmcl.util.tree.ZipFileTree;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ManuallyCreatedModpackInstallTask extends Task<Path> {

    private final Profile profile;
    private final Path zipFile;
    private final Charset charset;
    private final String name;

    public ManuallyCreatedModpackInstallTask(Profile profile, Path zipFile, Charset charset, String name) {
        this.profile = profile;
        this.zipFile = zipFile;
        this.charset = charset;
        this.name = name;
    }

    @Override
    public void execute() throws Exception {
        String subdirectory;
        try (var tree = new ZipFileTree(CompressingUtils.openZipFile(zipFile, charset))) {
            subdirectory = ModpackHelper.findMinecraftDirectoryInManuallyCreatedModpack(zipFile, tree).getFullName();
        }

        Path dest = Paths.get("externalgames").resolve(name);

        setResult(dest);

        new Unzipper(zipFile, dest)
                .setSubDirectory(subdirectory)
                .setTerminateIfSubDirectoryNotExists()
                .setEncoding(charset)
                .unzip();
    }
}
