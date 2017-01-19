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
package org.jackhuang.hellominecraft.launcher.core.asset;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftAssetService;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.system.IOUtils;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.launcher.core.download.IDownloadProvider;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.util.VersionNumber;
import org.jackhuang.hellominecraft.util.tasks.TaskInfo;

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
        File assets = mp.getAssets();
        HMCLog.log("Gathering asset index: " + assetsId);
        File f = IOUtils.tryGetCanonicalFile(new File(assets, "indexes/" + assetsId + ".json"));
        return new TaskInfo("Gather asset index") {
            @Override
            public Collection<Task> getDependTasks() {
                if (!f.exists())
                    return Arrays.asList(mp.downloadMinecraftAssetsIndex(mv.getAssetsIndex()));
                else
                    return null;
            }

            @Override
            public void executeTask(boolean areDependTasksSucceeded) throws Throwable {
                if (!areDependTasksSucceeded)
                    throw new IllegalStateException("Failed to get asset index");
                String result = FileUtils.read(f);
                if (StrUtils.isBlank(result))
                    throw new IllegalStateException("Index json is empty, please redownload it!");
                AssetsIndex o = C.GSON.fromJson(result, AssetsIndex.class);
                assetsDownloadURLs = new ArrayList<>();
                assetsLocalNames = new ArrayList<>();
                contents = new ArrayList<>();
                HashSet<String> loadedHashes = new HashSet<>();
                int pgs = 0;
                if (o != null && o.getFileMap() != null)
                    for (Map.Entry<String, AssetsObject> e : o.getFileMap().entrySet()) {
                        if (loadedHashes.contains(e.getValue().getHash()))
                            continue;
                        loadedHashes.add(e.getValue().getHash());
                        Contents c = new Contents();
                        c.eTag = e.getValue().getHash();
                        c.key = c.eTag.substring(0, 2) + "/" + e.getValue().getHash();
                        c.size = e.getValue().getSize();
                        contents.add(c);
                        assetsDownloadURLs.add(c.key);
                        assetsLocalNames.add(new File(assets, "objects" + File.separator + c.key.replace("/", File.separator)));
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
        VersionNumber ur = VersionNumber.check(formattedVersion);
        if (ur == null)
            return false;
        return VersionNumber.check("1.6.0").compareTo(ur) <= 0;
    }
}
