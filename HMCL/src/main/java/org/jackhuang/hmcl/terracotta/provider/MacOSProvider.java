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
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.tree.TarFileTree;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class MacOSProvider implements ITerracottaProvider {
    public final TerracottaNative installer, binary;

    public MacOSProvider(TerracottaNative installer, TerracottaNative binary) {
        this.installer = installer;
        this.binary = binary;
    }

    @Override
    public Status status() throws IOException {
        assert binary != null;

        if (!Files.exists(Path.of("/Applications/terracotta.app"))) {
            return Status.NOT_EXIST;
        }

        return binary.status();
    }

    @Override
    public Task<?> install(Context context, @Nullable TarFileTree tree) throws IOException {
        assert installer != null && binary != null;

        Task<?> installerTask = installer.install(context, tree);
        Task<?> binaryTask = binary.install(context, tree);
        context.bindProgress(installerTask.progressProperty().add(binaryTask.progressProperty()).multiply(0.4)); // (1 + 1) * 0.4 = 0.8

        return Task.allOf(
                installerTask.thenComposeAsync(() -> {
                    ManagedProcess process = new ManagedProcess(new ProcessBuilder(
                            "osascript",
                            "-e",
                            String.format(
                                    "do shell script \"installer -pkg %s -target /Applications\" with prompt \"%s\" with administrator privileges",
                                    installer.getPath(),
                                    i18n("terracotta.sudo_installing")
                            )
                    ));
                    process.pumpInputStream(SystemUtils::onLogLine);
                    process.pumpErrorStream(SystemUtils::onLogLine);

                    return Task.fromCompletableFuture(process.getProcess().onExit());
                }),
                binaryTask.thenRunAsync(() -> Files.setPosixFilePermissions(binary.getPath(), Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ,
                        PosixFilePermission.OTHERS_EXECUTE
                )))
        );
    }

    @Override
    public List<String> ofCommandLine(Path path) {
        assert binary != null;

        return List.of(binary.getPath().toString(), "--hmcl", path.toString());
    }
}
