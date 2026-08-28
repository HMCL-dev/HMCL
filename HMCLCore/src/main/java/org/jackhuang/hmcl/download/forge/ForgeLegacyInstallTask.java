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
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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

public final class ForgeLegacyInstallTask extends Task<GameInstancePatch> {
    public static final Library MODLOADER_LIBRARY = new Library(new Artifact("modloader", "modloader", "1.1"));
    public static final String MODLOADER_DOWNLOAD_URL = "https://github.com/HMCL-dev/metadata/raw/refs/heads/main/fmllibs/ModLoader%201.1.zip";
    public static final Library MODLOADER_MP_LIBRARY = new Library(new Artifact("modloader", "modloader-mp", "1.1"));
    public static final String MODLOADER_MP_DOWNLOAD_URL = "https://github.com/HMCL-dev/metadata/raw/refs/heads/main/fmllibs/ModLoaderMP%201.1%20v4.zip";

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final Path installer;
    private final @Nullable String selfVersion;
    private final ForgeInstallerType type;
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    ForgeLegacyInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, @Nullable String selfVersion, Path installer, ForgeInstallerType type) throws IOException {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.installer = installer;
        if (selfVersion != null)
            this.selfVersion = selfVersion;
        else
            this.selfVersion = DigestUtils.digestToString("SHA-1", installer);
        this.type = type;

        setSignificance(TaskSignificance.MAJOR);
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public void preExecute() throws Exception {
        if (type == ForgeInstallerType.LEGACY_MODLOADER) {
            GameRepository gameRepository = dependencyManager.getGameRepository();

            Path modloaderFile = gameRepository.getLayout().getLibraryFile(manifest.id(), MODLOADER_LIBRARY);
            var modloaderDownloadTask = new FileDownloadTask(MODLOADER_DOWNLOAD_URL, modloaderFile, null);
            modloaderDownloadTask.setCacheRepository(dependencyManager.getCacheRepository());
            modloaderDownloadTask.setCaching(true);
            modloaderDownloadTask.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
            dependencies.add(modloaderDownloadTask);

            Path modloaderMpFile = gameRepository.getLayout().getLibraryFile(manifest.id(), MODLOADER_MP_LIBRARY);
            var modloaderMpDownloadTask = new FileDownloadTask(MODLOADER_MP_DOWNLOAD_URL, modloaderMpFile, null);
            modloaderMpDownloadTask.setCacheRepository(dependencyManager.getCacheRepository());
            modloaderMpDownloadTask.setCaching(true);
            modloaderMpDownloadTask.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
            dependencies.add(modloaderMpDownloadTask);
        }
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

            Library forgeLibrary = new Library(new Artifact("net.minecraftforge", "forge", selfVersion));
            GameRepository gameRepository = dependencyManager.getGameRepository();
            Path forgeFile = gameRepository.getLayout().getLibraryFile(manifest.id(), forgeLibrary);
            Files.createDirectories(forgeFile.getParent());

            try (InputStream is = Files.newInputStream(installer); OutputStream os = Files.newOutputStream(forgeFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                is.transferTo(os);
            }

            List<Library> libraries;
            if (type == ForgeInstallerType.LEGACY_MODLOADER) {
                libraries = List.of(forgeLibrary, MODLOADER_LIBRARY, MODLOADER_MP_LIBRARY);
            } else {
                libraries = List.of(forgeLibrary);
            }

            setResult(new GameInstancePatch(GameComponentType.FORGE.getPatchId(), selfVersion, GameInstancePatch.PRIORITY_LOADER, null, null, libraries));
        } catch (ZipException ex) {
            throw new ArtifactMalformedException("Malformed forge installer file", ex);
        }
    }
}
