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

import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.game.VersionProvider;
import org.jackhuang.hmcl.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.StringUtils.removePrefix;
import static org.jackhuang.hmcl.util.StringUtils.removeSuffix;

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
        for (Library library : version.getLibraries()) {
            if (type.matchLibrary(library)) {
                // skip
            } else {
                libraries.add(library);
            }
        }
        return version.setLibraries(libraries);
    }

    /**
     * Remove library by library id
     * @param libraryId patch id or "forge"/"optifine"/"liteloader"/"fabric"
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

    public static LibraryAnalyzer analyze(Version version) {
        if (version.getInheritsFrom() != null)
            throw new IllegalArgumentException("LibraryAnalyzer can only analyze independent game version");

        Map<String, Pair<Library, String>> libraries = new HashMap<>();

        for (Library library : version.resolve(null).getLibraries()) {
            for (LibraryType type : LibraryType.values()) {
                if (type.matchLibrary(library)) {
                    libraries.put(type.getPatchId(), pair(library, library.getVersion()));
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
        return mainClass != null && (LAUNCH_WRAPPER_MAIN.equals(mainClass) || mainClass.startsWith("net.fabricmc") || mainClass.startsWith("cpw.mods"));
    }

    public static String modifyVersion(String gameVersion, String version) {
        return removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_");
    }

    public enum LibraryType {
        MINECRAFT(true, "game", Pattern.compile("^$"), Pattern.compile("^$")),
        FABRIC(true, "fabric", Pattern.compile("net\\.fabricmc"), Pattern.compile("fabric-loader")),
        FORGE(true, "forge", Pattern.compile("net\\.minecraftforge"), Pattern.compile("(forge|fmlloader)")),
        LITELOADER(true, "liteloader", Pattern.compile("com\\.mumfrey"), Pattern.compile("liteloader")),
        OPTIFINE(false, "optifine", Pattern.compile("(net\\.)?optifine"), Pattern.compile("^(?!.*launchwrapper).*$")),
        BOOTSTRAP_LAUNCHER(false, "", Pattern.compile("cpw\\.mods"), Pattern.compile("bootstraplauncher"));

        private final boolean modLoader;
        private final String patchId;
        private final Pattern group, artifact;

        LibraryType(boolean modLoader, String patchId, Pattern group, Pattern artifact) {
            this.modLoader = modLoader;
            this.patchId = patchId;
            this.group = group;
            this.artifact = artifact;
        }

        public boolean isModLoader() {
            return modLoader;
        }

        public String getPatchId() {
            return patchId;
        }

        public static LibraryType fromPatchId(String patchId) {
            for (LibraryType type : values())
                if (type.getPatchId().equals(patchId))
                    return type;
            return null;
        }

        public boolean matchLibrary(Library library) {
            return group.matcher(library.getGroupId()).matches() && artifact.matcher(library.getArtifactId()).matches();
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
}
