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
package org.jackhuang.hmcl.download.optifine;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.util.*;

/**
 *
 * @author huangyuhui
 */
public final class OptiFineBMCLVersionList extends VersionList<OptiFineRemoteVersion> {
    private final String apiRoot;

    /**
     * @param apiRoot API Root of BMCLAPI implementations
     */
    public OptiFineBMCLVersionList(String apiRoot) {
        this.apiRoot = apiRoot;
    }

    @Override
    public boolean hasType() {
        return true;
    }

    @Override
    public Task<?> refreshAsync() {
        GetTask task = new GetTask(NetworkUtils.toURL(apiRoot + "/optifine/versionlist"));
        return new Task<Void>() {
            @Override
            public Collection<Task<?>> getDependents() {
                return Collections.singleton(task);
            }

            @Override
            public void execute() {
                lock.writeLock().lock();

                try {
                    versions.clear();
                    Set<String> duplicates = new HashSet<>();
                    List<OptiFineVersion> root = JsonUtils.GSON.fromJson(task.getResult(), new TypeToken<List<OptiFineVersion>>() {
                    }.getType());
                    for (OptiFineVersion element : root) {
                        String version = element.getType() + "_" + element.getPatch();
                        String mirror = "https://bmclapi2.bangbang93.com/optifine/" + element.getGameVersion() + "/" + element.getType() + "/" + element.getPatch();
                        if (!duplicates.add(mirror))
                            continue;

                        boolean isPre = element.getPatch() != null && (element.getPatch().startsWith("pre") || element.getPatch().startsWith("alpha"));

                        if (StringUtils.isBlank(element.getGameVersion()))
                            continue;

                        String gameVersion = VersionNumber.normalize(element.getGameVersion());
                        versions.put(gameVersion, new OptiFineRemoteVersion(gameVersion, version, Collections.singletonList(mirror), isPre));
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }
        };
    }

}
