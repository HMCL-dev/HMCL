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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Pair.pair;

public final class LibraryAnalyzer implements Iterable<LibraryAnalyzer.LibraryMark> {
    private Version version;
    private final Map<String, Pair<Library, String>> libraries;

    private LibraryAnalyzer(Version version, Map<String, Pair<Library, String>> libraries) {
        this.version = version;
        this.libraries = libraries;
    }

    public Optional<String> getVersion(LibraryType type) {
        return getVersion(type.getPatchId());
    }

    public Optional<String> getVersion(String type) {
        return Optional.ofNullable(libraries.get(type)).map(Pair::getValue);
    }

    public Optional<Library> getLibrary(LibraryType type) {
        return Optional.ofNullable(libraries.get(type.getPatchId())).map(Pair::getKey);
    }

    @NotNull
    @Override
    public Iterator<LibraryMark> iterator() {
        return new Iterator<LibraryMark>() {
            Iterator<Map.Entry<String, Pair<Library, String>>> impl = libraries.entrySet().iterator();

            @Override
            public boolean hasNext() {
                return impl.hasNext();
            }

            @Override
            public LibraryMark next() {
                Map.Entry<String, Pair<Library, String>> entry = impl.next();
                return new LibraryMark(entry.getKey(), entry.getValue().getValue());
            }
        };
    }

    public boolean has(LibraryType type) {
        return has(type.getPatchId());
    }

    public boolean has(String type) {
        return libraries.containsKey(type);
    }

    public boolean hasModLoader() {
        return libraries.keySet().stream().map(LibraryType::fromPatchId)
                .filter(Objects::nonNull)
                .anyMatch(LibraryType::isModLoader);
    }

    public boolean hasModLauncher() {
        final String modLauncher = "cpw.mods.modlauncher.Launcher";
        return modLauncher.equals(version.getMainClass()) || version.getPatches().stream().anyMatch(patch -> modLauncher.equals(patch.getMainClass()));
    }

    public boolean hasBootstrapLauncher() {
        final String bootstrapLauncher = "cpw.mods.bootstraplauncher.BootstrapLauncher";
        return bootstrapLauncher.equals(version.getMainClass()) || version.getPatches().stream().anyMatch(patch -> bootstrapLauncher.equals(patch.getMainClass()));
    }

    private Version removingMatchedLibrary(Version version, String libraryId) {
        LibraryType type = LibraryType.fromPatchId(libraryId);
        if (type == null) return version;

        List<Library> libraries = new ArrayList<>();
        List<Library> rawLibraries = version.getLibraries();
        for (Library library : rawLibraries) {
            if (type.matchLibrary(library, rawLibraries)) {
                // skip
            } else {
                libraries.add(library);
            }
        }
        return version.setLibraries(libraries);
    }

    /**
     * Remove library by library id
     *
     * @param libraryId patch id or "forge"/"optifine"/"liteloader"/"fabric"/"quilt"/"neoforge"
     * @return this
     */
    public LibraryAnalyzer removeLibrary(String libraryId) {
        if (!has(libraryId)) return this;
        version = removingMatchedLibrary(version, libraryId)
                .setPatches(version.getPatches().stream()
                        .filter(patch -> !libraryId.equals(patch.getId()))
                        .map(patch -> removingMatchedLibrary(patch, libraryId))
                        .collect(Collectors.toList()));
        return this;
    }

    public Version build() {
        return version;
    }

