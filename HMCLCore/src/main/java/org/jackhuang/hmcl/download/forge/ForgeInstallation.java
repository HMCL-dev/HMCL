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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.UnsupportedInstallationException;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.jackhuang.hmcl.download.UnsupportedInstallationException.CLEANROOM_NOT_COMPATIBLE_WITH_FORGE;
import static org.jackhuang.hmcl.util.StringUtils.removePrefix;
import static org.jackhuang.hmcl.util.StringUtils.removeSuffix;

public final class ForgeInstallation {
    private ForgeInstallation() {
        throw new AssertionError();
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
            if ((Files.isRegularFile(fs.getPath("fmlversion.properties")) || Files.isRegularFile(fs.getPath("forgeversion.properties"))) && !Files.isRegularFile(fs.getPath("install_profile.json"))) {
                return ForgeInstallerType.LEGACY_FML;
            }

            if ((Files.isRegularFile(fs.getPath("mod_MinecraftForge.class"))) && !Files.isRegularFile(fs.getPath("install_profile.json"))) {
                return ForgeInstallerType.LEGACY_MODLOADER;
            }

            String installProfileText = Files.readString(fs.getPath("install_profile.json"));
            Map<?, ?> installProfile = JsonUtils.fromNonNullJson(installProfileText, Map.class);
            if (installProfile.containsKey("spec")) {
                ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
                if (!gameVersion.equals(profile.minecraft()))
                    throw new VersionMismatchException(profile.minecraft(), gameVersion);
                return ForgeInstallerType.NEW;
            } else if (installProfile.containsKey("install") && installProfile.containsKey("versionInfo")) {
                ForgeOldInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeOldInstallProfile.class);
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
    public static Task<GameInstancePatch> install(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, String gameVersion, Path installer) throws IOException, VersionMismatchException, UnsupportedInstallationException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            var type = detectForgeInstallerType(gameVersion, installer);

            switch (type) {
                case LEGACY_MODLOADER, LEGACY_FML -> {
                    var forgeProfile = ForgeLegacyInstallProfile.parse(installer);
                    return new ForgeLegacyInstallTask(dependencyManager, manifest, forgeProfile != null ? forgeProfile.forgeVersion() : null, installer, type);
                }
                case OLD -> {
                    checkCleanroomCompatibility(dependencyManager, manifest, gameVersion);
                    String installProfileText = Files.readString(fs.getPath("install_profile.json"));
                    ForgeOldInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeOldInstallProfile.class);

                    return new ForgeOldInstallTask(dependencyManager, manifest, modifyVersion(gameVersion, profile.install().path().getVersion().replaceAll("(?i)forge", "")), installer);
                }
                case NEW -> {
                    checkCleanroomCompatibility(dependencyManager, manifest, gameVersion);
                    String installProfileText = Files.readString(fs.getPath("install_profile.json"));
                    ForgeNewInstallProfile profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);

                    return new GameDownloadTask(dependencyManager, manifest).thenComposeAsync(minecraftJar -> new ForgeNewInstallTask(dependencyManager, manifest, minecraftJar, modifyVersion(gameVersion, profile.version()), installer));
                }
                default -> throw new IOException();
            }
        }
    }

    /// Rejects Forge installation when the manifest already contains Cleanroom.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the Forge patch
    /// @param gameVersion       Minecraft version used for component analysis
    /// @throws UnsupportedInstallationException if the manifest already contains Cleanroom
    private static void checkCleanroomCompatibility(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, String gameVersion) throws UnsupportedInstallationException {
        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(dependencyManager.getGameRepository().resolve(manifest), GameVersionNumber.asGameVersion(gameVersion));
        if (analyzer.has(GameComponentType.CLEANROOM)) {
            throw new UnsupportedInstallationException(CLEANROOM_NOT_COMPATIBLE_WITH_FORGE);
        }
    }

    private static String modifyVersion(String gameVersion, String version) {
        return removePrefix(removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_"), "forge-");
    }

    public static String toLookupVersion(String gameVersion) {
        return switch (gameVersion) {
            case "1.7.10-pre4" -> "1.7.10_pre4";
            case "1.4" -> "1.4.0";
            default -> gameVersion;
        };
    }

    public static String fromLookupVersion(String gameVersion) {
        return switch (gameVersion) {
            case "1.7.10_pre4" -> "1.7.10-pre4";
            case "1.4.0" -> "1.4";
            default -> gameVersion;
        };
    }
}
