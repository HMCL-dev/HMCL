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

import org.jackhuang.hmcl.addon.meta.ForgeOldModMetadata;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.UnsupportedInstallationException;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
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
import static org.jackhuang.hmcl.download.UnsupportedInstallationException.UNSUPPORTED_LAUNCH_WRAPPER;
import static org.jackhuang.hmcl.util.StringUtils.removePrefix;
import static org.jackhuang.hmcl.util.StringUtils.removeSuffix;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

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

        var type = detectForgeInstallerType(remote.getGameVersion(), installer);

        switch (type) {
            case LEGACY:
                dependency = new ForgeLegacyInstallTask(dependencyManager, manifest, remote.getSelfVersion(), installer);
                break;
            case OLD:
                dependency = new ForgeOldInstallTask(dependencyManager, manifest, remote.getSelfVersion(), installer);
                break;
            case NEW:
                dependency = new GameDownloadTask(dependencyManager, manifest)
                        .thenComposeAsync(minecraftJar -> new ForgeNewInstallTask(
                                dependencyManager,
                                manifest,
                                minecraftJar,
                                remote.getSelfVersion(),
                                installer));
                break;
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
    public static ForgeInstallerType detectForgeInstallerType(String gameVersion, Path installer) throws IOException, VersionMismatchException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            if ((Files.isRegularFile(fs.getPath("mod_MinecraftForge.class")) || Files.isRegularFile(fs.getPath("fmlversion.properties"))) && !Files.isRegularFile(fs.getPath("install_profile.json"))) {
                return ForgeInstallerType.LEGACY;
            }

            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (installProfile.containsKey("spec")) {
                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.equals(profile.getMinecraft()))
                    throw new VersionMismatchException(profile.getMinecraft(), gameVersion);
                return ForgeInstallerType.NEW;
            } else if (installProfile.containsKey("install") && installProfile.containsKey("versionInfo")) {
                ForgeInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeInstallProfile.class);
                if (!gameVersion.equals(profile.install().minecraft()))
                    throw new VersionMismatchException(profile.install().minecraft(), gameVersion);
                return ForgeInstallerType.OLD;
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
    /// @throws IOException                      if the installer profile is missing, malformed, or
    ///                                  unsupported
    /// @throws VersionMismatchException         if the installer targets another Minecraft version
    /// @throws UnsupportedInstallationException if the manifest already contains Cleanroom
    public static Task<GameInstancePatch> install(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            String gameVersion,
            Path installer) throws IOException, VersionMismatchException, UnsupportedInstallationException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            if ((Files.isRegularFile(fs.getPath("mod_MinecraftForge.class")) || Files.isRegularFile(fs.getPath("fmlversion.properties"))) && !Files.isRegularFile(fs.getPath("install_profile.json"))) {
                return new ForgeLegacyInstallTask(dependencyManager, manifest, tryGetLegacyForgeVersion(installer), installer);
            }

            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (installProfile.containsKey("spec")) {
                checkCleanroomCompatibility(dependencyManager, manifest, gameVersion);
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
                checkCleanroomCompatibility(dependencyManager, manifest, gameVersion);
                ForgeInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeInstallProfile.class);
                if (!gameVersion.equals(profile.install().minecraft()))
                    throw new VersionMismatchException(profile.install().minecraft(), gameVersion);
                return new ForgeOldInstallTask(dependencyManager, manifest, modifyVersion(gameVersion, profile.install().path().getVersion().replaceAll("(?i)forge", "")), installer);
            } else {
                throw new IOException();
            }
        }
    }

    /// Rejects Forge installation when the manifest already contains Cleanroom.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the Forge patch
    /// @param gameVersion       Minecraft version used for component analysis
    /// @throws UnsupportedInstallationException if the manifest already contains Cleanroom
    private static void checkCleanroomCompatibility(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            String gameVersion) throws UnsupportedInstallationException {
        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(
                dependencyManager.getGameRepository().resolve(manifest),
                GameVersionNumber.asGameVersion(gameVersion));
        if (analyzer.has(GameComponentType.CLEANROOM)) {
            throw new UnsupportedInstallationException(CLEANROOM_NOT_COMPATIBLE_WITH_FORGE);
        }
    }

    private static String modifyVersion(String gameVersion, String version) {
        return removePrefix(removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_"), "forge-");
    }

    public static @Nullable String tryGetLegacyForgeVersion(Path path) {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(path)) {
            if (Files.isRegularFile(fs.getPath("mod_MinecraftForge.info"))) {
                ForgeOldModMetadata metadata = JsonUtils.fromNonNullJson(Files.readString(fs.getPath("mod_MinecraftForge.info")), ForgeOldModMetadata.class);
                return metadata.getVersion();
            }

            if (Files.isRegularFile(fs.getPath("forgeversion.properties"))) {
                Properties properties = new Properties();
                properties.load(Files.newInputStream(fs.getPath("forgeversion.properties")));
                List<String> list = new ArrayList<>();
                list.add(properties.getProperty("forge.major.number"));
                list.add(properties.getProperty("forge.minor.number"));
                list.add(properties.getProperty("forge.revision.number"));
                list.add(properties.getProperty("forge.build.number"));
                return String.join(".", list);
            }

            return null;

        } catch (IOException e) {
            LOG.warning("Failed to get legacy forge version", e);
            return null;
        }
    }
}
