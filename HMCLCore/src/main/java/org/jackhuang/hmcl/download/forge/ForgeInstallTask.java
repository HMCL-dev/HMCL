// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.jackhuang.hmcl.download.UnsupportedInstallationException.CLEANROOM_NOT_COMPATIBLE_WITH_FORGE;
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
    private final ForgeRemoteVersion remote;

    public ForgeInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, ForgeRemoteVersion remoteVersion) {
        if (!manifest.isModifiable()) {
            throw new IllegalArgumentException("Manifest is not modifiable");
        }

        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.remote = remoteVersion;
        setSignificance(TaskSignificance.MODERATE);
    }

    /// Downloads the installer and returns the patch produced by the matching Forge installer format.
    @Override
    public GameInstancePatch execute(TaskExecutionContext context) throws Exception {
        Path installer = Files.createTempFile("forge-installer", ".jar");
        try (Closeable cleanup = () -> Files.deleteIfExists(installer)) {
            DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
            var download = new FileDownloadTask(
                    downloadProvider.getDownloadCandidates(remote),
                    installer, null);
            download.setCacheRepository(dependencyManager.getCacheRepository());
            download.setCaching(true);
            download.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
            context.run(download);

            String originalMainClass = manifest.mainClass();
            if (GameVersionNumber.asGameVersion("1.13").compareTo(remote.getGameVersion()) <= 0) {
                // Forge 1.13 is not compatible with fabric.
                if (!GameComponentAnalyzer.FORGE_OPTIFINE_MAIN.contains(originalMainClass))
                    throw new UnsupportedInstallationException(UNSUPPORTED_LAUNCH_WRAPPER);
            }

            Task<GameInstancePatch> install;
            if (detectForgeInstallerType(remote.getGameVersion(), installer)) {
                install = new GameDownloadTask(dependencyManager, manifest)
                        .thenComposeAsync(minecraftJar -> new ForgeNewInstallTask(
                                dependencyManager,
                                manifest,
                                minecraftJar,
                                remote.getSelfVersion(),
                                installer));
            } else {
                install = new ForgeOldInstallTask(dependencyManager, manifest, remote.getSelfVersion(), installer);
            }
            return context.run(install);
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
    public static boolean detectForgeInstallerType(GameVersionNumber gameVersion, Path installer) throws IOException, VersionMismatchException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (installProfile.containsKey("spec")) {
                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.equals(GameVersionNumber.asGameVersion(profile.getMinecraft())))
                    throw new VersionMismatchException(profile.getMinecraft(), gameVersion.toString());
                return true;
            } else if (installProfile.containsKey("install") && installProfile.containsKey("versionInfo")) {
                ForgeInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeInstallProfile.class);
                if (!gameVersion.equals(GameVersionNumber.asGameVersion(profile.install().getMinecraft())))
                    throw new VersionMismatchException(profile.install().getMinecraft(), gameVersion.toString());
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
    /// @throws UnsupportedInstallationException if the manifest already contains Cleanroom
    public static Task<GameInstancePatch> install(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            String gameVersion,
            Path installer) throws IOException, VersionMismatchException, UnsupportedInstallationException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (installProfile.containsKey("spec")) {
                checkCleanroomCompatibility(manifest, gameVersion);
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
                checkCleanroomCompatibility(manifest, gameVersion);
                ForgeInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeInstallProfile.class);
                if (!gameVersion.equals(profile.install().getMinecraft()))
                    throw new VersionMismatchException(profile.install().getMinecraft(), gameVersion);
                return new ForgeOldInstallTask(dependencyManager, manifest, modifyVersion(gameVersion, profile.install().getPath().getVersion().replaceAll("(?i)forge", "")), installer);
            } else {
                throw new IOException();
            }
        }
    }

    /// Rejects Forge installation when the manifest already contains Cleanroom.
    ///
    /// @param manifest          working manifest receiving the Forge patch
    /// @param gameVersion       Minecraft version used for component analysis
    /// @throws UnsupportedInstallationException if the manifest already contains Cleanroom
    private static void checkCleanroomCompatibility(
            GameInstanceManifest manifest,
            String gameVersion) throws UnsupportedInstallationException {
        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(manifest, GameVersionNumber.asGameVersion(gameVersion));
        if (analyzer.has(GameComponentType.CLEANROOM)) {
            throw new UnsupportedInstallationException(CLEANROOM_NOT_COMPATIBLE_WITH_FORGE);
        }
    }

    private static String modifyVersion(String gameVersion, String version) {
        return removePrefix(removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_"), "forge-");
    }
}
