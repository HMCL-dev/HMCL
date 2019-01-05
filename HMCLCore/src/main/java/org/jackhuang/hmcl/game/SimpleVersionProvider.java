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
package org.jackhuang.hmcl.game;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public class SimpleVersionProvider implements VersionProvider {

    protected final Map<String, Version> versionMap = new HashMap<>();

    @Override
    public boolean hasVersion(String id) {
        return versionMap.containsKey(id);
    }

    @Override
    public Version getVersion(String id) {
        if (!hasVersion(id))
            throw new VersionNotFoundException("Version id " + id + " not found");
        else
            return versionMap.get(id);
    }

    public void addVersion(Version version) {
        versionMap.put(version.getId(), version);
    }

    public Map<String, Version> getVersionMap() {
        return versionMap;
    }
}
