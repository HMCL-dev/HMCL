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
import org.jackhuang.hmcl.download.forge.ForgeNewInstallProfile;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.jackhuang.hmcl.util.StringUtils.removePrefix;
import static org.jackhuang.hmcl.util.StringUtils.removeSuffix;

public final class NeoForgeInstallTask extends Task<GameInstancePatch> {
    private final DefaultDependencyManager dependencyManager;

    private final GameInstanceManifest manifest;

    private final NeoForgeRemoteVersion remoteVersion;

    private Path installer = null;

    private FileDownloadTask dependent;

    private Task<GameInstancePatch> dependency;

    public NeoForgeInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, NeoForgeRemoteVersion remoteVersion) {
        this.dependencyManager = dependencyManager;
        this.manifest = manifest;
        this.remoteVersion = remoteVersion;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
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
        Files.deleteIfExists(installer);
        this.setResult(dependency.getResult());
    }

    @Override
    public Collection<? extends Task<?>> getDependents() {
        return Collections.singleton(dependent);
    }

    @Override
    public Collection<? extends Task<?>> getDependencies() {
        return Collections.singleton(dependency);
    }

    @Override
    public void execute() throws Exception {
        dependency = install(dependencyManager, manifest, remoteVersion.getGameVersion(), installer);
    }

    /// Creates a task that installs NeoForge from a local installer JAR.
    ///
    /// The returned task obtains the matching vanilla client JAR from shared cache storage and
    /// passes it explicitly to the selected processor implementation.
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
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (GameComponentType.FORGE.getPatchId().equals(installProfile.get("profile")) && (Files.exists(fs.getPath("META-INF/NEOFORGE.RSA")) || installProfileText.contains("neoforge"))) {
                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.equals(profile.minecraft()))
                    throw new VersionMismatchException(profile.minecraft(), gameVersion);
                return new GameDownloadTask(dependencyManager, manifest)
                        .thenComposeAsync(minecraftJar -> new ForgeNewInstallTask(
                                dependencyManager,
                                manifest,
                                minecraftJar,
                                modifyNeoForgeOldVersion(gameVersion, profile.version()),
                                installer))
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
                if (!gameVersion.equals(profile.minecraft()))
                    throw new VersionMismatchException(profile.minecraft(), gameVersion);
                return new GameDownloadTask(dependencyManager, manifest)
                        .thenComposeAsync(minecraftJar -> new NeoForgeOldInstallTask(
                                dependencyManager,
                                manifest,
                                minecraftJar,
                                modifyNeoForgeNewVersion(profile.version()),
                                installer));
            } else {
                throw new IOException();
            }
        }
    }

    private static String modifyNeoForgeOldVersion(String gameVersion, String version) {
        return removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_");
    }

    private static String modifyNeoForgeNewVersion(String version) {
        return removePrefix(version.replace("neoforge", ""), "-");
    }
}
