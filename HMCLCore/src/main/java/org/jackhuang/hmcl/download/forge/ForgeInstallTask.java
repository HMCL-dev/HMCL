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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DependencyManager;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.optifine.OptiFineInstallTask;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.util.StringUtils.removePrefix;
import static org.jackhuang.hmcl.util.StringUtils.removeSuffix;

/**
 *
 * @author huangyuhui
 */
public final class ForgeInstallTask extends Task<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private Path installer;
    private final ForgeRemoteVersion remote;
    private FileDownloadTask dependent;
    private Task<Version> dependency;

    public ForgeInstallTask(DefaultDependencyManager dependencyManager, Version version, ForgeRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.remote = remoteVersion;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        installer = Files.createTempFile("forge-installer", ".jar");

        dependent = new FileDownloadTask(
                dependencyManager.getDownloadProvider().injectURLsWithCandidates(remote.getUrls()),
                installer.toFile(), null)
                .setCacheRepository(dependencyManager.getCacheRepository())
                .setCaching(true);
        dependent.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        Files.deleteIfExists(installer);
        setResult(dependency.getResult());
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singleton(dependent);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return Collections.singleton(dependency);
    }

    @Override
    public void execute() throws IOException, VersionMismatchException, OptiFineInstallTask.UnsupportedOptiFineInstallationException {
        String originalMainClass = version.resolve(dependencyManager.getGameRepository()).getMainClass();
        if (VersionNumber.VERSION_COMPARATOR.compare("1.13", remote.getGameVersion()) <= 0) {
            // Forge 1.13 is not compatible with any other libraries.
            if (!"net.minecraft.client.main.Main".equals(originalMainClass) && !"cpw.mods.modlauncher.Launcher".equals(originalMainClass))
                throw new OptiFineInstallTask.UnsupportedOptiFineInstallationException();
        } else {
            // Forge 1.12 and older versions is compatible with vanilla and launchwrapper.
            // if (!"net.minecraft.client.main.Main".equals(originalMainClass) && !"net.minecraft.launchwrapper.Launch".equals(originalMainClass))
            //     throw new OptiFineInstallTask.UnsupportedOptiFineInstallationException();
        }


        if (detectForgeInstallerType(dependencyManager, version, installer))
            dependency = new ForgeNewInstallTask(dependencyManager, version, remote.getSelfVersion(), installer);
        else
            dependency = new ForgeOldInstallTask(dependencyManager, version, remote.getSelfVersion(), installer);
    }

    /**
     * Detect Forge installer type.
     *
     * @param dependencyManager game repository
     * @param version version.json
     * @param installer the Forge installer, either the new or old one.
     * @return true for new, false for old
     * @throws IOException if unable to read compressed content of installer file, or installer file is corrupted, or the installer is not the one we want.
     * @throws VersionMismatchException if required game version of installer does not match the actual one.
     */
    public static boolean detectForgeInstallerType(DependencyManager dependencyManager, Version version, Path installer) throws IOException, VersionMismatchException {
        Optional<String> gameVersion = GameVersion.minecraftVersion(dependencyManager.getGameRepository().getVersionJar(version));
        if (!gameVersion.isPresent()) throw new IOException();
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = FileUtils.readText(fs.getPath("install_profile.json"));
            Map installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (installProfile.containsKey("spec")) {
                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.get().equals(profile.getMinecraft()))
                    throw new VersionMismatchException(profile.getMinecraft(), gameVersion.get());
                return true;
            } else if (installProfile.containsKey("install") && installProfile.containsKey("versionInfo")) {
                ForgeInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeInstallProfile.class);
                if (!gameVersion.get().equals(profile.getInstall().getMinecraft()))
                    throw new VersionMismatchException(profile.getInstall().getMinecraft(), gameVersion.get());
                return false;
            } else {
                throw new IOException();
            }
        }
    }

    /**
     * Install Forge library from existing local file.
     * This method will try to identify this installer whether it is in old or new format.
     *
     * @param dependencyManager game repository
     * @param version version.json
     * @param installer the Forge installer, either the new or old one.
     * @return the task to install library
     * @throws IOException if unable to read compressed content of installer file, or installer file is corrupted, or the installer is not the one we want.
     * @throws VersionMismatchException if required game version of installer does not match the actual one.
     */
    public static Task<Version> install(DefaultDependencyManager dependencyManager, Version version, Path installer) throws IOException, VersionMismatchException {
        Optional<String> gameVersion = GameVersion.minecraftVersion(dependencyManager.getGameRepository().getVersionJar(version));
        if (!gameVersion.isPresent()) throw new IOException();
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = FileUtils.readText(fs.getPath("install_profile.json"));
            Map installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (installProfile.containsKey("spec")) {
                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.get().equals(profile.getMinecraft()))
                    throw new VersionMismatchException(profile.getMinecraft(), gameVersion.get());
                return new ForgeNewInstallTask(dependencyManager, version, modifyVersion(gameVersion.get(), profile.getPath().getVersion().replaceAll("(?i)forge", "")), installer);
            } else if (installProfile.containsKey("install") && installProfile.containsKey("versionInfo")) {
                ForgeInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeInstallProfile.class);
                if (!gameVersion.get().equals(profile.getInstall().getMinecraft()))
                    throw new VersionMismatchException(profile.getInstall().getMinecraft(), gameVersion.get());
                return new ForgeOldInstallTask(dependencyManager, version, modifyVersion(gameVersion.get(), profile.getInstall().getPath().getVersion().replaceAll("(?i)forge", "")), installer);
            } else {
                throw new IOException();
            }
        }
    }

    private static String modifyVersion(String gameVersion, String version) {
        return removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_");
    }
}
