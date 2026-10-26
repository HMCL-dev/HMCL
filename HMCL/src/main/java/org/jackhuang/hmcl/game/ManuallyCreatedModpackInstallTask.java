// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.Unzipper;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ManuallyCreatedModpackInstallTask extends Task<Path> {

    private final Path zipFile;
    private final String name;

    public ManuallyCreatedModpackInstallTask(Path zipFile, String name) {
        this.zipFile = zipFile;
        this.name = name;

        setName(i18n("modpack.installing"));
    }

    @Override
    public void execute() throws Exception {
        String subdirectory = ModpackHelper.findMinecraftDirectoryInManuallyCreatedModpack(zipFile.toString(), zipFile);

        Path dest = Paths.get("externalgames").resolve(name);

        setResult(dest);

        new Unzipper(zipFile, dest)
                .setSubDirectory(subdirectory)
                .setTerminateIfSubDirectoryNotExists()
                .unzip();
    }
}
