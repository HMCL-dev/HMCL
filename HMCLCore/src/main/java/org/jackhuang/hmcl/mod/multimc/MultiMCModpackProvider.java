/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.multimc;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.mod.MismatchedModpackTypeException;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackProvider;
import org.jackhuang.hmcl.mod.ModpackUpdateTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class MultiMCModpackProvider implements ModpackProvider {
    public static final MultiMCModpackProvider INSTANCE = new MultiMCModpackProvider();

    @Override
    public String getName() {
        return "MultiMC";
    }

    @Override
    public Task<?> createCompletionTask(DefaultDependencyManager dependencyManager, String version) {
        return null;
    }

    @Override
    public Task<?> createUpdateTask(DefaultDependencyManager dependencyManager, String name, File zipFile, Modpack modpack) throws MismatchedModpackTypeException {
        if (!(modpack.getManifest() instanceof MultiMCInstanceConfiguration))
            throw new MismatchedModpackTypeException(getName(), modpack.getManifest().getProvider().getName());

        return new ModpackUpdateTask(dependencyManager.getGameRepository(), name, new MultiMCModpackInstallTask(dependencyManager, zipFile, modpack, (MultiMCInstanceConfiguration) modpack.getManifest(), name));
    }

    private static boolean testPath(Path root) {
        return Files.exists(root.resolve("instance.cfg"));
    }

    public static Path getRootPath(Path root) throws IOException {
        if (testPath(root)) return root;
        try (Stream<Path> stream = Files.list(root)) {
            Path candidate = stream.filter(Files::isDirectory).findAny()
                    .orElseThrow(() -> new IOException("Not a valid MultiMC modpack"));
            if (testPath(candidate)) return candidate;
            throw new IOException("Not a valid MultiMC modpack");
        }
    }

    private static Path getRoot(FileSystem fileSystem) throws IOException {
        final String instanceFileName = "instance.cfg";

        Path root = fileSystem.getPath("/");
        if (Files.exists(fileSystem.getPath('/' + instanceFileName))) {
            return root;
        }

        try (Stream<Path> stream = Files.list(root)) {
            for (Path path : Lang.toIterable(stream)) {
                if (FileUtils.getName(path).equals(instanceFileName)) {
                    return path.getParent();
                }
            }
        }

        throw new IOException("Not a valid MultiMC modpack");
    }

    @Override
    public Modpack readManifest(FileSystem fileSystem, Path modpackPath, Charset encoding) throws IOException {
        Path root = getRoot(fileSystem);
        MultiMCManifest manifest = MultiMCManifest.readMultiMCModpackManifest(root);

        String name = root.getParent() == null ? FileUtils.getNameWithoutExtension(modpackPath) : FileUtils.getName(root);

        try (InputStream instanceStream = Files.newInputStream(root.resolve("instance.cfg"))) {
            MultiMCInstanceConfiguration cfg = new MultiMCInstanceConfiguration(name, instanceStream, manifest);
            return new Modpack(cfg.getName(), "", "", cfg.getGameVersion(), cfg.getNotes(), encoding, cfg) {
                @Override
                public Task<?> getInstallTask(DefaultDependencyManager dependencyManager, File zipFile, String name) {
                    return new MultiMCModpackInstallTask(dependencyManager, zipFile, this, cfg, name);
                }
            };
        }
    }

}
