/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.game.CompatibilityRule;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.SimpleMultimap;
import org.jackhuang.hmcl.util.VersionNumber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class LibrariesUniqueTask extends TaskResult<Version> {
    private final Version version;
    private final String id;

    public LibrariesUniqueTask(Version version) {
        this(version, "version");
    }

    public LibrariesUniqueTask(Version version, String id) {
        this.version = version;
        this.id = id;
    }

    @Override
    public void execute() {
        List<Library> libraries = new ArrayList<>(version.getLibraries());

        SimpleMultimap<String, Library> multimap = new SimpleMultimap<String, Library>(HashMap::new, LinkedList::new);

        for (Library library : libraries) {
            String id = library.getGroupId() + ":" + library.getArtifactId();
            VersionNumber number = VersionNumber.asVersion(library.getVersion());
            String serialized = Constants.GSON.toJson(library);

            if (multimap.containsKey(id)) {
                boolean hasEqualRule = false;
                for (Library otherLibrary : multimap.get(id)) {
                    VersionNumber otherNumber = VersionNumber.asVersion(otherLibrary.getVersion());
                    if (CompatibilityRule.equals(library.getRules(), otherLibrary.getRules())) { // rules equal, ignore older version.
                        hasEqualRule = true;
                        if (number.compareTo(otherNumber) > 0) { // if this library is newer
                            multimap.removeValue(otherLibrary);
                            multimap.put(id, library);
                            break;
                        } else if (number.compareTo(otherNumber) == 0) { // same library id.
                            // prevent from duplicated libraries
                            if (library.equals(otherLibrary)) {
                                String otherSerialized = Constants.GSON.toJson(otherLibrary);
                                // A trick, the library that has more information is better, which can be
                                // considered whose serialized JSON text will be longer.
                                if (serialized.length() > otherSerialized.length()) {
                                    multimap.removeValue(id, otherLibrary);
                                    multimap.put(id, library);
                                    break;
                                }
                            }
                        }
                    }
                }

                if (!hasEqualRule) {
                    multimap.put(id, library);
                }
            } else {
                multimap.put(id, library);
            }
        }

        setResult(version.setLibraries(multimap.values().stream().sorted().collect(Collectors.toList())));
    }

    @Override
    public String getId() {
        return id;
    }
}
