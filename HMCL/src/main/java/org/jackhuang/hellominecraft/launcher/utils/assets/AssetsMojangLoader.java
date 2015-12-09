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
package org.jackhuang.hellominecraft.launcher.utils.assets;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftProvider;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.launcher.utils.download.IDownloadProvider;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersion;
import org.jackhuang.hellominecraft.utils.VersionNumber;
import rx.Observable;
import rx.Observer;
import rx.subscriptions.Subscriptions;

/**
 *
 * @author huangyuhui
 */
public class AssetsMojangLoader extends IAssetsHandler {

    public AssetsMojangLoader(String name) {
        super(name);
    }

    @Override
    public Observable<String[]> getList(MinecraftVersion mv, IMinecraftProvider mp) {
        return Observable.<String[]>create((Observer<String[]> t1) -> {
            if (mv == null) {
                t1.onError(null);
                return Subscriptions.empty();
            }
            String assetsId = mv.assets == null ? "legacy" : mv.assets;
            File assets = mp.getAssetService().getAssets();
            HMCLog.log("Get index: " + assetsId);
            File f = IOUtils.tryGetCanonicalFile(new File(assets, "indexes/" + assetsId + ".json"));
            if (!f.exists() && !mp.getAssetService().downloadMinecraftAssetsIndex(assetsId)) {
                t1.onError(null);
                return Subscriptions.empty();
            }

            String result;
            try {
                result = FileUtils.readFileToString(f);
            } catch (IOException ex) {
                HMCLog.warn("Failed to read index json: " + f, ex);
                t1.onError(null);
                return Subscriptions.empty();
            }
            if (StrUtils.isBlank(result)) {
                HMCLog.err("Index json is empty, please redownload it!");
                t1.onError(null);
                return Subscriptions.empty();
            }
            AssetsIndex o;
            try {
                o = C.gson.fromJson(result, AssetsIndex.class);
            } catch (Exception e) {
                HMCLog.err("Failed to parse index json, please redownload it!", e);
                t1.onError(null);
                return Subscriptions.empty();
            }
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

            t1.onNext(al.toArray(new String[1]));
            t1.onCompleted();
            return Subscriptions.empty();
        });
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
