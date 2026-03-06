/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions.server;

import org.jackhuang.hmcl.setting.Profile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ServerDataHolder {
    final Profile profile;
    final Path serverDatPath;
    final int inDatPathSlot;

    final List<String> holdInstances = new ArrayList<>();
    final ServerData serverData;

    public ServerDataHolder(Profile profile, Path serverDatPath, int inDatPathSlot, final ServerData serverData) {
        this.profile = profile;
        this.serverDatPath = serverDatPath;
        this.inDatPathSlot = inDatPathSlot;
        this.serverData = serverData;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ServerDataHolder that = (ServerDataHolder) o;
        return inDatPathSlot == that.inDatPathSlot && Objects.equals(profile, that.profile) && Objects.equals(serverDatPath, that.serverDatPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profile, serverDatPath, inDatPathSlot);
    }
}
