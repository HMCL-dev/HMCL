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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.ArtifactMalformedException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.Task;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ForgeLegacyInstallTask extends Task<GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final Path installer;
    private final String selfVersion;
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    ForgeLegacyInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, String selfVersion, Path installer) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.installer = installer;
        this.selfVersion = selfVersion;

        setSignificance(TaskSignificance.MAJOR);
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void execute() throws Exception {
        try (ZipFile zipFile = new ZipFile(installer.toFile())) {
            ZipEntry entry1 = zipFile.getEntry("fmlversion.properties");
            ZipEntry entry2 = zipFile.getEntry("mod_MinecraftForge.class");

            InputStream stream = null;
            InputStream stream2 = null;

            if (entry1 != null) {
                stream = zipFile.getInputStream(entry1);
            }
            if (entry2 != null) {
                stream2 = zipFile.getInputStream(entry2);
            }

            if (stream == null && stream2 == null) {
                throw new ArtifactMalformedException("Malformed forge installer file, forgeversion.properties and mod_MinecraftForge.class both does not exist.");
            }

            // unpack the universal jar in the installer file.
            Library forgeLibrary = new Library(new Artifact(
                    "net.minecraftforge", "forge", selfVersion
            ));
            GameRepository gameRepository = dependencyManager.getGameRepository();
            Path forgeFile = gameRepository.getLayout().getLibraryFile(manifest.id(), forgeLibrary);
            Files.createDirectories(forgeFile.getParent());

            try (InputStream is = Files.newInputStream(installer);
                 OutputStream os = Files.newOutputStream(forgeFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                is.transferTo(os);
            }

            setResult(new GameInstancePatch(GameComponentType.FORGE.getPatchId(), selfVersion, GameInstancePatch.PRIORITY_LOADER, null, null, List.of(forgeLibrary)));
        } catch (ZipException ex) {
            throw new ArtifactMalformedException("Malformed forge installer file", ex);
        }
    }
}
