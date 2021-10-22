/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game.tlauncher;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.util.List;
import java.util.Map;

@Immutable
public class TLauncherLibrary {

    @SerializedName("name")
    private final Artifact name;
    private final String url;
    private final LibraryDownloadInfo artifact;

    @SerializedName("classifies") // stupid typo made by TLauncher
    private final Map<String, LibraryDownloadInfo> classifiers;
    private final ExtractRules extract;
    private final Map<OperatingSystem, String> natives;
    private final List<CompatibilityRule> rules;
    private final List<String> checksums;

    public TLauncherLibrary(Artifact name, String url, LibraryDownloadInfo artifact, Map<String, LibraryDownloadInfo> classifiers, ExtractRules extract, Map<OperatingSystem, String> natives, List<CompatibilityRule> rules, List<String> checksums) {
        this.name = name;
        this.url = url;
        this.artifact = artifact;
        this.classifiers = classifiers;
        this.extract = extract;
        this.natives = natives;
        this.rules = rules;
        this.checksums = checksums;
    }

    public Library toLibrary() {
        return new Library(
                name,
                url,
                new LibrariesDownloadInfo(artifact, classifiers),
                checksums,
                extract,
                natives,
                rules,
                null,
                null
        );
    }
}
