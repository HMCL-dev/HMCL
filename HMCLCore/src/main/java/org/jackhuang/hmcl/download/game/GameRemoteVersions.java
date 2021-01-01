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
package org.jackhuang.hmcl.download.game;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.Validation;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class GameRemoteVersions implements Validation {

    @SerializedName("versions")
    private final List<GameRemoteVersionInfo> versions;

    @SerializedName("latest")
    private final GameRemoteLatestVersions latest;

    /**
     * No-arg constructor for Gson.
     */
    @SuppressWarnings("unused")
    public GameRemoteVersions() {
        this(Collections.emptyList(), null);
    }

    public GameRemoteVersions(List<GameRemoteVersionInfo> versions, GameRemoteLatestVersions latest) {
        this.versions = versions;
        this.latest = latest;
    }

    public GameRemoteLatestVersions getLatest() {
        return latest;
    }

    public List<GameRemoteVersionInfo> getVersions() {
        return versions;
    }

    @Override
    public void validate() throws JsonParseException {
        if (versions == null)
            throw new JsonParseException("GameRemoteVersions.versions cannot be null");
    }
}
