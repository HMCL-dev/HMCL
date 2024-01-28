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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * The remote version.
 *
 * @author huangyuhui
 */
public class RemoteVersion implements Comparable<RemoteVersion> {

    private final String libraryId;
    private final String gameVersion;
    private final String selfVersion;
    private final Date releaseDate;
    private final List<String> urls;
    private final Type type;

    /**
     * Constructor.
     *
     * @param gameVersion the Minecraft version that this remote version suits.
     * @param selfVersion the version string of the remote version.
     * @param urls        the installer or universal jar original URL.
     */
    public RemoteVersion(String libraryId, String gameVersion, String selfVersion, Date releaseDate, List<String> urls) {
        this(libraryId, gameVersion, selfVersion, releaseDate, Type.UNCATEGORIZED, urls);
    }

    /**
     * Constructor.
     *
     * @param gameVersion the Minecraft version that this remote version suits.
     * @param selfVersion the version string of the remote version.
     * @param urls        the installer or universal jar URL.
     */
    public RemoteVersion(String libraryId, String gameVersion, String selfVersion, Date releaseDate, Type type, List<String> urls) {
        this.libraryId = Objects.requireNonNull(libraryId);
        this.gameVersion = Objects.requireNonNull(gameVersion);
        this.selfVersion = Objects.requireNonNull(selfVersion);
        this.releaseDate = releaseDate;
        this.urls = Objects.requireNonNull(urls);
        this.type = Objects.requireNonNull(type);
    }

    public String getLibraryId() {
        return libraryId;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getSelfVersion() {
        return selfVersion;
    }

    public String getFullVersion() {
        return getSelfVersion();
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public List<String> getUrls() {
        return urls;
    }

    public Type getVersionType() {
        return type;
    }

    public Task<Version> getInstallTask(DefaultDependencyManager dependencyManager, Version baseVersion) {
        throw new UnsupportedOperationException(this + " cannot be installed yet");
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RemoteVersion && Objects.equals(selfVersion, ((RemoteVersion) obj).selfVersion);
    }

    @Override
    public int hashCode() {
        return selfVersion.hashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("selfVersion", selfVersion)
                .append("gameVersion", gameVersion)
                .toString();
    }

    @Override
    public int compareTo(RemoteVersion o) {
        // newer versions are smaller than older versions
        return VersionNumber.asVersion(o.selfVersion).compareTo(VersionNumber.asVersion(selfVersion));
    }

    public enum Type {
        UNCATEGORIZED,
        RELEASE,
        SNAPSHOT,
        OLD
    }
}
