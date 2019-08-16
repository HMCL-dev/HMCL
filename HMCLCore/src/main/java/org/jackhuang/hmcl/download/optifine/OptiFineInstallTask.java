/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.game.LibrariesDownloadInfo;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.LibraryDownloadInfo;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jenkinsci.constant_pool_scanner.ConstantPool;
import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.ConstantType;
import org.jenkinsci.constant_pool_scanner.Utf8Constant;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.util.Lang.getOrDefault;

/**
 * <b>Note</b>: OptiFine should be installed in the end.
 *
 * @author huangyuhui
 */
public final class OptiFineInstallTask extends Task<Version> {

    private final DefaultDependencyManager dependencyManager;
    private final Version version;
    private final OptiFineRemoteVersion remote;
    private final Path installer;
    private final List<Task<?>> dependents = new LinkedList<>();
    private final List<Task<?>> dependencies = new LinkedList<>();
    private final File dest;

    private final Library optiFineLibrary;

    public OptiFineInstallTask(DefaultDependencyManager dependencyManager, Version version, OptiFineRemoteVersion remoteVersion) {
        this(dependencyManager, version, remoteVersion, null);
    }

    public OptiFineInstallTask(DefaultDependencyManager dependencyManager, Version version, OptiFineRemoteVersion remoteVersion, Path installer) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.remote = remoteVersion;
        this.installer = installer;

        String mavenVersion = remote.getGameVersion() + "_" + remote.getSelfVersion();

        optiFineLibrary = new Library(
                "optifine", "OptiFine", mavenVersion, null, null,
                new LibrariesDownloadInfo(new LibraryDownloadInfo(
                        "optifine/OptiFine/" + mavenVersion + "/OptiFine-" + mavenVersion + ".jar",
                        remote.getUrl()))
        );

        dest = dependencyManager.getGameRepository().getLibraryFile(version, optiFineLibrary);
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        if (!Arrays.asList("net.minecraft.client.main.Main",
                "net.minecraft.launchwrapper.Launch")
                .contains(version.resolve(dependencyManager.getGameRepository()).getMainClass()))
            throw new UnsupportedOptiFineInstallationException();


        if (installer == null) {
            dependents.add(new FileDownloadTask(NetworkUtils.toURL(remote.getUrl()), dest)
                    .setCacheRepository(dependencyManager.getCacheRepository())
                    .setCaching(true));
        } else {
            FileUtils.copyFile(installer, dest.toPath());
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
    public void execute() throws IOException {
        List<Library> libraries = new LinkedList<>();
        libraries.add(optiFineLibrary);

        // Install launch wrapper modified by OptiFine
        boolean hasLaunchWrapper = false;
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(dest.toPath())) {
            Path launchWrapperVersionText = fs.getPath("launchwrapper-of.txt");
            if (Files.exists(launchWrapperVersionText)) {
                String launchWrapperVersion = FileUtils.readText(launchWrapperVersionText).trim();
                Path launchWrapperJar = fs.getPath("launchwrapper-of-" + launchWrapperVersion + ".jar");

                Library launchWrapper = new Library("optifine", "launchwrapper-of", launchWrapperVersion);

                if (Files.exists(launchWrapperJar)) {
                    File launchWrapperFile = dependencyManager.getGameRepository().getLibraryFile(version, launchWrapper);
                    FileUtils.makeDirectory(launchWrapperFile.getAbsoluteFile().getParentFile());
                    FileUtils.copyFile(launchWrapperJar, launchWrapperFile.toPath());

                    hasLaunchWrapper = true;
                    libraries.add(0, launchWrapper);
                }
            }
        }

        if (!hasLaunchWrapper) {
            libraries.add(0, new Library("net.minecraft", "launchwrapper", "1.12"));
        }

        setResult(new Version(
                LibraryAnalyzer.LibraryType.OPTIFINE.getPatchId(),
                remote.getSelfVersion(),
                90000,
                new Arguments().addGameArguments("--tweakClass", "optifine.OptiFineTweaker"),
                "net.minecraft.launchwrapper.Launch",
                libraries
        ));

        dependencies.add(dependencyManager.checkLibraryCompletionAsync(version.setLibraries(libraries)));
    }

    public static class UnsupportedOptiFineInstallationException extends UnsupportedOperationException {
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
            ConstantPool pool = ConstantPoolScanner.parse(Files.readAllBytes(fs.getPath("Config.class")), ConstantType.UTF8);
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
                    new OptiFineRemoteVersion(mcVersion,  ofEdition + "_" + ofRelease, () -> null, false), installer);
        }
    }
}