    public static LibraryAnalyzer analyze(Version version, String gameVersion) {
        if (version.getInheritsFrom() != null)
            throw new IllegalArgumentException("LibraryAnalyzer can only analyze independent game version");

        Map<String, Pair<Library, String>> libraries = new HashMap<>();

        if (gameVersion != null) {
            libraries.put(LibraryType.MINECRAFT.getPatchId(), pair(null, gameVersion));
        }

        List<Library> rawLibraries = version.resolve(null).getLibraries();
        for (Library library : rawLibraries) {
            for (LibraryType type : LibraryType.values()) {
                if (type.matchLibrary(library, rawLibraries)) {
                    libraries.put(type.getPatchId(), pair(library, type.patchVersion(version, library.getVersion())));
                    break;
                }
            }
        }

        for (Version patch : version.getPatches()) {
            if (patch.isHidden()) continue;
            libraries.put(patch.getId(), pair(null, patch.getVersion()));
        }

        return new LibraryAnalyzer(version, libraries);
    }

    public static boolean isModded(VersionProvider provider, Version version) {
        Version resolvedVersion = version.resolve(provider);
        String mainClass = resolvedVersion.getMainClass();
        return mainClass != null && (LAUNCH_WRAPPER_MAIN.equals(mainClass)
                || mainClass.startsWith("net.fabricmc")
                || mainClass.startsWith("org.quiltmc")
                || mainClass.startsWith("cpw.mods"));
    }

