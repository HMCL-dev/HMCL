/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.neoforge;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.forge.*;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.StringUtils.removePrefix;
import static org.jackhuang.hmcl.util.StringUtils.removeSuffix;

public final class NeoForgeInstallTask extends Task<GameInstancePatch> {
    private final DefaultDependencyManager dependencyManager;

    private final GameInstanceManifest manifest;

    private final NeoForgeRemoteVersion remoteVersion;

    /// Key the build record is stored under.
    ///
    /// It is built from the remote version rather than from the installer profile, because deciding
    /// whether the installer has to be fetched happens before any profile can be read, while the
    /// version the patch ends up carrying is derived from that profile.
    private final String buildKey;

    /// NeoForge installer JAR, or `null` when [#buildManifest] proves the build already ran.
    private @Nullable Path installer = null;

    private @Nullable FileDownloadTask dependent;

    private Task<GameInstancePatch> dependency;

    /// Record of an earlier build of this loader, reused instead of rebuilding it.
    private @Nullable LoaderBuildManifest buildManifest;

    public NeoForgeInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, NeoForgeRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.remoteVersion = remoteVersion;
        this.buildKey = LoaderBuildManifest.buildKey(
                GameComponentType.NEO_FORGE.getPatchId(), remoteVersion.getSelfVersion(), "client");
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        // Resolve the build record before scheduling the download, because a record that verifies
        // means the build has already run and the installer JAR is not needed at all.
        buildManifest = LoaderBuildManifest.load(dependencyManager.getGameRepository(), buildKey);
        if (buildManifest != null && !buildManifest.verify(dependencyManager.getGameRepository()))
            buildManifest = null;

        if (buildManifest != null)
            return;

        installer = Files.createTempFile("neoforge-installer", ".jar");

