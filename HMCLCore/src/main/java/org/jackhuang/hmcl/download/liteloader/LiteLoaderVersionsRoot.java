/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;

import java.util.Collections;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class LiteLoaderVersionsRoot {

    @SerializedName("versions")
    private final Map<String, LiteLoaderGameVersions> versions;

    @SerializedName("meta")
    private final LiteLoaderVersionsMeta meta;

    public LiteLoaderVersionsRoot() {
        this(Collections.emptyMap(), null);
    }

    public LiteLoaderVersionsRoot(Map<String, LiteLoaderGameVersions> versions, LiteLoaderVersionsMeta meta) {
        this.versions = versions;
        this.meta = meta;
    }

    public Map<String, LiteLoaderGameVersions> getVersions() {
        return Collections.unmodifiableMap(versions);
    }

    public LiteLoaderVersionsMeta getMeta() {
        return meta;
    }

}
