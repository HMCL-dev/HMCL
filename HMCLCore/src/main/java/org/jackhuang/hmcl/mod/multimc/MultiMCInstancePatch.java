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
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.Argument;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.AssetIndexInfo;
import org.jackhuang.hmcl.game.CompatibilityRule;
import org.jackhuang.hmcl.game.DownloadType;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.OSRestriction;
import org.jackhuang.hmcl.game.RuledArgument;
import org.jackhuang.hmcl.game.StringArgument;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonMap;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author huangyuhui
 */
@Immutable
public final class MultiMCInstancePatch {
    public static final Library BOOTSTRAP_LIBRARY = new Library(new Artifact("org.jackhuang.hmcl", "mmc-bootstrap", "1.0"));

    private final int formatVersion;

    @SerializedName("uid")
    private final String id;

    @SerializedName("version")
    private final String version;

    @SerializedName("assetIndex")
    private final AssetIndexInfo assetIndex;

    @SerializedName("minecraftArguments")
    private final String minecraftArguments;

    @SerializedName("+jvmArgs")
    private final List<String> jvmArgs;

    @SerializedName("mainClass")
    private final String mainClass;

    @SerializedName("compatibleJavaMajors")
    private final int[] javaMajors;

    @SerializedName("mainJar")
    private final Library mainJar;

    @SerializedName("+traits")
    private final List<String> traits;

    @SerializedName("+tweakers")
    private final List<String> tweakers;

    @SerializedName(value = "+libraries")
    private final List<Library> libraries0;
    @SerializedName(value = "libraries")
    private final List<Library> libraries1;
    @SerializedName(value = "mavenFiles")
    private final List<Library> mavenFiles;

    @SerializedName("jarMods")
    private final List<Library> jarMods;

    @SerializedName("requires")
    private final List<MultiMCManifest.MultiMCManifestCachedRequires> requires;

