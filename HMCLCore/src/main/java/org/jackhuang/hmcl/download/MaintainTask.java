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

import org.jackhuang.hmcl.game.CompatibilityRule;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.game.VersionLibraryBuilder;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.SimpleMultimap;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;

public class MaintainTask extends Task<Version> {

    private final Version version;

    public MaintainTask(Version version) {
        this.version = version;

        if (version.getInheritsFrom() != null)
            throw new IllegalArgumentException("MaintainTask requires independent game version");
    }

    @Override
    public void execute() {
        setResult(maintain(null, version));
    }

    public static Version maintain(GameRepository repository, Version version) {
        if (version.getInheritsFrom() != null)
            throw new IllegalArgumentException("MaintainTask requires independent game version");

        String mainClass = version.resolve(null).getMainClass();

        if (mainClass != null && mainClass.contains("launchwrapper")) {
            return maintainOptiFineLibrary(repository, maintainGameWithLaunchWrapper(unique(version)));
        } else {
            // Vanilla Minecraft does not need maintain
            // Forge 1.13 support not implemented, not compatible with OptiFine currently.
            // Fabric does not need maintain, nothing compatible with fabric now.
            return maintainOptiFineLibrary(repository, unique(version));
        }
    }

    private static Version maintainGameWithLaunchWrapper(Version version) {
        LibraryAnalyzer libraryAnalyzer = LibraryAnalyzer.analyze(version);
        VersionLibraryBuilder builder = new VersionLibraryBuilder(version);
        String mainClass = null;

        if (!libraryAnalyzer.has(FORGE)) {
            builder.removeTweakClass("forge");
        }

        // Installing Forge will override the Minecraft arguments in json, so LiteLoader and OptiFine Tweaker are being re-added.

        builder.removeTweakClass("liteloader");
        if (libraryAnalyzer.has(LITELOADER) && !libraryAnalyzer.hasModLauncher()) {
            builder.addArgument("--tweakClass", "com.mumfrey.liteloader.launch.LiteLoaderTweaker");
        }

        builder.removeTweakClass("optifine");
        if (libraryAnalyzer.has(OPTIFINE)) {
            if (!libraryAnalyzer.has(LITELOADER) && !libraryAnalyzer.has(FORGE)) {
                builder.addArgument("--tweakClass", "optifine.OptiFineTweaker");
            } else {
                if (libraryAnalyzer.hasModLauncher()) {
                    // If ModLauncher installed, we use ModLauncher in place of LaunchWrapper.
                    mainClass = "cpw.mods.modlauncher.Launcher";
                } else {
                    // If forge or LiteLoader installed, OptiFine Forge Tweaker is needed.
                    builder.addArgument("--tweakClass", "optifine.OptiFineForgeTweaker");
                }
            }
        }

        Version ret = builder.build();
        return mainClass == null ? ret : ret.setMainClass(mainClass);
    }

    private static Version maintainOptiFineLibrary(GameRepository repository, Version version) {
        LibraryAnalyzer libraryAnalyzer = LibraryAnalyzer.analyze(version);
        List<Library> libraries = new ArrayList<>(version.getLibraries());

        if (libraryAnalyzer.has(OPTIFINE)) {
            if (libraryAnalyzer.has(LITELOADER) || libraryAnalyzer.has(FORGE)) {
                // If forge or LiteLoader installed, OptiFine Forge Tweaker is needed.
                // And we should load the installer jar instead of patch jar.
                if (repository != null)
                    for (int i = 0; i < version.getLibraries().size(); ++i) {
                        Library library = libraries.get(i);
                        if (library.is("optifine", "OptiFine")) {
                            Library newLibrary = new Library("optifine", "OptiFine", library.getVersion(), "installer", null, null);
                            if (repository.getLibraryFile(version, newLibrary).exists()) {
                                libraries.set(i, null);
                                // OptiFine should be loaded after Forge in classpath.
                                // Although we have altered priority of OptiFine higher than Forge,
                                // there still exists a situation that Forge is installed without patch.
                                // Here we manually alter the position of OptiFine library in classpath.
                                libraries.add(newLibrary);
                            }
                        }
                    }
            }
        }

        return version.setLibraries(libraries.stream().filter(Objects::nonNull).collect(Collectors.toList()));
    }

    public static Version unique(Version version) {
        List<Library> libraries = new ArrayList<>();

        SimpleMultimap<String, Integer> multimap = new SimpleMultimap<String, Integer>(HashMap::new, LinkedList::new);

        for (Library library : version.getLibraries()) {
            String id = library.getGroupId() + ":" + library.getArtifactId();
            VersionNumber number = VersionNumber.asVersion(library.getVersion());
            String serialized = JsonUtils.GSON.toJson(library);

            if (multimap.containsKey(id)) {
                boolean duplicate = false;
                for (int otherLibraryIndex : multimap.get(id)) {
                    Library otherLibrary = libraries.get(otherLibraryIndex);
                    VersionNumber otherNumber = VersionNumber.asVersion(otherLibrary.getVersion());
                    if (CompatibilityRule.equals(library.getRules(), otherLibrary.getRules())) { // rules equal, ignore older version.
                        boolean flag = true;
                        if (number.compareTo(otherNumber) > 0) { // if this library is newer
                            // replace [otherLibrary] with [library]
                            libraries.set(otherLibraryIndex, library);
                        } else if (number.compareTo(otherNumber) == 0) { // same library id.
                            // prevent from duplicated libraries
                            if (library.equals(otherLibrary)) {
                                String otherSerialized = JsonUtils.GSON.toJson(otherLibrary);
                                // A trick, the library that has more information is better, which can be
                                // considered whose serialized JSON text will be longer.
                                if (serialized.length() > otherSerialized.length()) {
                                    libraries.set(otherLibraryIndex, library);
                                }
                            } else {
                                // for text2speech, which have same library id as well as version number,
                                // but its library and native library does not equal
                                flag = false;
                            }
                        }
                        if (flag) {
                            duplicate = true;
                            break;
                        }
                    }
                }

                if (!duplicate) {
                    multimap.put(id, libraries.size());
                    libraries.add(library);
                }
            } else {
                multimap.put(id, libraries.size());
                libraries.add(library);
            }
        }

        return version.setLibraries(libraries);
    }
}
