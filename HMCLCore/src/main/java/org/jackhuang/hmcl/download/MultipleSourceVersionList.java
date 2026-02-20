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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.task.Task;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class MultipleSourceVersionList extends VersionList<RemoteVersion> {

    private final VersionList<?>[] backends;

    MultipleSourceVersionList(VersionList<?>[] backends) {
        this.backends = backends;

        assert (backends.length >= 1);
    }

    @Override
    public boolean hasType() {
        boolean hasType = backends[0].hasType();
        assert (Arrays.stream(backends).allMatch(versionList -> versionList.hasType() == hasType));
        return hasType;
    }

    @Override
    public Task<?> refreshAsync() {
        throw new UnsupportedOperationException("MultipleSourceVersionList does not support loading the entire remote version list.");
    }

    private Task<?> refreshAsync(String gameVersion, int sourceIndex) {
        VersionList<?> versionList = backends[sourceIndex];
        Task<?> refreshTask = versionList.refreshAsync(gameVersion);

        return new Task<Object>() {
            private Task<?> nextTask = null;

            {
                setSignificance(TaskSignificance.MODERATE);
                setName("MultipleSourceVersionList.refreshAsync(" + sourceIndex + ")");
            }

            @Override
            public Collection<Task<?>> getDependents() {
                return List.of(refreshTask);
            }

            @Override
            public Collection<? extends Task<?>> getDependencies() {
                return nextTask != null ? List.of(nextTask) : List.of();
            }

            @Override
            public boolean isRelyingOnDependents() {
                return false;
            }

            @Override
            public void execute() throws Exception {
                if (isDependentsSucceeded()) {
                    lock.writeLock().lock();
                    try {
                        versions.putAll(gameVersion, versionList.getVersions(gameVersion));
                    } finally {
                        lock.writeLock().unlock();
                    }

                    setResult(refreshTask.getResult());
                } else {
                    Exception exception = refreshTask.getException();
                    assert exception != null;

                    if (sourceIndex == backends.length - 1) {
                        LOG.warning("Failed to fetch versions list from all sources", exception);
                        setSignificance(TaskSignificance.MINOR);
                        throw exception;
                    } else {
                        LOG.warning("Failed to fetch versions list and try to fetch from other source", exception);
                        nextTask = refreshAsync(gameVersion, sourceIndex + 1);
                        nextTask.storeTo(this::setResult);
                    }
                }
            }
        };
    }

    @Override
    public Task<?> refreshAsync(String gameVersion) {
        versions.clear(gameVersion);
        return refreshAsync(gameVersion, 0);
    }
}
