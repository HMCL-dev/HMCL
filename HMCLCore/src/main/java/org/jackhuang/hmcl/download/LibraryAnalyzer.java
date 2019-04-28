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

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class LibraryAnalyzer {
    private final Map<LibraryType, Library> libraries;

    private LibraryAnalyzer(Map<LibraryType, Library> libraries) {
        this.libraries = libraries;
    }

    public Optional<Library> get(LibraryType type) {
        return Optional.ofNullable(libraries.get(type));
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
        Map<LibraryType, Library> libraries = new EnumMap<>(LibraryType.class);

        for (Library library : version.getLibraries()) {
            String groupId = library.getGroupId();
            String artifactId = library.getArtifactId();

            for (LibraryType type : LibraryType.values()) {
                if (type.group.matcher(groupId).matches() && type.artifact.matcher(artifactId).matches()) {
                    libraries.put(type, library);
                    break;
                }
            }
        }

        return new LibraryAnalyzer(libraries);
    }

    public enum LibraryType {
        FORGE(true, Pattern.compile("net\\.minecraftforge"), Pattern.compile("forge")),
        LITELOADER(true, Pattern.compile("com\\.mumfrey"), Pattern.compile("liteloader")),
        OPTIFINE(false, Pattern.compile("(net\\.)?optifine"), Pattern.compile(".*")),
        FABRIC(true, Pattern.compile("net\\.fabricmc"), Pattern.compile(".*"));

        private final Pattern group, artifact;
        private final boolean modLoader;

        LibraryType(boolean modLoader, Pattern group, Pattern artifact) {
            this.modLoader = modLoader;
            this.group = group;
            this.artifact = artifact;
        }

        public boolean isModLoader() {
            return modLoader;
        }
    }
}
