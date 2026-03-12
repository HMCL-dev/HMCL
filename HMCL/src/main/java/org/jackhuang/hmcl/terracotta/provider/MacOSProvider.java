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

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.TerracottaBundle;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MacOSProvider extends AbstractTerracottaProvider {
    private final Path executable, installer;

    public MacOSProvider(TerracottaBundle bundle, Path executable, Path installer) {
        super(bundle);
        this.executable = executable;
        this.installer = installer;
    }

    @Override
    public Status status() throws IOException {
        if (!Files.exists(Path.of("/Applications/terracotta.app"))) {
            return Status.NOT_EXIST;
        }

        return bundle.status();
    }

    @Override
    public Task<?> install(Path pkg) throws IOException {
        return super.install(pkg).thenComposeAsync(() -> {
            Path osascript = SystemUtils.which("osascript");
            if (osascript == null) {
                throw new IllegalStateException("Cannot locate 'osascript' system executable on MacOS for installing Terracotta.");
            }

            Path movedInstaller = Files.createTempDirectory(Metadata.HMCL_GLOBAL_DIRECTORY, "terracotta-pkg")
                    .toRealPath()
                    .resolve(FileUtils.getName(installer));
            Files.copy(installer, movedInstaller, StandardCopyOption.REPLACE_EXISTING);

            ManagedProcess process = new ManagedProcess(new ProcessBuilder(
                    osascript.toString(), "-e", String.format(
                    "do shell script \"installer -pkg '%s' -target /\" with prompt \"%s\" with administrator privileges",
                    movedInstaller, i18n("terracotta.sudo_installing")
            )));
            process.pumpInputStream(SystemUtils::onLogLine);
            process.pumpErrorStream(SystemUtils::onLogLine);

            return Task.fromCompletableFuture(process.getProcess().onExit()).thenRunAsync(() -> {
                try {
                    FileUtils.cleanDirectory(movedInstaller.getParent());
                } catch (IOException e) {
                    LOG.warning("Cannot remove temporary Terracotta package file.", e);
                }

                if (process.getExitCode() != 0) {
                    throw new IllegalStateException(String.format(
                            "Cannot install Terracotta %s: system installer exited with code %d", movedInstaller, process.getExitCode()
                    ));
                }
            });
        });
    }

    @Override
    public List<String> ofCommandLine(Path path) {
        return List.of(executable.toString(), "--hmcl", path.toString());
    }
}
