/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.addon.mod;

import org.jackhuang.hmcl.addon.LoaderType;

import java.util.List;

public enum ModLoaderType implements LoaderType {
    UNKNOWN,
    FORGE("forge"),
    CLEANROOM("cleanroom"),
    NEO_FORGE("neoforge"),
    FABRIC("fabric"),
    QUILT("quilt"),
    LITE_LOADER("liteloader"),
    LEGACY_FABRIC("legacy-fabric");

    private final List<String> names;

    ModLoaderType(String... names) {
        this.names = List.of(names);
    }

    @Override
    public List<String> names() {
        return names;
    }

}
