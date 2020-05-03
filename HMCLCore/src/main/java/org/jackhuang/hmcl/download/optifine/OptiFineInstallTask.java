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
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jenkinsci.constant_pool_scanner.ConstantPool;
import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.ConstantType;
import org.jenkinsci.constant_pool_scanner.Utf8Constant;

import java.io.File;
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
public final class OptiFineInstallTask extends Task<Version> {

    private final DefaultGameRepository gameRepository;
    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private final OptiFineRemoteVersion remote;
    private final Path installer;
    private final List<Task<?>> dependents = new LinkedList<>();
    private final List<Task<?>> dependencies = new LinkedList<>();
    private Path dest;

    private final Library optiFineLibrary;
    private final Library optiFineInstallerLibrary;

    public OptiFineInstallTask(DefaultDependencyManager dependencyManager, Version version, OptiFineRemoteVersion remoteVersion) {
        this(dependencyManager, version, remoteVersion, null);
    }

    public OptiFineInstallTask(DefaultDependencyManager dependencyManager, Version version, OptiFineRemoteVersion remoteVersion, Path installer) {
        this.dependencyManager = dependencyManager;
        this.gameRepository = dependencyManager.getGameRepository();
        this.version = version;
        this.remote = remoteVersion;
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
        dest = Files.createTempFile("optifine-installer", ".jar");

        if (installer == null) {
            FileDownloadTask task = new FileDownloadTask(
                    dependencyManager.getDownloadProvider().injectURLsWithCandidates(remote.getUrls()),
                    dest.toFile(), null);
            task.setCacheRepository(dependencyManager.getCacheRepository());
            task.setCaching(true);
            dependents.add(task);
        } else {
            FileUtils.copyFile(installer, dest);
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
        String originalMainClass = version.resolve(dependencyManager.getGameRepository()).getMainClass();
        if (!LibraryAnalyzer.VANILLA_MAIN.equals(originalMainClass) && !LibraryAnalyzer.LAUNCH_WRAPPER_MAIN.equals(originalMainClass) && !LibraryAnalyzer.MOD_LAUNCHER_MAIN.equals(originalMainClass))
            throw new OptiFineInstallTask.UnsupportedOptiFineInstallationException();

        List<Library> libraries = new LinkedList<>();
        libraries.add(optiFineLibrary);

        FileUtils.copyFile(dest, gameRepository.getLibraryFile(version, optiFineInstallerLibrary).toPath());

        // Install launch wrapper modified by OptiFine
        boolean hasLaunchWrapper = false;
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(dest)) {
            if (Files.exists(fs.getPath("optifine/Patcher.class"))) {
                int exitCode = SystemUtils.callExternalProcess(
                        JavaVersion.fromCurrentEnvironment().getBinary().toString(),
                        "-cp",
                        dest.toString(),
                        "optifine.Patcher",
                        gameRepository.getVersionJar(version).getAbsolutePath(),
                        dest.toString(),
                        gameRepository.getLibraryFile(version, optiFineLibrary).toString()
                );
                if (exitCode != 0)
                    throw new IOException("OptiFine patcher failed");
            } else {
                FileUtils.copyFile(dest, gameRepository.getLibraryFile(version, optiFineLibrary).toPath());
            }

            Path launchWrapper2 = fs.getPath("launchwrapper-2.0.jar");
            if (Files.exists(launchWrapper2)) {
                Library launchWrapper = new Library(new Artifact("optifine", "launchwrapper", "2.0"));
                File launchWrapperFile = gameRepository.getLibraryFile(version, launchWrapper);
                FileUtils.makeDirectory(launchWrapperFile.getAbsoluteFile().getParentFile());
                FileUtils.copyFile(launchWrapper2, launchWrapperFile.toPath());
                hasLaunchWrapper = true;
                libraries.add(launchWrapper);
            }

            Path launchWrapperVersionText = fs.getPath("launchwrapper-of.txt");
            if (Files.exists(launchWrapperVersionText)) {
                String launchWrapperVersion = FileUtils.readText(launchWrapperVersionText).trim();
                Path launchWrapperJar = fs.getPath("launchwrapper-of-" + launchWrapperVersion + ".jar");

                Library launchWrapper = new Library(new Artifact("optifine", "launchwrapper-of", launchWrapperVersion));

                if (Files.exists(launchWrapperJar)) {
                    File launchWrapperFile = gameRepository.getLibraryFile(version, launchWrapper);
                    FileUtils.makeDirectory(launchWrapperFile.getAbsoluteFile().getParentFile());
                    FileUtils.copyFile(launchWrapperJar, launchWrapperFile.toPath());

                    hasLaunchWrapper = true;
                    libraries.add(launchWrapper);
                }
            }
        }

        if (!hasLaunchWrapper) {
            libraries.add(new Library(new Artifact("net.minecraft", "launchwrapper", "1.12")));
        }

        setResult(new Version(
                LibraryAnalyzer.LibraryType.OPTIFINE.getPatchId(),
                remote.getSelfVersion(),
                10000,
                new Arguments().addGameArguments("--tweakClass", "optifine.OptiFineTweaker"),
                LibraryAnalyzer.LAUNCH_WRAPPER_MAIN,
                libraries
        ));

        dependencies.add(dependencyManager.checkLibraryCompletionAsync(getResult(), true));
    }

    public static class UnsupportedOptiFineInstallationException extends Exception {
    }

    /**
     * Install OptiFine library from existing local file.
     *
     * @param dependencyManager game repository
     * @param version version.json
     * @param installer the OptiFine installer
     * @return the task to install library
     * @throws IOException if unable to read compressed content of installer file, or installer file is corrupted, or the installer is not the one we want.
     * @throws VersionMismatchException if required game version of installer does not match the actual one.
     */
    public static Task<Version> install(DefaultDependencyManager dependencyManager, Version version, Path installer) throws IOException, VersionMismatchException {
        File jar = dependencyManager.getGameRepository().getVersionJar(version);
        Optional<String> gameVersion = GameVersion.minecraftVersion(jar);
        if (!gameVersion.isPresent()) throw new IOException();
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            Path configClass = fs.getPath("Config.class");
            if (!Files.exists(configClass)) configClass = fs.getPath("net/optifine/Config.class");
            if (!Files.exists(configClass)) throw new IOException("Unrecognized installer");
            ConstantPool pool = ConstantPoolScanner.parse(Files.readAllBytes(configClass), ConstantType.UTF8);
            List<String> constants = new ArrayList<>();
            pool.list(Utf8Constant.class).forEach(utf8 -> constants.add(utf8.get()));
            String mcVersion = getOrDefault(constants, constants.indexOf("MC_VERSION") + 1, null);
            String ofEdition = getOrDefault(constants, constants.indexOf("OF_EDITION") + 1, null);
            String ofRelease = getOrDefault(constants, constants.indexOf("OF_RELEASE") + 1, null);

            if (mcVersion == null || ofEdition == null || ofRelease == null)
                throw new IOException("Unrecognized OptiFine installer");

            if (!mcVersion.equals(gameVersion.get()))
                throw new VersionMismatchException(mcVersion, gameVersion.get());

            return new OptiFineInstallTask(dependencyManager, version,
                    new OptiFineRemoteVersion(mcVersion,  ofEdition + "_" + ofRelease, Collections.singletonList(""), false), installer);
        }
    }
}
