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

import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.core.launch.IAssetProvider;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.core.service.IMinecraftAssetService;
import org.jackhuang.hmcl.core.service.IMinecraftService;
import org.jackhuang.hmcl.core.version.AssetIndexDownloadInfo;
import org.jackhuang.hmcl.core.version.MinecraftVersion;
import org.jackhuang.hmcl.util.MessageBox;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.util.task.Task;
import org.jackhuang.hmcl.util.task.TaskWindow;
import org.jackhuang.hmcl.util.net.FileDownloadTask;
import org.jackhuang.hmcl.util.sys.FileUtils;
import org.jackhuang.hmcl.util.task.TaskInfo;

/**
 *
 * @author huangyuhui
 */
public class MinecraftAssetService extends IMinecraftAssetService {

    public MinecraftAssetService(IMinecraftService service) {
        super(service);
    }

    @Override
    public Task downloadAssets(final String mcVersion) throws GameException {
        return downloadAssets(service.version().getVersionById(mcVersion));
    }

    public Task downloadAssets(final MinecraftVersion mv) throws GameException {
        if (mv == null)
            return null;
        return IAssetsHandler.ASSETS_HANDLER.getList(mv.resolve(service.version()), service.asset()).with(IAssetsHandler.ASSETS_HANDLER.getDownloadTask(service.getDownloadType().getProvider()));
    }

    @Override
    public boolean refreshAssetsIndex(String id) throws GameException {
        MinecraftVersion mv = service.version().getVersionById(id);
        if (mv == null)
            return false;
        return downloadMinecraftAssetsIndexAsync(mv.resolve(service.version()).getAssetsIndex());
    }

    @Override
    public Task downloadMinecraftAssetsIndex(AssetIndexDownloadInfo assetIndex) {
        File assetsLocation = getAssets(assetIndex.getId());
        if (!FileUtils.makeDirectory(assetsLocation))
            HMCLog.warn("Failed to make directories: " + assetsLocation);
        File assetsIndex = getIndexFile(assetIndex.getId());
        File renamed = null;
        if (assetsIndex.exists()) {
            renamed = new File(assetsLocation, "indexes/" + assetIndex.getId() + "-renamed.json");
            if (assetsIndex.renameTo(renamed))
                HMCLog.warn("Failed to rename " + assetsIndex + " to " + renamed);
        }
        File renamedFinal = renamed;
        return new TaskInfo("Download Asset Index") {
            @Override
            public Collection<Task> getDependTasks() {
                return Arrays.asList(new FileDownloadTask(assetIndex.getUrl(service.getDownloadType()), assetsIndex, assetIndex.sha1).setTag(assetIndex.getId() + ".json"));
            }

            @Override
            public void executeTask(boolean areDependTasksSucceeded) {
                if (areDependTasksSucceeded) {
                    if (renamedFinal != null && !renamedFinal.delete())
                        HMCLog.warn("Failed to delete " + renamedFinal + ", maybe you should do it.");
                } else if (renamedFinal != null && !renamedFinal.renameTo(assetsIndex))
                    HMCLog.warn("Failed to rename " + renamedFinal + " to " + assetsIndex);
            }
        };
    }

    @Override
    public boolean downloadMinecraftAssetsIndexAsync(AssetIndexDownloadInfo assetIndex) {
        File assetsDir = getAssets(assetIndex.getId());
        if (!FileUtils.makeDirectory(assetsDir))
            HMCLog.warn("Failed to make directories: " + assetsDir);
        File assetsIndex = getIndexFile(assetIndex.getId());
        File renamed = null;
        if (assetsIndex.exists()) {
            renamed = new File(assetsDir, "indexes/" + assetIndex.getId() + "-renamed.json");
            if (assetsIndex.renameTo(renamed))
                HMCLog.warn("Failed to rename " + assetsIndex + " to " + renamed);
        }
        if (TaskWindow.factory()
                .append(new FileDownloadTask(assetIndex.getUrl(service.getDownloadType()), assetsIndex, assetIndex.sha1).setTag(assetIndex.getId() + ".json"))
                .execute()) {
            if (renamed != null && !renamed.delete())
                HMCLog.warn("Failed to delete " + renamed + ", maybe you should do it.");
            return true;
        }
        if (renamed != null && !renamed.renameTo(assetsIndex))
            HMCLog.warn("Failed to rename " + renamed + " to " + assetsIndex);
        return false;
    }

