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
package org.jackhuang.hmcl.download.liteloader;

import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.util.Immutable;

import java.util.Collection;
import java.util.Collections;

/**
 *
 * @author huangyuhui
 */
@Immutable
public record LiteLoaderVersion(String tweakClass, String file, String version, String md5, String timestamp,
                                int lastSuccessfulBuild, Collection<Library> libraries) {

    public LiteLoaderVersion {
        if (tweakClass == null) tweakClass = "";
        if (file == null) file = "";
        if (version == null) version = "";
        if (md5 == null) md5 = "";
        if (timestamp == null) timestamp = "";
        libraries = libraries == null ? Collections.emptySet() : Collections.unmodifiableCollection(libraries);
    }
}
