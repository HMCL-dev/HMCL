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
package org.jackhuang.hmcl.download.liteloader;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class LiteLoaderGameVersions {

    @SerializedName("repo")
    private final LiteLoaderRepository repoitory;

    @SerializedName("artefacts")
    private final LiteLoaderBranch artifacts;

    @SerializedName("snapshots")
    private final LiteLoaderBranch snapshots;

    /**
     * No-arg constructor for Gson.
     */
    @SuppressWarnings("unused")
    public LiteLoaderGameVersions() {
        this(null, null, null);
    }

    public LiteLoaderGameVersions(LiteLoaderRepository repoitory, LiteLoaderBranch artifacts, LiteLoaderBranch snapshots) {
        this.repoitory = repoitory;
        this.artifacts = artifacts;
        this.snapshots = snapshots;
    }

    public LiteLoaderRepository getRepoitory() {
        return repoitory;
    }

    public LiteLoaderBranch getArtifacts() {
        return artifacts;
    }

    public LiteLoaderBranch getSnapshots() {
        return snapshots;
    }

}
