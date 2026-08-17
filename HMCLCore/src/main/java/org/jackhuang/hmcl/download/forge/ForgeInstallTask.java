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

import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.jackhuang.hmcl.download.UnsupportedInstallationException.UNSUPPORTED_LAUNCH_WRAPPER;
import static org.jackhuang.hmcl.util.StringUtils.removePrefix;
import static org.jackhuang.hmcl.util.StringUtils.removeSuffix;

/**
 *
 * @author huangyuhui
 */
public final class ForgeInstallTask extends Task<GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private Path installer;
    private final ForgeRemoteVersion remote;
    private FileDownloadTask dependent;
    private Task<GameInstancePatch> dependency;

    public ForgeInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, ForgeRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.remote = remoteVersion;
        setSignificance(TaskSignificance.MODERATE);
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
                installer, null);
        dependent.setCacheRepository(dependencyManager.getCacheRepository());
        dependent.setCaching(true);
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
    public void execute() throws IOException, VersionMismatchException, UnsupportedInstallationException {
        String originalMainClass = dependencyManager.getGameRepository().resolve(manifest).launchManifest().mainClass();
        if (GameVersionNumber.compare("1.13", remote.getGameVersion()) <= 0) {
            // Forge 1.13 is not compatible with fabric.
            if (!GameComponentAnalyzer.FORGE_OPTIFINE_MAIN.contains(originalMainClass))
                throw new UnsupportedInstallationException(UNSUPPORTED_LAUNCH_WRAPPER);
        }

        if (detectForgeInstallerType(remote.getGameVersion(), installer)) {
            dependency = new GameDownloadTask(dependencyManager, manifest)
                    .thenComposeAsync(minecraftJar -> new ForgeNewInstallTask(
                            dependencyManager,
                            manifest,
                            minecraftJar,
                            remote.getSelfVersion(),
                            installer));
        } else {
            dependency = new ForgeOldInstallTask(dependencyManager, manifest, remote.getSelfVersion(), installer);
        }
    }

    /// Returns whether a Forge installer uses the processor-based format.
    ///
    /// @param gameVersion Minecraft version expected by the installation
    /// @param installer   the Forge installer JAR
    /// @return `true` for the processor-based format, or `false` for the legacy format
    /// @throws IOException              if the installer profile is missing, malformed, or
    ///                                  unsupported
    /// @throws VersionMismatchException if the installer targets another Minecraft version
    public static boolean detectForgeInstallerType(String gameVersion, Path installer) throws IOException, VersionMismatchException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (installProfile.containsKey("spec")) {
                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.equals(profile.getMinecraft()))
                    throw new VersionMismatchException(profile.getMinecraft(), gameVersion);
                return true;
            } else if (installProfile.containsKey("install") && installProfile.containsKey("versionInfo")) {
                ForgeInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeInstallProfile.class);
                if (!gameVersion.equals(profile.install().getMinecraft()))
                    throw new VersionMismatchException(profile.install().getMinecraft(), gameVersion);
                return false;
            } else {
                throw new IOException();
            }
        }
    }

    /// Creates a task that installs Forge from a local installer JAR.
    ///
    /// Processor-based installers obtain a verified vanilla client JAR from shared cache storage;
    /// neither format reads an instance JAR.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the Forge patch
    /// @param gameVersion       Minecraft version expected by the installation
    /// @param installer         the Forge installer JAR
    /// @return the task producing the Forge patch
    /// @throws IOException              if the installer profile is missing, malformed, or
    ///                                  unsupported
    /// @throws VersionMismatchException if the installer targets another Minecraft version
    public static Task<GameInstancePatch> install(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            String gameVersion,
            Path installer) throws IOException, VersionMismatchException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (installProfile.containsKey("spec")) {
                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.equals(profile.getMinecraft()))
                    throw new VersionMismatchException(profile.getMinecraft(), gameVersion);
                return new GameDownloadTask(dependencyManager, manifest)
                        .thenComposeAsync(minecraftJar -> new ForgeNewInstallTask(
                                dependencyManager,
                                manifest,
                                minecraftJar,
                                modifyVersion(gameVersion, profile.getVersion()),
                                installer));
            } else if (installProfile.containsKey("install") && installProfile.containsKey("versionInfo")) {
                ForgeInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeInstallProfile.class);
                if (!gameVersion.equals(profile.install().getMinecraft()))
                    throw new VersionMismatchException(profile.install().getMinecraft(), gameVersion);
                return new ForgeOldInstallTask(dependencyManager, manifest, modifyVersion(gameVersion, profile.install().getPath().getVersion().replaceAll("(?i)forge", "")), installer);
            } else {
                throw new IOException();
            }
        }
    }

    private static String modifyVersion(String gameVersion, String version) {
        return removePrefix(removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_"), "forge-");
    }
}
