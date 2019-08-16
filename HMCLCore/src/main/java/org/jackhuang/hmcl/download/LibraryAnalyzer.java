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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.util.Pair;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public final class LibraryAnalyzer {
    private final Map<LibraryType, Pair<Library, String>> libraries;

    private LibraryAnalyzer(Map<LibraryType, Pair<Library, String>> libraries) {
        this.libraries = libraries;
    }

    public Optional<String> getVersion(LibraryType type) {
        return Optional.ofNullable(libraries.get(type)).map(Pair::getValue);
    }

    public void ifPresent(LibraryType type, BiConsumer<Library, String> consumer) {
        if (libraries.containsKey(type)) {
            Pair<Library, String> value = libraries.get(type);
            consumer.accept(value.getKey(), value.getValue());
        }
    }

    public boolean has(LibraryType type) {
        return libraries.containsKey(type);
    }

    public boolean hasModLoader() {
        return Arrays.stream(LibraryType.values())
                .filter(LibraryType::isModLoader)
                .anyMatch(this::has);
    }

    public static LibraryAnalyzer analyze(Version version) {
        Map<LibraryType, Pair<Library, String>> libraries = new EnumMap<>(LibraryType.class);

        for (Library library : version.getLibraries()) {
            String groupId = library.getGroupId();
            String artifactId = library.getArtifactId();

            for (LibraryType type : LibraryType.values()) {
                if (type.group.matcher(groupId).matches() && type.artifact.matcher(artifactId).matches()) {
                    libraries.put(type, Pair.pair(library, library.getVersion()));
                    break;
                }
            }
        }

        for (Version patch : version.getPatches()) {
            for (LibraryType type : LibraryType.values()) {
                if (type.patchId.equals(patch.getId())) {
                    libraries.put(type, Pair.pair(null, patch.getVersion()));
                    break;
                }
            }
        }

        return new LibraryAnalyzer(libraries);
    }

    public enum LibraryType {
        FABRIC(true, "fabric", Pattern.compile("net\\.fabricmc"), Pattern.compile("fabric-loader")),
        FORGE(true, "forge", Pattern.compile("net\\.minecraftforge"), Pattern.compile("forge")),
        LITELOADER(true, "liteloader", Pattern.compile("com\\.mumfrey"), Pattern.compile("liteloader")),
        OPTIFINE(false, "optifine", Pattern.compile("(net\\.)?optifine"), Pattern.compile("^(?!.*launchwrapper).*$"));

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
    }
}