        dependent = new FileDownloadTask(
                dependencyManager.getDownloadProvider().injectURLsWithCandidates(remoteVersion.getUrls()),
                installer, null
        );
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
        if (installer != null)
            Files.deleteIfExists(installer);
        this.setResult(dependency.getResult());
    }

    @Override
    public Collection<? extends Task<?>> getDependents() {
        return dependent == null ? Collections.emptyList() : Collections.singleton(dependent);
    }

    @Override
    public Collection<? extends Task<?>> getDependencies() {
        return Collections.singleton(dependency);
    }

    @Override
    public void execute() throws Exception {
        dependency = install(
                dependencyManager, manifest, remoteVersion.getGameVersion(),
                installer, buildManifest, buildKey);
    }

    /// Creates a task that installs NeoForge from a local installer JAR.
    ///
    /// The returned task obtains the matching vanilla client JAR from shared cache storage and
    /// passes it explicitly to the processor implementation.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the NeoForge patch
    /// @param gameVersion       Minecraft version expected by the installation
    /// @param installer         the NeoForge installer JAR
    /// @return the task producing the NeoForge patch
    /// @throws IOException              if the installer profile is missing, malformed, or
    ///                                  unsupported
    /// @throws VersionMismatchException if the installer targets another Minecraft version
    public static Task<GameInstancePatch> install(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            String gameVersion,
            Path installer) throws IOException, VersionMismatchException {
        return install(dependencyManager, manifest, gameVersion, installer, null, null);
    }

    /// Creates a task that installs NeoForge.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the NeoForge patch
    /// @param gameVersion       Minecraft version expected by the installation
    /// @param installer         the NeoForge installer JAR, or `null` when `buildManifest` is given
    /// @param buildManifest     record of an earlier build, which stands in for the installer
    ///                          profile when the installer was never fetched
    /// @param buildKey          the key the build record is stored under, or `null` to derive one
    /// @return the task producing the NeoForge patch
    /// @throws IOException              if the installer profile is missing, malformed, or
    ///                                  unsupported
    /// @throws VersionMismatchException if the installer targets another Minecraft version
    public static Task<GameInstancePatch> install(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            String gameVersion,
            @Nullable Path installer,
            @Nullable LoaderBuildManifest buildManifest,
            @Nullable String buildKey) throws IOException, VersionMismatchException {
        String installProfileText;
        boolean neoForgeMarker;
        if (buildManifest != null) {
            // The record proves a NeoForge build already ran, so the profile is read from it rather
            // than from an installer JAR that was never downloaded. The bundled RSA marker cannot be
            // checked without that JAR, but it only serves to reject installers that are not
            // NeoForge at all, which a record of a finished NeoForge build already rules out.
            installProfileText = buildManifest.getInstallProfile();
            neoForgeMarker = true;
        } else {
            if (installer == null)
                throw new IOException("NeoForge installer JAR not found");

            try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
                installProfileText = Files.readString(fs.getPath("install_profile.json"));
                neoForgeMarker = Files.exists(fs.getPath("META-INF/NEOFORGE.RSA"))
                        || installProfileText.contains("neoforge");
            }
        }

        Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
        if (GameComponentType.FORGE.getPatchId().equals(installProfile.get("profile")) && neoForgeMarker) {
            ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
            if (!gameVersion.equals(profile.getMinecraft()))
                throw new VersionMismatchException(profile.getMinecraft(), gameVersion);
            String recordKey = resolveBuildKey(buildKey, profile.getVersion());
            return new GameDownloadTask(dependencyManager, manifest)
                    .thenComposeAsync(minecraftJar -> new ForgeNewInstallTask(
                            dependencyManager,
                            manifest,
                            minecraftJar,
                            modifyNeoForgeOldVersion(gameVersion, profile.getVersion()),
                            installer,
                            buildManifest,
                            recordKey))
                    .thenApplyAsync(neoForgeVersion -> {
                        if (!neoForgeVersion.id().equals(GameComponentType.FORGE.getPatchId()) || neoForgeVersion.version() == null) {
                            throw new IOException("Invalid neoforge version.");
                        }
                        return neoForgeVersion.withId(GameComponentType.NEO_FORGE.getPatchId())
                                .withVersion(
                                        removePrefix(neoForgeVersion.version().replace(GameComponentType.FORGE.getPatchId(), ""), "-")
                                );
                    });
        } else if (GameComponentType.NEO_FORGE.getPatchId().equals(installProfile.get("profile")) || "NeoForge".equals(installProfile.get("profile"))) {
            ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
            if (!gameVersion.equals(profile.getMinecraft()))
                throw new VersionMismatchException(profile.getMinecraft(), gameVersion);
            // This flavour used to be served by a copy of the Forge processor task. Sharing the
            // original keeps the build record, and the skipping it enables, working for both.
            String recordKey = resolveBuildKey(buildKey, profile.getVersion());
            return new GameDownloadTask(dependencyManager, manifest)
                    .thenComposeAsync(minecraftJar -> new ForgeNewInstallTask(
                            dependencyManager,
                            manifest,
                            minecraftJar,
                            modifyNeoForgeNewVersion(profile.getVersion()),
                            installer,
                            buildManifest,
                            recordKey))
                    .thenApplyAsync(neoForgeVersion -> neoForgeVersion.withId(GameComponentType.NEO_FORGE.getPatchId()));
        } else {
            throw new IOException();
        }
    }

    /// Returns the key to record a build under.
    ///
    /// @param buildKey the key the caller already resolved, or `null` when it has none, which is
    ///                 the case for the local-installer entry point
    /// @param version  the version named by the installer profile
    /// @return the build key
    private static String resolveBuildKey(@Nullable String buildKey, String version) {
        return buildKey != null
                ? buildKey
                : LoaderBuildManifest.buildKey(GameComponentType.NEO_FORGE.getPatchId(), version, "client");
    }

    private static String modifyNeoForgeOldVersion(String gameVersion, String version) {
        return removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_");
    }

    private static String modifyNeoForgeNewVersion(String version) {
        return removePrefix(version.replace("neoforge", ""), "-");
    }
}
