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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.util.VersionNumber;

import java.util.Comparator;
import java.util.Objects;

/**
 * The remote version.
 *
 * @author huangyuhui
 */
public final class RemoteVersion<T> implements Comparable<RemoteVersion<T>> {

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
    public int compareTo(RemoteVersion<T> o) {
        // newer versions are smaller than older versions
        return -selfVersion.compareTo(o.selfVersion);
    }

    public static class RemoteVersionComparator implements Comparator<RemoteVersion<?>> {

        public static final RemoteVersionComparator INSTANCE = new RemoteVersionComparator();

        private RemoteVersionComparator() {
        }

        @Override
        public int compare(RemoteVersion<?> o1, RemoteVersion<?> o2) {
            return -VersionNumber.asVersion(o1.selfVersion).compareTo(VersionNumber.asVersion(o2.selfVersion));
        }

    }
}