    public MultiMCInstancePatch(int formatVersion, String id, String version, AssetIndexInfo assetIndex, String minecraftArguments, List<String> jvmArgs, String mainClass, int[] javaMajors, Library mainJar, List<String> traits, List<String> tweakers, List<Library> libraries0, List<Library> libraries1, List<Library> mavenFiles, List<Library> jarMods, List<MultiMCManifest.MultiMCManifestCachedRequires> requires) {
        this.formatVersion = formatVersion;
        this.id = id;
        this.version = version;
        this.assetIndex = assetIndex;
        this.minecraftArguments = minecraftArguments;
        this.jvmArgs = jvmArgs;
        this.mainClass = mainClass;
        this.javaMajors = javaMajors;
        this.mainJar = mainJar;
        this.traits = traits;
        this.tweakers = tweakers;
        this.libraries0 = libraries0;
        this.libraries1 = libraries1;
        this.mavenFiles = mavenFiles;
        this.jarMods = jarMods;
        this.requires = requires;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public String getID() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public AssetIndexInfo getAssetIndex() {
        return assetIndex;
    }

    public String getMinecraftArguments() {
        return minecraftArguments;
    }

    public List<String> getJvmArgs() {
        return nonNullOrEmpty(jvmArgs);
    }

    public String getMainClass() {
        return mainClass;
    }

    public int[] getJavaMajors() {
        return javaMajors;
    }

    public Library getMainJar() {
        return mainJar;
    }

    public List<String> getTraits() {
        return nonNullOrEmpty(traits);
    }

    public List<String> getTweakers() {
        return nonNullOrEmpty(tweakers);
    }

    public List<Library> getLibraries() {
        List<Library> list = new ArrayList<>();
        if (libraries0 != null) {
            list.addAll(libraries0);
        }
        if (libraries1 != null) {
            list.addAll(libraries1);
        }
        return nonNullOrEmpty(list);
    }

    public List<Library> getMavenOnlyFiles() {
        return nonNullOrEmpty(mavenFiles);
    }

    public List<Library> getJarMods() {
        return nonNullOrEmpty(jarMods);
    }

    public List<MultiMCManifest.MultiMCManifestCachedRequires> getRequires() {
        return nonNullOrEmpty(requires);
    }

    private static <T> List<T> nonNullOrEmpty(List<T> value) {
        return value != null && !value.isEmpty() ? value : Collections.emptyList();
    }

    private static <T> List<T> dropDuplicate(List<T> original) {
        // TODO: Maybe new ArrayList(new LinkedHashSet(original)) ?

        Set<T> values = new HashSet<>();
        List<T> result = new ArrayList<>();

        for (T item : original) {
            if (values.add(item)) {
                result.add(item);
            }
        }

        return result;
    }

    public static MultiMCInstancePatch read(String componentID, String text) {
        try {
            return JsonUtils.fromNonNullJson(text, MultiMCInstancePatch.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Illegal Json-Patch: " + componentID);
        }
    }

    public static final class ResolvedInstance {
        private final Version version;

        private final String gameVersion;

        private final Library mainJar;

        private final List<String> jarModFileNames;
        private final List<Library> mavenOnlyFiles;

        public ResolvedInstance(Version version, String gameVersion, Library mainJar, List<String> jarModFileNames, List<Library> mavenOnlyFiles) {
            this.version = version;
            this.gameVersion = gameVersion;
            this.mainJar = mainJar;
            this.jarModFileNames = jarModFileNames;
            this.mavenOnlyFiles = mavenOnlyFiles;
        }

        public Version getVersion() {
            return version;
        }

        public String getGameVersion() {
            return gameVersion;
        }

        public Library getMainJar() {
            return mainJar;
        }

        public List<String> getJarModFileNames() {
            return jarModFileNames;
        }

        public List<Library> getMavenOnlyFiles() {
            return mavenOnlyFiles;
        }
    }

    /**
     * <p>Core methods transforming MultiMCModpack to Official Version Scheme.</p>
     *
     * <p>Most of the information can be transformed in a lossless manner, except for some inputs.
     * See to do marks below for more information</p>
     *
     * @param patches   List of all Json-Patch.
     * @param versionID the version ID. Used when constructing a Version.
     * @return The resolved instance.
     */
    public static ResolvedInstance resolveArtifact(List<MultiMCInstancePatch> patches, String versionID) {
        if (patches.isEmpty()) {
            throw new IllegalArgumentException("Empty components.");
        }

        for (MultiMCInstancePatch patch : patches) {
            Objects.requireNonNull(patch, "patch");

            if (patch.getFormatVersion() != 1) {
                throw new UnsupportedOperationException(
                        String.format("Unsupported JSON-Patch[%s] format version: %d", patch.getID(), patch.getFormatVersion())
                );
            }
        }

        StringBuilder message = new StringBuilder();

        List<String> minecraftArguments;
        ArrayList<Argument> jvmArguments = new ArrayList<>(Arguments.DEFAULT_JVM_ARGUMENTS);
        String mainClass;
        AssetIndexInfo assetIndex;
        int[] javaMajors;
        Library mainJar;
        List<String> traits;
        List<String> tweakers;
        /* TODO: MultiMC use a slightly different way to store jars containing jni files.
            Transforming them to Official Scheme might boost compatibility with other launchers. */
        List<Library> libraries;
        List<Library> mavenOnlyFiles;
        List<String> jarModFileNames;

        {
            MultiMCInstancePatch last = patches.get(patches.size() - 1);
            minecraftArguments = last.getMinecraftArguments() == null ? null : StringUtils.tokenize(last.getMinecraftArguments());
            mainClass = last.getMainClass();
            assetIndex = last.getAssetIndex();
            javaMajors = last.getJavaMajors();
            mainJar = last.getMainJar();
            traits = last.getTraits();
            tweakers = last.getTweakers();
            libraries = last.getLibraries();
            mavenOnlyFiles = last.getMavenOnlyFiles();
            jarModFileNames = last.getJarMods().stream().map(Library::getFileName).collect(Collectors.toList());
        }

        for (int i = patches.size() - 2; i >= 0; i--) {
            MultiMCInstancePatch patch = patches.get(i);
            if (minecraftArguments == null & patch.getMinecraftArguments() != null) {
                minecraftArguments = StringUtils.tokenize(patch.getMinecraftArguments());
            }
            for (String jvmArg : patch.getJvmArgs()) {
                jvmArguments.add(new StringArgument(jvmArg));
            }
            mainClass = Lang.requireNonNullElse(mainClass, patch.getMainClass());
            assetIndex = Lang.requireNonNullElse(patch.getAssetIndex(), assetIndex);
            javaMajors = Lang.requireNonNullElse(patch.getJavaMajors(), javaMajors);
            mainJar = Lang.requireNonNullElse(patch.getMainJar(), mainJar);
            traits = Lang.merge(patch.getTraits(), traits);
            tweakers = Lang.merge(patch.getTweakers(), tweakers);
            libraries = Lang.merge(patch.getLibraries(), libraries);
            mavenOnlyFiles = Lang.merge(patch.getMavenOnlyFiles(), mavenOnlyFiles);
            jarModFileNames = Lang.merge(patch.getJarMods().stream().map(Library::getFileName).collect(Collectors.toList()), jarModFileNames);
        }

        mainClass = Lang.requireNonNullElse(mainClass, "net.minecraft.client.Minecraft");

        if (minecraftArguments == null) {
            minecraftArguments = new ArrayList<>();
        }

        // '--tweakClass' can't be the last argument.
        for (int i = minecraftArguments.size() - 2; i >= 0; i--) {
            if ("--tweakClass".equals(minecraftArguments.get(i))) {
                tweakers.add(minecraftArguments.get(i + 1));

                minecraftArguments.remove(i);
                minecraftArguments.remove(i);
            }
        }

        traits = dropDuplicate(traits);
        tweakers = dropDuplicate(tweakers);
        jarModFileNames = dropDuplicate(jarModFileNames);

        for (String tweaker : tweakers) {
            minecraftArguments.add("--tweakClass");
            minecraftArguments.add(tweaker);
        }

        for (String trait : traits) {
            switch (trait) {
                case "FirstThreadOnMacOS": {
                    jvmArguments.add(new RuledArgument(
                            Collections.singletonList(
                                    new CompatibilityRule(CompatibilityRule.Action.ALLOW, new OSRestriction(OperatingSystem.MACOS))
                            ),
                            Collections.singletonList("-XstartOnFirstThread")
                    ));
                    break;
                }
                case "XR:Initial": // Flag for chat report. See https://discord.com/channels/132965178051526656/134843027553255425/1380885829702127616
                case "texturepacks": // HMCL hasn't support checking whether a game version supports texture packs.
                case "no-texturepacks": {
                    break;
                }
                default: {
                    message.append(" - Trait: ").append(trait).append('\n');
                    break;
                }
            }
        }

        for (Library library : libraries) {
            Artifact artifact = library.getArtifact();
            if ("io.github.zekerzhayard".equals(artifact.getGroup()) && "ForgeWrapper".equals(artifact.getName())) {
                jvmArguments.add(new StringArgument("-Dforgewrapper.librariesDir=${library_directory}"));
                jvmArguments.add(new StringArgument("-Dforgewrapper.minecraft=${primary_jar}"));

                for (Library lib : libraries) {
                    Artifact ar = lib.getArtifact();
                    if ("net.neoforged".equals(ar.getGroup()) && "neoforge".equals(ar.getName()) && "installer".equals(ar.getClassifier()) ||
                            "net.minecraftforge".equals(ar.getGroup()) && "forge".equals(ar.getName()) && "installer".equals(ar.getClassifier())
                    ) {
                        jvmArguments.add(new StringArgument("-Dforgewrapper.installer=${library_directory}/" + ar.getPath()));
                    }
                }
            }
        }

        {
            libraries.add(0, BOOTSTRAP_LIBRARY);
            jvmArguments.add(new StringArgument("-Dhmcl.mmc.bootstrap=" + NetworkUtils.withQuery("hmcl:///bootstrap_profile_v1/", Map.of(
                    "main_class", mainClass,
                    "installer", MultiMCComponents.getInstallerProfile()
            ))));
            mainClass = "org.jackhuang.hmcl.HMCLMultiMCBootstrap";
        }

        Version version = new Version(versionID)
                .setArguments(new Arguments().addGameArguments(minecraftArguments).addJVMArgumentsDirect(jvmArguments))
                .setMainClass(mainClass)
                .setLibraries(libraries)
                .setAssetIndex(assetIndex)
                .setDownload(new JsonMap<>(Collections.singletonMap(DownloadType.CLIENT, mainJar.getRawDownloadInfo())));

        /* TODO: Official Version-Json can only store one pre-defined GameJavaVersion, including 8, 11, 16, 17 and 21.
            An array of all suitable java versions are NOT supported.
            For compatibility with official launcher and any other launchers, a transform is made between int[] and GameJavaVersion. */
        javaMajors:
        if (javaMajors != null) {
            javaMajors = javaMajors.clone();
            Arrays.sort(javaMajors);

            for (int i = javaMajors.length - 1; i >= 0; i--) {
                GameJavaVersion jv = GameJavaVersion.get(javaMajors[i]);
                if (jv != null) {
                    version = version.setJavaVersion(jv);
                    break javaMajors;
                }
            }

            message.append(" - Java Version Range: ").append(Arrays.toString(javaMajors)).append('\n');
        }

        version = version.markAsResolved();

        String gameVersion = null;
        for (MultiMCInstancePatch patch : patches) {
            if (MultiMCComponents.getComponent(patch.getID()) == LibraryAnalyzer.LibraryType.MINECRAFT) {
                gameVersion = patch.getVersion();
                break;
            }
        }

        if (message.length() != 0) {
            if (message.charAt(message.length() - 1) == '\n') {
                message.setLength(message.length() - 1);
            }
            Logger.LOG.warning("Cannot fully parse MultiMC modpack with following unsupported features: \n" + message);
        }
        return new ResolvedInstance(version, gameVersion, mainJar, jarModFileNames, mavenOnlyFiles);
    }
}
