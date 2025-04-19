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
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
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
    private final Map<String, GetTask> componentOriginalPatch = new HashMap<>();

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
                String componentID = component.getUid();

                String version = component.getVersion();
                if (version == null) {
                    // https://github.com/MultiMC/Launcher/blob/develop/launcher/minecraft/ComponentUpdateTask.cpp#L586-L602
                    labelSwitch:
                    switch (componentID) {
                        case "org.lwjgl": {
                            version = "2.9.1";
                            break;
                        }
                        case "org.lwjgl3": {
                            version = "3.1.2";
                            break;
                        }
                        case "net.fabricmc.intermediary":
                        case "org.quiltmc.hashed": {
                            for (MultiMCManifest.MultiMCManifestComponent c : manifest.getMmcPack().getComponents()) {
                                if (MultiMCComponents.getComponent(c.getUid()) == LibraryAnalyzer.LibraryType.MINECRAFT) {
                                    version = Objects.requireNonNull(c.getVersion(), "Version of Minecraft must be specific.");
                                    break labelSwitch;
                                }
                            }
                            break;
                        }
                    }
                }

                if (version != null) {
                    List<URL> urls = new ArrayList<>(MultiMCComponents.META.length);
                    for (String s : MultiMCComponents.META) {
                        urls.add(NetworkUtils.toURL(String.format(s, componentID, version)));
                    }

                    GetTask task = new GetTask(urls);

                    componentOriginalPatch.put(componentID, task);
                    dependents.add(task);

                    LibraryAnalyzer.LibraryType type = MultiMCComponents.getComponent(componentID);
                    if (type != null) {
                        builder.version(type.getPatchId(), version);
                    }
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
        Map<String, Pair<Version, MultiMCInstancePatch>> components = new HashMap<>();

        for (Map.Entry<String, GetTask> entry : componentOriginalPatch.entrySet()) {
            String componentID = entry.getKey();
            String patchJson = Objects.requireNonNull(entry.getValue().getResult());

            if (components.put(componentID, Pair.pair(convertPatchToVersion(
                    readPatch(patchJson), componentID), null
            )) != null) {
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

                            Pair<?, MultiMCInstancePatch> pair = components.computeIfAbsent(patchID, p -> Pair.pair(null, null));
                            if (pair.setValue(readPatch(FileUtils.readText(patchJson))) != null) {
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
            Pair<Version, ?> pair = components.get(MultiMCComponents.getComponent(LibraryAnalyzer.LibraryType.MINECRAFT));

            Version mc = pair.getKey();
            if (mc.getArguments().map(Arguments::getJvm).map(List::isEmpty).orElse(true)) {
                pair.setKey(mc.setArguments(new Arguments(null, Arguments.DEFAULT_JVM_ARGUMENTS)));
            }
        }

        // Rearrange all patches.

        Version artifact = null;
        try (FileSystem mc = CompressingUtils.writable(
                repository.getVersionRoot(name).toPath().resolve(name + ".jar")
        ).setAutoDetectEncoding(true).build()) {
            for (MultiMCManifest.MultiMCManifestComponent component : manifest.getMmcPack().getComponents()) {
                String componentID = component.getUid();

                Pair<Version, MultiMCInstancePatch> pair = components.get(componentID);
                if (pair == null) {
                    throw new IllegalArgumentException("No such component: " + componentID);
                }

                Version original = pair.getKey();
                MultiMCInstancePatch jp = pair.getValue();
                if (jp != null && !jp.getJarMods().isEmpty()) {
                    // JarMod. Merge it into minecraft.jar
                    if (original != null || !componentID.startsWith("org.multimc.jarmod.")) {
                        throw new IllegalArgumentException("Illegal jar mod: " + componentID);
                    }

                    try (FileSystem jm = CompressingUtils.readonly(repository.getVersionRoot(name).toPath().resolve(
                            "jarmods/" + StringUtils.removePrefix(componentID, "org.multimc.jarmod.") + ".jar"
                    )).setAutoDetectEncoding(true).build()) {
                        FileUtils.copyDirectory(jm.getPath("/"), mc.getPath("/"));
                    }
                } else {
                    Version tc, pp = jp == null ? null : convertPatchToVersion(jp, componentID);

                    if (original == null) {
                        tc = Objects.requireNonNull(pp, "Original and Json-Patch shouldn't be empty at the same time.");
                    } else if (jp != null) {
                        tc = pp.merge(original, true, Version.ONLY_THIS);
                    } else {
                        tc = original;
                    }

                    artifact = artifact == null ? tc : tc.merge(artifact, true, Version.THAT_FIRST);
                }
            }
        }

        // Erase all patches info to reject any modification to MultiMC mod packs.
        artifact = Objects.requireNonNull(artifact, "There should be at least one component.")
                .setPatches(null).setId(name).setJar(name).setRoot(null);

        dependencies.add(repository.saveAsync(artifact));
    }

    private MultiMCInstancePatch readPatch(String patchJson) {
        MultiMCInstancePatch patch;
        try {
            patch = JsonUtils.GSON.fromJson(patchJson, MultiMCInstancePatch.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Cannot parse MultiMC patch json: " + patchJson, e);
        }
        return patch;
    }

    private Version convertPatchToVersion(MultiMCInstancePatch patch, String patchID) {
        List<String> arguments = new ArrayList<>();
        for (String arg : patch.getTweakers()) {
            arguments.add("--tweakClass");
            arguments.add(arg);
        }

        Version version = new Version(patchID)
                .setVersion(patch.getVersion())
                .setArguments(new Arguments().addGameArguments(arguments).addJVMArguments(patch.getJvmArgs()))
                .setMainClass(patch.getMainClass())
                .setMinecraftArguments(patch.getMinecraftArguments())
                .setLibraries(patch.getLibraries());

        // Workaround: Official Version Json can only store one GameJavaVersion, not a array of all suitable java versions.
        // For compatibility with official launcher and any other launchers,
        // a transform is made between int[] and GameJavaVersion.
        int[] majors = patch.getJavaMajors();
        if (majors != null) {
            majors = majors.clone();
            Arrays.sort(majors);

            for (int i = majors.length - 1; i >= 0; i--) {
                GameJavaVersion jv = GameJavaVersion.get(majors[i]);
                if (jv != null) {
                    version = version.setJavaVersion(jv);
                    break;
                }
            }
        }

        return version;
    }
}
