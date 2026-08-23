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
package org.jackhuang.hmcl.download.optifine;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.UnsupportedInstallationException;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jenkinsci.constant_pool_scanner.ConstantPool;
import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.ConstantType;
import org.jenkinsci.constant_pool_scanner.Utf8Constant;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.Lang.getOrDefault;

/**
 * <b>Note</b>: OptiFine should be installed in the end.
 *
 * @author huangyuhui
 */
public final class OptiFineInstallTask extends Task<GameInstancePatch> {

    private final DefaultGameRepository gameRepository;
    private final DefaultDependencyManager dependencyManager;
    private final GameInstanceManifest manifest;
    private final OptiFineRemoteVersion remote;
    /// Vanilla client JAR used as patcher input.
    private final Path minecraftJar;
    private final @Nullable Path installer;
    private final List<Task<?>> dependents = new ArrayList<>(0);
    private final List<Task<?>> dependencies = new ArrayList<>(1);
    private @Nullable Path dest;

    private final Library optiFineLibrary;
    private final Library optiFineInstallerLibrary;

    /// Creates an OptiFine task that downloads its installer.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the OptiFine patch
    /// @param remoteVersion     selected OptiFine version
    /// @param minecraftJar      vanilla client JAR for the target Minecraft version
    public OptiFineInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            OptiFineRemoteVersion remoteVersion,
            Path minecraftJar) {
        this(dependencyManager, manifest, remoteVersion, minecraftJar, null);
    }

    /// Creates an OptiFine task with an optional local installer.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the OptiFine patch
    /// @param remoteVersion     selected OptiFine version
    /// @param minecraftJar      vanilla client JAR for the target Minecraft version
    /// @param installer         local installer JAR, or `null` to download it
    public  OptiFineInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            OptiFineRemoteVersion remoteVersion,
            Path minecraftJar,
            @Nullable Path installer) {
        this.dependencyManager = dependencyManager;
        this.gameRepository = dependencyManager.getGameRepository();
        this.manifest = manifest;
        this.remote = remoteVersion;
        this.minecraftJar = minecraftJar;
        this.installer = installer;

        String mavenVersion = remote.getGameVersion() + "_" + remote.getSelfVersion();

        optiFineLibrary = new Library(new Artifact("optifine", "OptiFine", mavenVersion));

        optiFineInstallerLibrary = new Library(
                new Artifact("optifine", "OptiFine", mavenVersion, "installer"), null,
                new LibrariesDownloadInfo(new LibraryDownloadInfo(
                        "optifine/OptiFine/" + mavenVersion + "/OptiFine-" + mavenVersion + "-installer.jar",
                        remote.getUrls().get(0).toString()))
        );
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        Path installerFile = Files.createTempFile("optifine-installer", ".jar");
        dest = installerFile;

        if (installer == null) {
            var task = new FileDownloadTask(
                    dependencyManager.getDownloadProvider().injectURLsWithCandidates(remote.getUrls()),
                    installerFile, null);
            task.setCacheRepository(dependencyManager.getCacheRepository());
            task.setCaching(true);
            dependents.add(task);
        } else {
            FileUtils.copyFile(installer, installerFile);
        }
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isRelyingOnDependencies() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        if (!Files.isRegularFile(minecraftJar)) {
            throw new IOException("Minecraft client JAR not found: " + minecraftJar);
        }
        Path installerFile = Objects.requireNonNull(dest);
        String originalMainClass = dependencyManager.getGameRepository().resolve(manifest).launchManifest().mainClass();
        if (!GameComponentAnalyzer.FORGE_OPTIFINE_MAIN.contains(originalMainClass))
            throw new UnsupportedInstallationException(UnsupportedInstallationException.UNSUPPORTED_LAUNCH_WRAPPER);

        List<Library> libraries = new ArrayList<>(4);
        libraries.add(optiFineLibrary);

        Path optiFineInstallerLibraryPath = gameRepository.getLayout().getLibraryFile(manifest.id(), optiFineInstallerLibrary);
        FileUtils.copyFile(installerFile, optiFineInstallerLibraryPath);

        try (FileSystem fs2 = CompressingUtils.createWritableZipFileSystem(optiFineInstallerLibraryPath)) {
            Files.deleteIfExists(fs2.getPath("/META-INF/mods.toml"));
        }

        // Install launch wrapper modified by OptiFine
        boolean hasLaunchWrapper = false;
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installerFile)) {
            Path optiFineLibraryPath = gameRepository.getLayout().getLibraryFile(manifest.id(), optiFineLibrary);
            if (Files.exists(fs.getPath("optifine/Patcher.class"))) {
                String[] command = {
                        JavaRuntime.getDefault().getBinary().toString(),
                        "-cp",
                        installerFile.toString(),
                        "optifine.Patcher",
                        minecraftJar.toAbsolutePath().normalize().toString(),
                        installerFile.toString(),
                        optiFineLibraryPath.toString()
                };
                int exitCode = SystemUtils.callExternalProcess(command);
                if (exitCode != 0)
                    throw new IOException("OptiFine patcher failed, command: " + new CommandBuilder().addAll(Arrays.asList(command)));
            } else {
                FileUtils.copyFile(installerFile, optiFineLibraryPath);
            }

            try (FileSystem fs2 = CompressingUtils.createWritableZipFileSystem(optiFineLibraryPath)) {
                Files.deleteIfExists(fs2.getPath("/META-INF/mods.toml"));
            }

            Path launchWrapper2 = fs.getPath("launchwrapper-2.0.jar");
            if (Files.exists(launchWrapper2)) {
                Library launchWrapper = new Library(new Artifact("optifine", "launchwrapper", "2.0"));
                Path launchWrapperFile = gameRepository.getLayout().getLibraryFile(manifest.id(), launchWrapper);
                Files.createDirectories(launchWrapperFile.toAbsolutePath().getParent());
                FileUtils.copyFile(launchWrapper2, launchWrapperFile);
                hasLaunchWrapper = true;
                libraries.add(launchWrapper);
            }

            Path launchWrapperVersionText = fs.getPath("launchwrapper-of.txt");
            if (Files.exists(launchWrapperVersionText)) {
                String launchWrapperVersion = Files.readString(launchWrapperVersionText).trim();
                Path launchWrapperJar = fs.getPath("launchwrapper-of-" + launchWrapperVersion + ".jar");

                Library launchWrapper = new Library(new Artifact("optifine", "launchwrapper-of", launchWrapperVersion));

                if (Files.exists(launchWrapperJar)) {
                    Path launchWrapperFile = gameRepository.getLayout().getLibraryFile(manifest.id(), launchWrapper);
                    Files.createDirectories(launchWrapperFile.toAbsolutePath().getParent());
                    FileUtils.copyFile(launchWrapperJar, launchWrapperFile);

                    hasLaunchWrapper = true;
                    libraries.add(launchWrapper);
                }
            }

            Path buildofText = fs.getPath("buildof.txt");
            if (Files.exists(buildofText)) {
                String buildof = Files.readString(buildofText).trim();
                VersionNumber buildofVer = VersionNumber.asVersion(buildof);

                if (GameComponentAnalyzer.BOOTSTRAP_LAUNCHER_MAIN.equals(originalMainClass)) {
                    // OptiFine H1 Pre2+ is compatible with Forge 1.17
                    if (buildofVer.compareTo("20210924-190833") < 0) {
                        throw new UnsupportedInstallationException(UnsupportedInstallationException.FORGE_1_17_OPTIFINE_H1_PRE2);
                    }
                }
            }
        }

        if (!hasLaunchWrapper) {
            libraries.add(new Library(new Artifact("net.minecraft", "launchwrapper", "1.12")));
        }

        setResult(new GameInstancePatch(
                GameComponentType.OPTIFINE.getPatchId(),
                remote.getSelfVersion(),
                10000,
                new Arguments().addGameArguments("--tweakClass", "optifine.OptiFineTweaker"),
                GameComponentAnalyzer.LAUNCH_WRAPPER_MAIN,
                libraries
        ));

        dependencies.add(new org.jackhuang.hmcl.download.game.GameLibrariesTask(dependencyManager, manifest, true, getResult().getLibraries()));
    }

    /// Creates a task that installs OptiFine from a local installer JAR.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest           working manifest receiving the OptiFine patch
    /// @param gameVersion       Minecraft version expected by the installation
    /// @param installer         the OptiFine installer JAR
    /// @return the task producing the OptiFine patch
    /// @throws IOException              if the installer is malformed or unsupported
    /// @throws VersionMismatchException if the installer targets another Minecraft version
    public static Task<GameInstancePatch> install(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            String gameVersion,
            Path installer) throws IOException, VersionMismatchException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            Path configClass = fs.getPath("Config.class");
            if (!Files.exists(configClass)) configClass = fs.getPath("net/optifine/Config.class");
            if (!Files.exists(configClass)) configClass = fs.getPath("notch/net/optifine/Config.class");
            if (!Files.exists(configClass)) throw new IOException("Unrecognized installer");
            ConstantPool pool = ConstantPoolScanner.parse(Files.readAllBytes(configClass), ConstantType.UTF8);
            List<String> constants = new ArrayList<>();
            pool.list(Utf8Constant.class).forEach(utf8 -> constants.add(utf8.get()));
            String mcVersion = getOrDefault(constants, constants.indexOf("MC_VERSION") + 1, null);
            String ofEdition = getOrDefault(constants, constants.indexOf("OF_EDITION") + 1, null);
            String ofRelease = getOrDefault(constants, constants.indexOf("OF_RELEASE") + 1, null);

            if (mcVersion == null || ofEdition == null || ofRelease == null)
                throw new IOException("Unrecognized OptiFine installer");

            if (!mcVersion.equals(gameVersion))
                throw new VersionMismatchException(mcVersion, gameVersion);

            OptiFineRemoteVersion remoteVersion = new OptiFineRemoteVersion(
                    mcVersion,
                    ofEdition + "_" + ofRelease,
                    Collections.singletonList(""),
                    false);
            return new GameDownloadTask(dependencyManager, manifest)
                    .thenComposeAsync(minecraftJar -> new OptiFineInstallTask(
                            dependencyManager,
                            manifest,
                            remoteVersion,
                            minecraftJar,
                            installer));
        }
    }
}
