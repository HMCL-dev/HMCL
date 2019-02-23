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
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.game.CompatibilityRule;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.SimpleMultimap;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class LibrariesUniqueTask extends TaskResult<Version> {
    private final Version version;

    public LibrariesUniqueTask(Version version) {
        this.version = version;
    }

    @Override
    public void execute() {
        List<Library> libraries = new ArrayList<>(version.getLibraries());

        SimpleMultimap<String, Library> multimap = new SimpleMultimap<String, Library>(HashMap::new, LinkedList::new);

        for (Library library : libraries) {
            String id = library.getGroupId() + ":" + library.getArtifactId();
            VersionNumber number = VersionNumber.asVersion(library.getVersion());
            String serialized = JsonUtils.GSON.toJson(library);

            if (multimap.containsKey(id)) {
                boolean duplicate = false;
                for (Library otherLibrary : multimap.get(id)) {
                    VersionNumber otherNumber = VersionNumber.asVersion(otherLibrary.getVersion());
                    if (CompatibilityRule.equals(library.getRules(), otherLibrary.getRules())) { // rules equal, ignore older version.
                        boolean flag = true;
                        if (number.compareTo(otherNumber) > 0) { // if this library is newer
                            multimap.removeValue(otherLibrary);
                            multimap.put(id, library);
                            break;
                        } else if (number.compareTo(otherNumber) == 0) { // same library id.
                            // prevent from duplicated libraries
                            if (library.equals(otherLibrary)) {
                                String otherSerialized = JsonUtils.GSON.toJson(otherLibrary);
                                // A trick, the library that has more information is better, which can be
                                // considered whose serialized JSON text will be longer.
                                if (serialized.length() > otherSerialized.length()) {
                                    multimap.removeValue(id, otherLibrary);
                                    multimap.put(id, library);
                                    break;
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
                    multimap.put(id, library);
                }
            } else {
                multimap.put(id, library);
            }
        }

        setResult(version.setLibraries(multimap.values().stream().sorted().collect(Collectors.toList())));
    }
}
