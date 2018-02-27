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

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.SimpleMultimap;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The remote version list.
 *
 * @param <T> The type of {@code RemoteVersion<T>}, the type of tags.
 *
 * @author huangyuhui
 */
public abstract class VersionList<T> {

    /**
     * the remote version list.
     * key: game version.
     * values: corresponding remote versions.
     */
    protected final SimpleMultimap<String, RemoteVersion<T>> versions = new SimpleMultimap<String, RemoteVersion<T>>(HashMap::new, TreeSet::new);

    /**
     * True if the version list has been loaded.
     */
    public boolean isLoaded() {
        return !versions.isEmpty();
    }

    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * @param downloadProvider DownloadProvider
     * @return the task to reload the remote version list.
     */
    public abstract Task refreshAsync(DownloadProvider downloadProvider);

    public Task loadAsync(DownloadProvider downloadProvider) {
        return Task.ofThen(variables -> {
            lock.readLock().lock();
            boolean loaded;

            try {
                loaded = isLoaded();
            } finally {
                lock.readLock().unlock();
            }
            return loaded ? null : refreshAsync(downloadProvider);
        });
    }

    private Collection<RemoteVersion<T>> getVersionsImpl(String gameVersion) {
        lock.readLock().lock();
        try {
            Collection<RemoteVersion<T>> ans = versions.get(gameVersion);
            return ans.isEmpty() ? versions.values() : ans;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the remote versions that specifics Minecraft version.
     *
     * @param gameVersion the Minecraft version that remote versions belong to
     * @return the collection of specific remote versions
     */
    public final Collection<RemoteVersion<T>> getVersions(String gameVersion) {
        return Collections.unmodifiableCollection(getVersionsImpl(gameVersion));
    }

    /**
     * Get the specific remote version.
     *
     * @param gameVersion the Minecraft version that remote versions belong to
     * @param remoteVersion the version of the remote version.
     * @return the specific remote version, null if it is not found.
     */
    public final Optional<RemoteVersion<T>> getVersion(String gameVersion, String remoteVersion) {
        lock.readLock().lock();
        try {
            RemoteVersion<T> result = null;
            for (RemoteVersion<T> it : versions.get(gameVersion))
                if (remoteVersion.equals(it.getSelfVersion()))
                    result = it;
            return Optional.ofNullable(result);
        } finally {
            lock.readLock().unlock();
        }
    }
}