    public Set<ModLoaderType> getModLoaders() {
        return Arrays.stream(LibraryType.values())
                .filter(LibraryType::isModLoader)
                .filter(this::has)
                .map(LibraryType::getModLoaderType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public enum LibraryType {
        MINECRAFT(true, "game", Pattern.compile("^$"), Pattern.compile("^$"), null),
        FABRIC(true, "fabric", Pattern.compile("net\\.fabricmc"), Pattern.compile("fabric-loader"), ModLoaderType.FABRIC),
        FABRIC_API(true, "fabric-api", Pattern.compile("net\\.fabricmc"), Pattern.compile("fabric-api"), null),
        FORGE(true, "forge", Pattern.compile("net\\.minecraftforge"), Pattern.compile("(forge|fmlloader)"), ModLoaderType.FORGE) {
            private final Pattern FORGE_VERSION_MATCHER = Pattern.compile("^([0-9.]+)-(?<forge>[0-9.]+)(-([0-9.]+))?$");

            @Override
            public String patchVersion(Version gameVersion, String libraryVersion) {
                Matcher matcher = FORGE_VERSION_MATCHER.matcher(libraryVersion);
                if (matcher.find()) {
                    return matcher.group("forge");
                }
                return super.patchVersion(gameVersion, libraryVersion);
            }

            @Override
            public boolean matchLibrary(Library library, List<Library> libraries) {
                for (Library l : libraries) {
                    if (NEO_FORGE.matchLibrary(l, libraries)) {
                        return false;
                    }
                }
                return super.matchLibrary(library, libraries);
            }
        },
        NEO_FORGE(true, "neoforge", Pattern.compile("net\\.neoforged\\.fancymodloader"), Pattern.compile("(core|loader)"), ModLoaderType.NEO_FORGED) {
            private final Pattern NEO_FORGE_VERSION_MATCHER = Pattern.compile("^([0-9.]+)-(?<forge>[0-9.]+)(-([0-9.]+))?$");

            @Override
            public String patchVersion(Version gameVersion, String libraryVersion) {
                Matcher matcher = NEO_FORGE_VERSION_MATCHER.matcher(libraryVersion);
                if (matcher.find()) {
                    return matcher.group("forge");
                }

                String res = scanVersion(gameVersion);
                if (res != null) {
                    return res;
                }

                for (Version patch : gameVersion.getPatches()) {
                    res = scanVersion(patch);
                    if (res != null) {
                        return res;
                    }
                }

                return super.patchVersion(gameVersion, libraryVersion);
            }

            private String scanVersion(Version version) {
                Optional<Arguments> optArgument = version.getArguments();
                if (!optArgument.isPresent()) {
                    return null;
                }
                List<Argument> gameArguments = optArgument.get().getGame();
                if (gameArguments == null) {
                    return null;
                }

                for (int i = 0; i < gameArguments.size() - 1; i++) {
                    Argument argument = gameArguments.get(i);
                    if (argument instanceof StringArgument && "--fml.neoForgeVersion".equals(((StringArgument) argument).getArgument())) {
                        Argument next = gameArguments.get(i + 1);
                        if (next instanceof StringArgument) {
                            return ((StringArgument) next).getArgument();
                        }
                        return null; // Normally, there should not be two --fml.neoForgeVersion argument.
                    }
                }
                return null;
            }

        },
        LITELOADER(true, "liteloader", Pattern.compile("com\\.mumfrey"), Pattern.compile("liteloader"), ModLoaderType.LITE_LOADER),
        OPTIFINE(false, "optifine", Pattern.compile("(net\\.)?optifine"), Pattern.compile("^(?!.*launchwrapper).*$"), null),
        QUILT(true, "quilt", Pattern.compile("org\\.quiltmc"), Pattern.compile("quilt-loader"), ModLoaderType.QUILT),
        QUILT_API(true, "quilt-api", Pattern.compile("org\\.quiltmc"), Pattern.compile("quilt-api"), null),
        BOOTSTRAP_LAUNCHER(false, "", Pattern.compile("cpw\\.mods"), Pattern.compile("bootstraplauncher"), null);

        private final boolean modLoader;
        private final String patchId;
        private final Pattern group, artifact;
        private final ModLoaderType modLoaderType;

        LibraryType(boolean modLoader, String patchId, Pattern group, Pattern artifact, ModLoaderType modLoaderType) {
            this.modLoader = modLoader;
            this.patchId = patchId;
            this.group = group;
            this.artifact = artifact;
            this.modLoaderType = modLoaderType;
        }

        public boolean isModLoader() {
            return modLoader;
        }

        public String getPatchId() {
            return patchId;
        }

        public ModLoaderType getModLoaderType() {
            return modLoaderType;
        }

        public static LibraryType fromPatchId(String patchId) {
            for (LibraryType type : values())
                if (type.getPatchId().equals(patchId))
                    return type;
            return null;
        }

        public boolean matchLibrary(Library library, List<Library> libraries) {
            return group.matcher(library.getGroupId()).matches() && artifact.matcher(library.getArtifactId()).matches();
        }

        public String patchVersion(Version gameVersion, String libraryVersion) {
            return libraryVersion;
        }
    }

    public static class LibraryMark {
        private final String libraryId;
        private final String libraryVersion;

        public LibraryMark(@NotNull String libraryId, @Nullable String libraryVersion) {
            this.libraryId = libraryId;
            this.libraryVersion = libraryVersion;
        }

        @NotNull
        public String getLibraryId() {
            return libraryId;
        }

        @Nullable
        public String getLibraryVersion() {
            return libraryVersion;
        }
    }

    public static final String VANILLA_MAIN = "net.minecraft.client.main.Main";
    public static final String LAUNCH_WRAPPER_MAIN = "net.minecraft.launchwrapper.Launch";
    public static final String MOD_LAUNCHER_MAIN = "cpw.mods.modlauncher.Launcher";
    public static final String BOOTSTRAP_LAUNCHER_MAIN = "cpw.mods.bootstraplauncher.BootstrapLauncher";

    public static final String[] FORGE_TWEAKERS = new String[]{
            "net.minecraftforge.legacy._1_5_2.LibraryFixerTweaker", // 1.5.2
            "cpw.mods.fml.common.launcher.FMLTweaker", // 1.6.1 ~ 1.7.10
            "net.minecraftforge.fml.common.launcher.FMLTweaker" // 1.8 ~ 1.12.2
    };
    public static final String[] OPTIFINE_TWEAKERS = new String[]{
            "optifine.OptiFineTweaker",
            "optifine.OptiFineForgeTweaker"
    };
    public static final String LITELOADER_TWEAKER = "com.mumfrey.liteloader.launch.LiteLoaderTweaker";
}
