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
package org.jackhuang.hellominecraft.launcher.core.assets;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import org.jackhuang.hellominecraft.utils.C;
import org.jackhuang.hellominecraft.utils.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftAssetService;
import org.jackhuang.hellominecraft.utils.tasks.Task;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.launcher.core.download.IDownloadProvider;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.utils.OverridableSwingWorker;
import org.jackhuang.hellominecraft.utils.VersionNumber;

/**
 *
 * @author huangyuhui
 */
public class AssetsMojangLoader extends IAssetsHandler {

    public AssetsMojangLoader(String name) {
        super(name);
    }

    @Override
    public OverridableSwingWorker<String[]> getList(final MinecraftVersion mv, final IMinecraftAssetService mp) {
        return new OverridableSwingWorker<String[]>() {
            @Override
            protected void work() throws Exception {
                if (mv == null)
                    throw new IllegalArgumentException("AssetsMojangLoader: null argument: MinecraftVersion");
                String assetsId = mv.assets == null ? "legacy" : mv.assets;
                File assets = mp.getAssets();
                HMCLog.log("Gathering asset index: " + assetsId);
                File f = IOUtils.tryGetCanonicalFile(new File(assets, "indexes/" + assetsId + ".json"));
                if (!f.exists() && !mp.downloadMinecraftAssetsIndex(assetsId))
                    throw new IllegalStateException("Failed to get index json");

                String result = FileUtils.readFileToString(f);
                if (StrUtils.isBlank(result))
                    throw new IllegalStateException("Index json is empty, please redownload it!");
                AssetsIndex o = C.gson.fromJson(result, AssetsIndex.class);
                assetsDownloadURLs = new ArrayList<>();
                assetsLocalNames = new ArrayList<>();
                ArrayList<String> al = new ArrayList<>();
                contents = new ArrayList<>();
                if (o != null && o.getFileMap() != null)
                    for (Map.Entry<String, AssetsObject> e : o.getFileMap().entrySet()) {
                        Contents c = new Contents();
                        c.eTag = e.getValue().getHash();
                        c.key = c.eTag.substring(0, 2) + "/" + e.getValue().getHash();
                        c.size = e.getValue().getSize();
                        contents.add(c);
                        assetsDownloadURLs.add(c.key);
                        assetsLocalNames.add(new File(assets, "objects" + File.separator + c.key.replace("/", File.separator)));
                        al.add(e.getKey());
                    }

                publish(al.toArray(new String[1]));
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
