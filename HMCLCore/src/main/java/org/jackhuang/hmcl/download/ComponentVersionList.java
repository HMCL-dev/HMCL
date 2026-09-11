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

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.SimpleMultimap;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/// The remote version list.
///
/// @param <V> the type of ComponentRemoteVersion.
/// @author huangyuhui
public abstract class ComponentVersionList<V extends ComponentRemoteVersion> {

    /**
     * the remote version list.
     * key: game version.
     * values: corresponding remote versions.
     */
    protected final SimpleMultimap<String, V, TreeSet<V>> versions = new SimpleMultimap<>(HashMap::new, TreeSet::new);

    /**
     * True if the version list has been loaded.
     */
    public boolean isLoaded() {
        return !versions.isEmpty();
    }

    /**
     * True if the version list that contains the remote versions which depends on the specific game version has been loaded.
     *
     * @param gameVersion the remote version depends on
     */
    public boolean isLoaded(String gameVersion) {
        return !versions.get(gameVersion).isEmpty();
    }

    public abstract boolean hasType();

    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * @return the task to reload the remote version list.
     */
    public abstract Task<?> refreshAsync();

    /**
     * @param gameVersion the remote version depends on
     * @return the task to reload the remote version list.
     */
    public Task<?> refreshAsync(String gameVersion) {
        return refreshAsync();
    }

    public Task<?> loadAsync(String gameVersion) {
        return Task.composeAsync(() -> {
            lock.readLock().lock();
            try {
                return isLoaded(gameVersion) ? null : refreshAsync(gameVersion);
            } finally {
                lock.readLock().unlock();
            }
        });
    }

    protected Collection<V> getVersionsImpl(String gameVersion) {
        return versions.get(gameVersion);
    }

    /**
     * Get the remote versions that specifics Minecraft version.
     *
     * @param gameVersion the Minecraft version that remote versions belong to
     * @return the collection of specific remote versions
     */
    public final Collection<V> getVersions(String gameVersion) {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(new ArrayList<>(getVersionsImpl(gameVersion)));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the specific remote version.
     *
     * @param gameVersion   the Minecraft version that remote versions belong to
     * @param remoteVersion the version of the remote version.
     * @return the specific remote version, null if it is not found.
     */
    public Optional<V> getVersion(String gameVersion, String remoteVersion) {
        lock.readLock().lock();
        try {
            V result = null;
            TreeSet<V> remoteVersions = versions.get(gameVersion);
            for (V it : remoteVersions)
                if (remoteVersion.equals(it.getSelfVersion()))
                    result = it;
            if (result == null)
                for (V it : remoteVersions)
                    if (remoteVersion.equals(it.getFullVersion()))
                        result = it;
            return Optional.ofNullable(result);
        } finally {
            lock.readLock().unlock();
        }
    }
}
