/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.download.liteloader;

import com.google.gson.annotations.SerializedName;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.util.Immutable;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class LiteLoaderBranch {

    @SerializedName("libraries")
    private final Collection<Library> libraries;

    @SerializedName("com.mumfrey:liteloader")
    private final Map<String, LiteLoaderVersion> liteLoader;

    public LiteLoaderBranch() {
        this(Collections.EMPTY_SET, Collections.EMPTY_MAP);
    }

    public LiteLoaderBranch(Collection<Library> libraries, Map<String, LiteLoaderVersion> liteLoader) {
        this.libraries = libraries;
        this.liteLoader = liteLoader;
    }

    public Collection<Library> getLibraries() {
        return Collections.unmodifiableCollection(libraries);
    }

    public Map<String, LiteLoaderVersion> getLiteLoader() {
        return Collections.unmodifiableMap(liteLoader);
    }

}
