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
package org.jackhuang.hmcl.download.optifine;

import com.google.gson.reflect.TypeToken;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.NetworkUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.VersionNumber;

/**
 *
 * @author huangyuhui
 */
public final class OptiFineBMCLVersionList extends VersionList<Void> {

    public static final OptiFineBMCLVersionList INSTANCE = new OptiFineBMCLVersionList();

    private OptiFineBMCLVersionList() {
    }

    @Override
    public Task refreshAsync(DownloadProvider downloadProvider) {
        GetTask task = new GetTask(NetworkUtils.toURL("http://bmclapi.bangbang93.com/optifine/versionlist"));
        return new Task() {
            @Override
            public Collection<Task> getDependents() {
                return Collections.singleton(task);
            }

            @Override
            public void execute() throws Exception {
                versions.clear();
                Set<String> duplicates = new HashSet<>();
                List<OptiFineVersion> root = Constants.GSON.fromJson(task.getResult(), new TypeToken<List<OptiFineVersion>>() {
                }.getType());
                for (OptiFineVersion element : root) {
                    String version = element.getType();
                    if (version == null)
                        continue;
                    String mirror = "http://bmclapi2.bangbang93.com/optifine/" + element.getGameVersion() + "/" + element.getType() + "/" + element.getPatch();
                    if (!duplicates.add(mirror))
                        continue;

                    if (StringUtils.isBlank(element.getGameVersion()))
                        continue;
                    String gameVersion = VersionNumber.parseVersion(element.getGameVersion());
                    versions.put(gameVersion, new RemoteVersion<>(gameVersion, version, mirror, null));
                }
            }
        };
    }

}
