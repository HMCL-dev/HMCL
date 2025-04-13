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
package org.jackhuang.hmcl.mod.multimc;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.mod.MinecraftInstanceTask;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.ModpackInstallTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author huangyuhui
 */
public final class MultiMCModpackInstallTask extends Task<Void> {

    private final File zipFile;
    private final Modpack modpack;
    private final MultiMCInstanceConfiguration manifest;
    private final String name;
    private final DefaultGameRepository repository;
    private final List<Task<?>> dependencies = new ArrayList<>(1);
    private final List<Task<?>> dependents = new ArrayList<>(4);

    public MultiMCModpackInstallTask(DefaultDependencyManager dependencyManager, File zipFile, Modpack modpack, MultiMCInstanceConfiguration manifest, String name) {
        this.zipFile = zipFile;
        this.modpack = modpack;
        this.manifest = manifest;
        this.name = name;
        this.repository = dependencyManager.getGameRepository();

        File json = repository.getModpackConfiguration(name);
        if (repository.hasVersion(name) && !json.exists())
            throw new IllegalArgumentException("Version " + name + " already exists.");

        GameBuilder builder = dependencyManager.gameBuilder().name(name).gameVersion(manifest.getGameVersion());

        if (manifest.getMmcPack() != null) {
            for (MultiMCManifest.MultiMCManifestComponent component : manifest.getMmcPack().getComponents()) {
                LibraryAnalyzer.LibraryType type = MultiMCComponents.getComponent(component.getUid());
                String version = component.getVersion();
                if (type != null && version != null) {
                    builder.version(type.getPatchId(), version);
                }
            }
        }

        dependents.add(builder.buildAsync());
        onDone().register(event -> {
            if (event.isFailed())
                repository.removeVersionFromDisk(name);
        });
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        File run = repository.getRunDirectory(name);
        File json = repository.getModpackConfiguration(name);

        ModpackConfiguration<MultiMCInstanceConfiguration> config = null;
        try {
            if (json.exists()) {
                config = JsonUtils.GSON.fromJson(FileUtils.readText(json), ModpackConfiguration.typeOf(MultiMCInstanceConfiguration.class));

                if (!MultiMCModpackProvider.INSTANCE.getName().equals(config.getType()))
                    throw new IllegalArgumentException("Version " + name + " is not a MultiMC modpack. Cannot update this version.");
            }
        } catch (JsonParseException | IOException ignore) {
        }

        String subDirectory;

        try (FileSystem fs = CompressingUtils.readonly(zipFile.toPath()).setEncoding(modpack.getEncoding()).build()) {
            // /.minecraft
            if (Files.exists(fs.getPath("/.minecraft"))) {
                subDirectory = "/.minecraft";
                // /minecraft
            } else if (Files.exists(fs.getPath("/minecraft"))) {
                subDirectory = "/minecraft";
                // /[name]/.minecraft
            } else if (Files.exists(fs.getPath("/" + manifest.getName() + "/.minecraft"))) {
                subDirectory = "/" + manifest.getName() + "/.minecraft";
                // /[name]/minecraft
            } else if (Files.exists(fs.getPath("/" + manifest.getName() + "/minecraft"))) {
                subDirectory = "/" + manifest.getName() + "/minecraft";
            } else {
                subDirectory = "/" + manifest.getName() + "/.minecraft";
            }
        }

        dependents.add(new ModpackInstallTask<>(zipFile, run, modpack.getEncoding(), Collections.singletonList(subDirectory), any -> true, config).withStage("hmcl.modpack"));
        dependents.add(new MinecraftInstanceTask<>(zipFile, modpack.getEncoding(), Collections.singletonList(subDirectory), manifest, MultiMCModpackProvider.INSTANCE, manifest.getName(), null, repository.getModpackConfiguration(name)).withStage("hmcl.modpack"));
    }