    @Override
    public File getAssets(String assetId) {
        return new File(service.baseDirectory(), "assets");
    }

    @Override
    public File getIndexFile(String assetId) {
        return new File(getAssets(assetId), "indexes/" + assetId + ".json");
    }

    @Override
    public File getAssetObject(String assetId, String name) throws IOException {
        try {
            AssetsIndex index = (AssetsIndex) C.GSON.fromJson(FileUtils.read(getIndexFile(assetId), "UTF-8"), AssetsIndex.class);
            if (index == null || index.getFileMap() == null || index.getFileMap().get(name) == null)
                throw new IOException("Assets file format malformed.");
            return getAssetObject(assetId, (AssetsObject) index.getFileMap().get(name));
        } catch (JsonSyntaxException e) {
            throw new IOException("Assets file format malformed.", e);
        }
    }

    protected boolean checkAssetsExistance(AssetIndexDownloadInfo assetIndex) {
        String assetId = assetIndex.getId();
        File indexFile = getIndexFile(assetId);
        File assetDir = getAssets(assetId);

        if (!getAssets(assetId).exists() || !indexFile.isFile())
            return false;

        try {
            String assetIndexContent = FileUtils.read(indexFile, "UTF-8");
            AssetsIndex index = (AssetsIndex) C.GSON.fromJson(assetIndexContent, AssetsIndex.class);

            if (index == null)
                return false;
            for (Map.Entry<String, AssetsObject> entry : index.getFileMap().entrySet())
                if (!assetObjectPath(assetDir, (AssetsObject) entry.getValue()).exists())
                    return false;
            return true;
        } catch (IOException | JsonSyntaxException e) {
            return false;
        }
    }

    protected File reconstructAssets(AssetIndexDownloadInfo assetIndex) {
        File assetsDir = getAssets(assetIndex.getId());
        String assetVersion = assetIndex.getId();
        File indexFile = getIndexFile(assetVersion);
        File virtualRoot = new File(new File(assetsDir, "virtual"), assetVersion);

        if (!indexFile.isFile()) {
            HMCLog.warn("No assets index file " + virtualRoot + "; can't reconstruct assets");
            return assetsDir;
        }

        try {
            String assetIndexContent = FileUtils.read(indexFile, "UTF-8");
            AssetsIndex index = (AssetsIndex) C.GSON.fromJson(assetIndexContent, AssetsIndex.class);

            if (index == null)
                return assetsDir;
            if (index.isVirtual()) {
                int cnt = 0;
                HMCLog.log("Reconstructing virtual assets folder at " + virtualRoot);
                int tot = index.getFileMap().entrySet().size();
                for (Map.Entry<String, AssetsObject> entry : index.getFileMap().entrySet()) {
                    File target = new File(virtualRoot, (String) entry.getKey());
                    File original = assetObjectPath(assetsDir, (AssetsObject) entry.getValue());
                    if (original.exists()) {
                        cnt++;
                        if (!target.isFile())
                            FileUtils.copyFile(original, target);
                    }
                }
                // If the scale new format existent file is lower then 0.1, use the old format.
                if (cnt * 10 < tot)
                    return assetsDir;
            }
        } catch (IOException | JsonSyntaxException e) {
            HMCLog.warn("Failed to create virutal assets.", e);
        }

        return virtualRoot;
    }

    @Override
    public File getAssetObject(String assetId, AssetsObject object) {
        return assetObjectPath(getAssets(assetId), object);
    }
    
    public File assetObjectPath(File assetDir, AssetsObject object) {
        return new File(assetDir, "objects/" + object.getLocation());
    }

    public final IAssetProvider ASSET_PROVIDER_IMPL = (t, allow) -> {
        if (allow && !checkAssetsExistance(t.getAssetsIndex()))
            if (MessageBox.show(C.i18n("assets.no_assets"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                TaskWindow.factory().execute(downloadAssets(t));
        return reconstructAssets(t.getAssetsIndex()).getAbsolutePath();
    };
}
