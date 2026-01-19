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

import org.intellij.lang.annotations.Language;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionRange;
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

    /**
     * If a library is provided in $.patches, it's structure is so clear that we can do any operation.
     * Otherwise, we must guess how are these libraries mixed.
     * Maybe a guessing implementation will be provided in the future. But by now, we simply set it to JUST_EXISTED.
     */
    public LibraryMark.LibraryStatus getLibraryStatus(String type) {
        return version.hasPatch(type) ? LibraryMark.LibraryStatus.CLEAR : LibraryMark.LibraryStatus.JUST_EXISTED;
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
                return new LibraryMark(entry.getKey(), entry.getValue().getValue(), getLibraryStatus(entry.getKey()));
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
        return LibraryAnalyzer.MOD_LAUNCHER_MAIN.equals(version.getMainClass()) || version.getPatches().stream().anyMatch(
                patch -> LibraryAnalyzer.MOD_LAUNCHER_MAIN.equals(patch.getMainClass())
        );
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
     * @param libraryId patch id or "forge"/"optifine"/"liteloader"/"fabric"/"quilt"/"neoforge"/"cleanroom"
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
                || mainClass.startsWith("net.minecraftforge")
                || mainClass.startsWith("net.neoforged")
                || mainClass.startsWith("top.outlands") //Cleanroom
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
        MINECRAFT(true, "game", "^$", "^$", null),
        LEGACY_FABRIC(true, "legacyfabric", "net\\.fabricmc", "fabric-loader", ModLoaderType.LEGACY_FABRIC) {
            @Override
            protected boolean matchLibrary(Library library, List<Library> libraries) {
                if (!super.matchLibrary(library, libraries)) {
                    return false;
                }
                for (Library l : libraries) {
                    if ("net.legacyfabric".equals(l.getGroupId())) {
                        return true;
                    }
                }
                return false;
            }
        },
        LEGACY_FABRIC_API(false, "legacyfabric-api", "net\\.legacyfabric", "legacyfabric-api", null),
        FABRIC(true, "fabric", "net\\.fabricmc", "fabric-loader", ModLoaderType.FABRIC) {
            @Override
            protected boolean matchLibrary(Library library, List<Library> libraries) {
                if (!super.matchLibrary(library, libraries)) {
                    return false;
                }
                for (Library l : libraries) {
                    if ("net.legacyfabric".equals(l.getGroupId())) {
                        return false;
                    }
                }
                return true;
            }
        },
        FABRIC_API(true, "fabric-api", "net\\.fabricmc", "fabric-api", null),
        FORGE(true, "forge", "net\\.minecraftforge", "(forge|fmlloader)", ModLoaderType.FORGE) {
            private final Pattern FORGE_VERSION_MATCHER = Pattern.compile("^([0-9.]+)-(?<forge>[0-9.]+)(-([0-9.]+))?$");

            @Override
            protected String patchVersion(Version gameVersion, String libraryVersion) {
                Matcher matcher = FORGE_VERSION_MATCHER.matcher(libraryVersion);
                if (matcher.find()) {
                    return matcher.group("forge");
                }
                return super.patchVersion(gameVersion, libraryVersion);
            }

            @Override
            protected boolean matchLibrary(Library library, List<Library> libraries) {
                for (Library l : libraries) {
                    if (NEO_FORGE.matchLibrary(l, libraries)) {
                        return false;
                    }
                }
                return super.matchLibrary(library, libraries);
            }
        },
        CLEANROOM(true, "cleanroom", "com\\.cleanroommc", "cleanroom", ModLoaderType.CLEANROOM),
        NEO_FORGE(true, "neoforge", "net\\.neoforged\\.fancymodloader", "(core|loader)", ModLoaderType.NEO_FORGED) {
            private final Pattern NEO_FORGE_VERSION_MATCHER = Pattern.compile("^([0-9.]+)-(?<forge>[0-9.]+)(-([0-9.]+))?$");

            @Override
            protected String patchVersion(Version gameVersion, String libraryVersion) {
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

                Matcher matcher = NEO_FORGE_VERSION_MATCHER.matcher(libraryVersion);
                if (matcher.find()) {
                    return matcher.group("forge");
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
                    if (argument instanceof StringArgument) {
                        String argumentValue = ((StringArgument) argument).getArgument();
                        if ("--fml.neoForgeVersion".equals(argumentValue) || "--fml.forgeVersion".equals(argumentValue)) {
                            Argument next = gameArguments.get(i + 1);
                            if (next instanceof StringArgument) {
                                return ((StringArgument) next).getArgument();
                            }
                            return null; // Normally, there should not be two --fml.neoForgeVersion argument.
                        }
                    }
                }
                return null;
            }

        },
        LITELOADER(true, "liteloader", "com\\.mumfrey", "liteloader", ModLoaderType.LITE_LOADER),
        OPTIFINE(false, "optifine", "(net\\.)?optifine", "^(?!.*launchwrapper).*$", null),
        QUILT(true, "quilt", "org\\.quiltmc", "quilt-loader", ModLoaderType.QUILT),
        QUILT_API(true, "quilt-api", "org\\.quiltmc", "quilt-api", null),
        BOOTSTRAP_LAUNCHER(false, "", "cpw\\.mods", "bootstraplauncher", null);

        private final boolean modLoader;
        private final String patchId;
        private final Pattern group, artifact;
        private final ModLoaderType modLoaderType;

        private static final Map<String, LibraryType> PATCH_ID_MAP = new HashMap<>();

        static {
            for (LibraryType type : values()) {
                PATCH_ID_MAP.put(type.getPatchId(), type);
            }
        }

        LibraryType(boolean modLoader, String patchId, @Language("RegExp") String group, @Language("RegExp") String artifact, ModLoaderType modLoaderType) {
            this.modLoader = modLoader;
            this.patchId = patchId;
            this.group = Pattern.compile(group);
            this.artifact = Pattern.compile(artifact);
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
            return PATCH_ID_MAP.get(patchId);
        }

        protected boolean matchLibrary(Library library, List<Library> libraries) {
            return group.matcher(library.getGroupId()).matches() && artifact.matcher(library.getArtifactId()).matches();
        }

        protected String patchVersion(Version gameVersion, String libraryVersion) {
            return libraryVersion;
        }
    }

    public final static class LibraryMark {
        /**
         * If a library is provided in $.patches, it's structure is so clear that we can do any operation.
         * Otherwise, we must guess how are these libraries mixed.
         * Maybe a guessing implementation will be provided in the future. But by now, we simply set it to JUST_EXISTED.
         */
        public enum LibraryStatus {
            CLEAR, UNSURE, JUST_EXISTED
        }

        private final String libraryId;
        private final String libraryVersion;
        /**
         * If this version is installed by HMCL, instead of external process,
         * which means $.patches contains this library, structureClear is true.
         */
        private final LibraryStatus status;

        private LibraryMark(@NotNull String libraryId, @Nullable String libraryVersion, LibraryStatus status) {
            this.libraryId = libraryId;
            this.libraryVersion = libraryVersion;
            this.status = status;
        }

        @NotNull
        public String getLibraryId() {
            return libraryId;
        }

        @Nullable
        public String getLibraryVersion() {
            return libraryVersion;
        }

        public LibraryStatus getStatus() {
            return status;
        }
    }

    public static final String VANILLA_MAIN = "net.minecraft.client.main.Main";
    public static final String LAUNCH_WRAPPER_MAIN = "net.minecraft.launchwrapper.Launch";
    public static final String MOD_LAUNCHER_MAIN = "cpw.mods.modlauncher.Launcher";
    public static final String BOOTSTRAP_LAUNCHER_MAIN = "cpw.mods.bootstraplauncher.BootstrapLauncher";
    public static final String FORGE_BOOTSTRAP_MAIN = "net.minecraftforge.bootstrap.ForgeBootstrap";
    public static final String NEO_FORGED_BOOTSTRAP_MAIN = "net.neoforged.fml.startup.Client";

    public static final Set<String> FORGE_OPTIFINE_MAIN = Set.of(
            LibraryAnalyzer.VANILLA_MAIN,
            LibraryAnalyzer.LAUNCH_WRAPPER_MAIN,
            LibraryAnalyzer.MOD_LAUNCHER_MAIN,
            LibraryAnalyzer.BOOTSTRAP_LAUNCHER_MAIN,
            LibraryAnalyzer.FORGE_BOOTSTRAP_MAIN,
            LibraryAnalyzer.NEO_FORGED_BOOTSTRAP_MAIN
    );

    public static final VersionRange<VersionNumber> FORGE_OPTIFINE_BROKEN_RANGE = VersionNumber.between("48.0.0", "49.0.50");

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
