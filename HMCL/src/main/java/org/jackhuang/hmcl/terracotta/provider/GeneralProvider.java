/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.terracotta.provider;

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.TerracottaNative;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.tree.TarFileTree;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

public final class GeneralProvider implements ITerracottaProvider {
    private final TerracottaNative target;

    public GeneralProvider(TerracottaNative target) {
        this.target = target;
    }

    @Override
    public Status status() throws IOException {
        return target.status();
    }

    @Override
    public Task<?> install(Context context, @Nullable TarFileTree tree) throws IOException {
        Task<?> task = target.install(context, tree);
        context.bindProgress(task.progressProperty());
        if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
            task = task.thenRunAsync(() -> Files.setPosixFilePermissions(target.getPath(), Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE
            )));
        }
        return task;
    }

    @Override
    public List<String> ofCommandLine(Path path) {
        return List.of(target.getPath().toString(), "--hmcl", path.toString());
    }
}
