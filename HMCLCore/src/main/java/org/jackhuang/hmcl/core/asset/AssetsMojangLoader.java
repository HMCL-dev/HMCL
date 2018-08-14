/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.core.asset;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.core.service.IMinecraftAssetService;
import org.jackhuang.hmcl.util.task.Task;
import org.jackhuang.hmcl.util.sys.FileUtils;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.core.download.IDownloadProvider;
import org.jackhuang.hmcl.core.version.MinecraftVersion;
import org.jackhuang.hmcl.api.VersionNumber;
import org.jackhuang.hmcl.util.task.TaskInfo;

/**
 *
 * @author huangyuhui
 */
public class AssetsMojangLoader extends IAssetsHandler {

    public AssetsMojangLoader(String name) {
        super(name);
    }

    @Override
    public Task getList(final MinecraftVersion mv, final IMinecraftAssetService mp) {
        Objects.requireNonNull(mv);
        String assetsId = mv.getAssetsIndex().getId();
        HMCLog.log("Gathering asset index: " + assetsId);
        File f = mp.getIndexFile(assetsId);
        return new TaskInfo("Gather asset index") {
            @Override
            public Collection<Task> getDependTasks() {
                if (!f.exists())
                    return Arrays.asList(mp.downloadMinecraftAssetsIndex(mv.getAssetsIndex()));
                else
                    return null;
            }

            @Override
            public void executeTask(boolean areDependTasksSucceeded) throws Exception {
                if (!areDependTasksSucceeded)
                    throw new IllegalStateException("Failed to get asset index");
                String result = FileUtils.read(f);
                if (StrUtils.isBlank(result))
                    throw new IllegalStateException("Index json is empty, please redownload it!");
                AssetsIndex o = C.GSON.fromJson(result, AssetsIndex.class);
                assetsDownloadURLs = new ArrayList<>();
                assetsLocalNames = new ArrayList<>();
                assetsObjects = new ArrayList<>();
                HashSet<String> loadedHashes = new HashSet<>();
                int pgs = 0;
                if (o != null && o.getFileMap() != null)
                    for (Map.Entry<String, AssetsObject> e : o.getFileMap().entrySet()) {
                        if (loadedHashes.contains(e.getValue().getHash()))
                            continue;
                        loadedHashes.add(e.getValue().getHash());
                        assetsObjects.add(e.getValue());
                        assetsDownloadURLs.add(e.getValue().getLocation());
                        assetsLocalNames.add(mp.getAssetObject(assetsId, e.getValue()));
                        if (ppl != null)
                            ppl.setProgress(this, ++pgs, o.getFileMap().size());
                    }
            }
        };
    }

    @Override
    public Task getDownloadTask(IDownloadProvider sourceType) {
        return new AssetsTask(sourceType.getAssetsDownloadURL());
    }

    @Override
    public boolean isVersionAllowed(String formattedVersion) {
        VersionNumber ur = VersionNumber.asVersion(formattedVersion);
        if (ur == null)
            return false;
        return VersionNumber.asVersion("1.6.0").compareTo(ur) <= 0;
    }
}
