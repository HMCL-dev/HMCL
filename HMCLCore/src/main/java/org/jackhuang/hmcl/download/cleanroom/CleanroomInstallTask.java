// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.cleanroom;

import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallProfile;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.download.UnsupportedInstallationException.CLEANROOM_NOT_COMPATIBLE_WITH_FORGE;

public final class CleanroomInstallTask extends Task<GameInstancePatch> {

    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    /// Minecraft version whose vanilla client JAR is required by the installer processors.
    private final GameVersionNumber gameVersion;
    private final @Nullable CleanroomRemoteVersion remote;
    /// The local installer supplied by the caller, or `null` to download it.
    private final @Nullable Path installer;
    /// The local installer version, or `null` for a remote installation.
    private final @Nullable String selfVersion;

    /// Creates a Cleanroom task that downloads the selected installer.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the Cleanroom patch
    /// @param remoteVersion     selected Cleanroom version
    public CleanroomInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            CleanroomRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.gameVersion = remoteVersion.getGameVersion();
        this.remote = remoteVersion;
        this.installer = null;
        this.selfVersion = null;

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Creates a Cleanroom task backed by an existing local installer.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the Cleanroom patch
    /// @param gameVersion       Minecraft version expected by the installation
    /// @param selfVersion       Cleanroom version recorded in the returned patch
    /// @param installer         Cleanroom installer JAR
    public CleanroomInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            GameVersionNumber gameVersion,
            String selfVersion,
            Path installer) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.gameVersion = gameVersion;
        this.selfVersion = selfVersion;
        this.remote = null;
        this.installer = installer;

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Obtains the installer and returns its Cleanroom patch for the matching vanilla client JAR.
    @Override
    public GameInstancePatch execute(TaskExecutionContext context) throws Exception {
        if (installer != null) {
            return install(context, installer, Objects.requireNonNull(selfVersion));
        }

        CleanroomRemoteVersion remoteVersion = Objects.requireNonNull(remote);
        Path downloadedInstaller = Files.createTempFile("cleanroom-installer", ".jar");
        try (Closeable cleanup = () -> Files.deleteIfExists(downloadedInstaller)) {
            DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
            var download = new FileDownloadTask(downloadProvider.getDownloadCandidates(remoteVersion), downloadedInstaller, null);
            download.setCacheRepository(dependencyManager.getCacheRepository());
            download.setCaching(true);
            download.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
            context.run(download);
            return install(context, downloadedInstaller, remoteVersion.getSelfVersion());
        }
    }

    /// Applies the downloaded or caller-supplied Cleanroom installer to the matching vanilla client JAR.
    private GameInstancePatch install(TaskExecutionContext context, Path installerFile, String cleanroomVersion) throws Exception {
        Path minecraftJar = context.run(new GameDownloadTask(dependencyManager, manifest));
        return context.run(new ForgeNewInstallTask(dependencyManager, manifest, minecraftJar, cleanroomVersion, installerFile))
                .withId(GameComponentType.CLEANROOM);
    }

    /// Creates a task that installs Cleanroom from a local installer JAR.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the Cleanroom patch
    /// @param gameVersion       Minecraft version expected by the installation
    /// @param installer         Cleanroom installer JAR
    /// @return the task producing the Cleanroom patch
    /// @throws IOException              if the installer profile is missing, malformed, or not a
    ///                                  Cleanroom profile
    /// @throws VersionMismatchException if the installer targets another Minecraft version
    /// @throws UnsupportedInstallationException if the manifest already contains Forge
    public static Task<GameInstancePatch> install(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            String gameVersion,
            Path installer) throws IOException, VersionMismatchException, UnsupportedInstallationException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (GameComponentType.CLEANROOM.getPatchId().equals(installProfile.get("profile"))) {
                GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(manifest, GameVersionNumber.asGameVersion(gameVersion));
                if (analyzer.has(GameComponentType.FORGE)) {
                    throw new UnsupportedInstallationException(CLEANROOM_NOT_COMPATIBLE_WITH_FORGE);
                }

                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.equals(profile.getMinecraft()))
                    throw new VersionMismatchException(profile.getMinecraft(), gameVersion);
                return new CleanroomInstallTask(
                        dependencyManager,
                        manifest,
                        GameVersionNumber.asGameVersion(gameVersion),
                        modifyVersion(profile.getVersion()),
                        installer);
            } else {
                throw new IOException();
            }
        }
    }

    private static String modifyVersion(String version) {
        return version.replace("cleanroom-", "");
    }
}
