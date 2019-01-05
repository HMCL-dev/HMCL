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

import java.util.Optional;

public final class LibraryAnalyzer {
    private final Library forge;
    private final Library liteLoader;
    private final Library optiFine;

    public LibraryAnalyzer(Library forge, Library liteLoader, Library optiFine) {
        this.forge = forge;
        this.liteLoader = liteLoader;
        this.optiFine = optiFine;
    }

    public Optional<Library> getForge() {
        return Optional.ofNullable(forge);
    }

    public boolean hasForge() {
        return forge != null;
    }

    public Optional<Library> getLiteLoader() {
        return Optional.ofNullable(liteLoader);
    }

    public boolean hasLiteLoader() {
        return liteLoader != null;
    }

    public Optional<Library> getOptiFine() {
        return Optional.ofNullable(optiFine);
    }

    public boolean hasOptiFine() {
        return optiFine != null;
    }

    public static LibraryAnalyzer analyze(Version version) {
        Library forge = null, liteLoader = null, optiFine = null;

        for (Library library : version.getLibraries()) {
            String groupId = library.getGroupId();
            String artifactId = library.getArtifactId();
            if (groupId.equalsIgnoreCase("net.minecraftforge") && artifactId.equalsIgnoreCase("forge"))
                forge = library;

            if (groupId.equalsIgnoreCase("com.mumfrey") && artifactId.equalsIgnoreCase("liteloader"))
                liteLoader = library;

            if ((groupId.equalsIgnoreCase("optifine") || groupId.equalsIgnoreCase("net.optifine")) && artifactId.equalsIgnoreCase("optifine"))
                optiFine = library;
        }

        return new LibraryAnalyzer(forge, liteLoader, optiFine);
    }
}