    @Override
    public List<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public void execute() throws Exception {
        // componentID -> <default, user patch>
        Map<String, Pair<Version, Version>> components = new HashMap<>();

        for (Version patch : repository.readVersionJson(name).getPatches()) {
            LibraryAnalyzer.LibraryType libraryType = LibraryAnalyzer.LibraryType.fromPatchId(patch.getId());
            if (libraryType == null) {
                throw new IllegalArgumentException("Unknown library: " + patch.getId());
            }

            String componentID = MultiMCComponents.getComponent(libraryType);
            if (componentID == null) {
                throw new IllegalArgumentException("Unknown library type: " + libraryType);
            }

            if (components.put(componentID, Pair.pair(patch, null)) != null) {
                throw new IllegalArgumentException("Duplicate libraries: " + componentID);
            }
        }

        try (FileSystem fs = CompressingUtils.readonly(zipFile.toPath()).setAutoDetectEncoding(true).build()) {
            Path root = MultiMCModpackProvider.getRootPath(fs.getPath("/"));
            Path patches = root.resolve("patches");

            if (Files.exists(patches)) {
                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(patches)) {
                    for (Path patchJson : directoryStream) {
                        if (patchJson.toString().endsWith(".json")) {
                            String patchID = FileUtils.getNameWithoutExtension(patchJson);
                            MultiMCInstancePatch multiMCPatch;

                            try {
                                multiMCPatch = JsonUtils.GSON.fromJson(FileUtils.readText(patchJson), MultiMCInstancePatch.class);
                            } catch (JsonParseException e) {
                                throw new IllegalArgumentException("Cannot parse MultiMC patch json: " + patchJson, e);
                            }

                            List<String> arguments = new ArrayList<>();
                            for (String arg : multiMCPatch.getTweakers()) {
                                arguments.add("--tweakClass");
                                arguments.add(arg);
                            }

                            Version patch = new Version(
                                    patchID, multiMCPatch.getVersion(), multiMCPatch.getOrder(),
                                    new Arguments().addGameArguments(arguments).addJVMArguments(multiMCPatch.getJvmArgs()), multiMCPatch.getMainClass(),
                                    multiMCPatch.getLibraries()
                            );

                            int[] majors = multiMCPatch.getJavaMajors();
                            if (majors != null) {
                                majors = majors.clone();
                                Arrays.sort(majors);

                                for (int i = majors.length - 1; i >= 0; i--) {
                                    GameJavaVersion jv = GameJavaVersion.get(majors[i]);
                                    if (jv != null) {
                                        patch = patch.setJavaVersion(jv);
                                        break;
                                    }
                                }
                            }

                            Pair<Version, Version> pair = components.computeIfAbsent(patchID, p -> Pair.pair(null, null));
                            if (pair.setValue(patch) != null) {
                                throw new IllegalArgumentException("Duplicate user patch: " + patchID);
                            }
                        }
                    }
                }
            }

            Path libraries = root.resolve("libraries");
            if (Files.exists(libraries))
                FileUtils.copyDirectory(libraries, repository.getVersionRoot(name).toPath().resolve("libraries"));

            Path jarmods = root.resolve("jarmods");
            if (Files.exists(jarmods))
                FileUtils.copyDirectory(jarmods, repository.getVersionRoot(name).toPath().resolve("jarmods"));

            String iconKey = this.manifest.getIconKey();
            if (iconKey != null) {
                Path iconFile = root.resolve(iconKey + ".png");
                if (Files.exists(iconFile)) {
                    FileUtils.copyFile(iconFile, repository.getVersionRoot(name).toPath().resolve("icon.png"));
                }
            }
        }

        // If $.minecraftArguments exist, write default VM arguments into $.patches[name=game].arguments.jvm for compatibility.
        // See org.jackhuang.hmcl.game.VersionLibraryBuilder::build

        {
            Pair<Version, Version> pair = components.get(MultiMCComponents.getComponent(LibraryAnalyzer.LibraryType.MINECRAFT));

            Version mc = pair.getKey();
            if (mc.getMinecraftArguments().isPresent() && mc.getArguments().map(Arguments::getJvm).map(List::isEmpty).orElse(true)) {
                pair.setKey(mc.setArguments(new Arguments(null, Arguments.DEFAULT_JVM_ARGUMENTS)));
            }
        }

        // Rearrange all patches.

        Version artifact = null;
        for (MultiMCManifest.MultiMCManifestComponent component : manifest.getMmcPack().getComponents()) {
            String componentID = component.getUid();

            Pair<Version, Version> pair = components.get(componentID);
            if (pair == null) {
                throw new IllegalArgumentException("No such component: " + componentID);
            }

            Version original = pair.getKey(), jp = pair.getValue(), tc;
            if (original == null) {
                tc = Objects.requireNonNull(jp, "Original and Json-Patch shouldn't be empty at the same time.");
            } else {
                if (jp != null) {
                    original = jp.merge(original, true, Version.ONLY_THIS);
                }
                tc = original;
            }

            artifact = artifact == null ? tc : tc.merge(artifact, true, Version.THAT_FIRST);
        }

        // Erase all patches info to reject any modification to MultiMC mod packs.
        artifact = Objects.requireNonNull(artifact, "There should be at least one component.")
                .setPatches(null).setId(name).setJar(name).setRoot(null);

        dependencies.add(repository.saveAsync(artifact));
    }
}
