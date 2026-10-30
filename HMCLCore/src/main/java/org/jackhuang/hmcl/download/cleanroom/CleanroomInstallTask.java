// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.cleanroom;

import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallProfile;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

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
    private @Nullable Path installer;
    private @Nullable FileDownloadTask dependent;
    private @Nullable Task<GameInstancePatch> task;
    private @Nullable String selfVersion;

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

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        if (installer == null) {
            installer = Files.createTempFile("cleanroom-installer", ".jar");

            CleanroomRemoteVersion remoteVersion = Objects.requireNonNull(remote);
            DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
            dependent = new FileDownloadTask(
                    downloadProvider.getDownloadCandidates(remoteVersion),
                    installer, null);
            dependent.setCacheRepository(dependencyManager.getCacheRepository());
            dependent.setCaching(true);
            dependent.addIntegrityCheckHandler(FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER);
        }
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        if (remote != null) {
            Files.deleteIfExists(Objects.requireNonNull(installer));
        }

        setResult(Objects.requireNonNull(task).getResult());
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependent == null ? Collections.emptySet() : Collections.singleton(dependent);
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return Collections.singleton(Objects.requireNonNull(task));
    }

    @Override
    public void execute() throws IOException, VersionMismatchException, UnsupportedInstallationException {
        String cleanroomVersion;
        if (selfVersion == null) {
            cleanroomVersion = Objects.requireNonNull(remote).getSelfVersion();
        } else {
            cleanroomVersion = selfVersion;
        }

        InstallProfile profile;
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(Objects.requireNonNull(installer))) {
            profile = JsonUtils.fromNonNullJson(Files.readString(fs.getPath("install_profile.json")), InstallProfile.class);
        }

        // Since 0.7, installers no longer bundle the Cleanroom jar, and point to Maven repositories instead.
        Map<String, String> repositories = Objects.requireNonNullElse(profile.repositories(), Map.of());
        List<Library> remoteLibraries = profile.libraries().stream()
                .filter(library -> !library.hasDownloadURL() && repositories.containsKey(library.groupId()))
                .map(library -> withRepository(library, repositories))
                .toList();

        task = new GameDownloadTask(dependencyManager, manifest)
                .thenComposeAsync(minecraftJar -> new GameLibrariesTask(dependencyManager, manifest, true, remoteLibraries)
                        .thenComposeAsync(new ForgeNewInstallTask(
                                dependencyManager,
                                manifest,
                                minecraftJar,
                                cleanroomVersion,
                                Objects.requireNonNull(installer))))
                .thenApplyAsync(patch -> patch
                        .withLibraries(patch.getLibraries().stream()
                                .map(library -> withRepository(library, repositories))
                                .toList())
                        .withId(GameComponentType.CLEANROOM));
    }

    /// Fields of the Cleanroom installer profile used by this task.
    ///
    /// @param libraries    libraries required by the installer
    /// @param repositories Maven repository base URLs keyed by group ID, present since Cleanroom 0.7
    @JsonSerializable
    private record InstallProfile(List<Library> libraries, @Nullable Map<String, String> repositories) {
    }

    /// Points a library without a download URL at the Maven repository declared for its group.
    ///
    /// @param library      library to resolve
    /// @param repositories Maven repository base URLs keyed by group ID
    /// @return the library with its repository set, or the original library if it is unchanged
    private static Library withRepository(Library library, Map<String, String> repositories) {
        String repository = repositories.get(library.groupId());
        if (repository == null || library.hasDownloadURL())
            return library;
        return new Library(library.artifact(), repository, library.downloads(), library.checksums(), library.extract(),
                library.natives(), library.rules(), library.hint(), library.filename());
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
        return StringUtils.substringAfter(version, "Cleanroom-", version);
    }
}
