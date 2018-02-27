/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.VersionNumber;

import java.util.Objects;

/**
 * The remote version.
 *
 * @author huangyuhui
 */
public class RemoteVersion<T> implements Comparable<RemoteVersion<T>> {

    private final String gameVersion;
    private final String selfVersion;
    private final String url;
    private final T tag;

    /**
     * Constructor.
     *
     * @param gameVersion the Minecraft version that this remote version suits.
     * @param selfVersion the version string of the remote version.
     * @param url the installer or universal jar URL.
     * @param tag some necessary information for Installer Task.
     */
    public RemoteVersion(String gameVersion, String selfVersion, String url, T tag) {
        this.gameVersion = Objects.requireNonNull(gameVersion);
        this.selfVersion = Objects.requireNonNull(selfVersion);
        this.url = Objects.requireNonNull(url);
        this.tag = tag;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getSelfVersion() {
        return selfVersion;
    }

    public T getTag() {
        return tag;
    }

    public String getUrl() {
        return url;
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
                .append("tag", tag)
                .toString();
    }

    @Override
    public int compareTo(RemoteVersion<T> o) {
        // newer versions are smaller than older versions
        return VersionNumber.asVersion(o.selfVersion).compareTo(VersionNumber.asVersion(selfVersion));
    }
}
