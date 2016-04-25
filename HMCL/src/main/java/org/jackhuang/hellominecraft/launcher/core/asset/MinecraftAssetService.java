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

import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftAssetService;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.version.AssetIndexDownloadInfo;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.func.BiFunction;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.tasks.TaskWindow;
import org.jackhuang.hellominecraft.util.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.system.IOUtils;
import org.jackhuang.hellominecraft.util.tasks.TaskInfo;

/**
 *
 * @author huangyuhui
 */
public class MinecraftAssetService extends IMinecraftAssetService {

    public MinecraftAssetService(IMinecraftService service) {
        super(service);
    }

    @Override
    public Task downloadAssets(final String mcVersion) {
        return downloadAssets(service.version().getVersionById(mcVersion));
    }

    public Task downloadAssets(final MinecraftVersion mv) {
        if (mv == null)
            return null;
        return IAssetsHandler.ASSETS_HANDLER.getList(mv, service.asset()).after(IAssetsHandler.ASSETS_HANDLER.getDownloadTask(service.getDownloadType().getProvider()));
    }

    @Override
    public boolean refreshAssetsIndex(String id) {
        MinecraftVersion mv = service.version().getVersionById(id);
        if (mv == null)
            return false;
        return downloadMinecraftAssetsIndexAsync(mv.getAssetsIndex());
    }

    @Override
    public Task downloadMinecraftAssetsIndex(AssetIndexDownloadInfo assets) {
        File assetsLocation = getAssets();
        if (!assetsLocation.exists() && !assetsLocation.mkdirs())
            HMCLog.warn("Failed to make directories: " + assetsLocation);
        File assetsIndex = new File(assetsLocation, "indexes/" + assets.getId() + ".json");
        File renamed = null;
        if (assetsIndex.exists()) {
            renamed = new File(assetsLocation, "indexes/" + assets.getId() + "-renamed.json");
            if (assetsIndex.renameTo(renamed))
                HMCLog.warn("Failed to rename " + assetsIndex + " to " + renamed);
        }
        File renamedFinal = renamed;
        return new TaskInfo("Download Asset Index") {
            @Override
            public Collection<Task> getDependTasks() {
                return Arrays.asList(new FileDownloadTask(assets.getUrl(service.getDownloadType()), IOUtils.tryGetCanonicalFile(assetsIndex), assets.sha1).setTag(assets.getId() + ".json"));
            }

            @Override
            public void executeTask() throws Throwable {
                if (areDependTasksSucceeded) {
                    if (renamedFinal != null && !renamedFinal.delete())
                        HMCLog.warn("Failed to delete " + renamedFinal + ", maybe you should do it.");
                } else if (renamedFinal != null && !renamedFinal.renameTo(assetsIndex))
                    HMCLog.warn("Failed to rename " + renamedFinal + " to " + assetsIndex);
            }
        };
    }

    @Override
    public boolean downloadMinecraftAssetsIndexAsync(AssetIndexDownloadInfo assets) {
        File assetsLocation = getAssets();
        if (!assetsLocation.exists() && !assetsLocation.mkdirs())
            HMCLog.warn("Failed to make directories: " + assetsLocation);
        File assetsIndex = new File(assetsLocation, "indexes/" + assets.getId() + ".json");
        File renamed = null;
        if (assetsIndex.exists()) {
            renamed = new File(assetsLocation, "indexes/" + assets.getId() + "-renamed.json");
            if (assetsIndex.renameTo(renamed))
                HMCLog.warn("Failed to rename " + assetsIndex + " to " + renamed);
        }
        if (TaskWindow.factory()
            .append(new FileDownloadTask(assets.getUrl(service.getDownloadType()), IOUtils.tryGetCanonicalFile(assetsIndex), assets.sha1).setTag(assets.getId() + ".json"))
            .create()) {
            if (renamed != null && !renamed.delete())
                HMCLog.warn("Failed to delete " + renamed + ", maybe you should do it.");
            return true;
        }
        if (renamed != null && !renamed.renameTo(assetsIndex))
            HMCLog.warn("Failed to rename " + renamed + " to " + assetsIndex);
        return false;
    }

    @Override
    public File getAssets() {
        return new File(service.baseDirectory(), "assets");
    }

    @Override
    public File getAssetObject(String assetVersion, String name) throws IOException {
        File assetsDir = getAssets();
        File indexDir = new File(assetsDir, "indexes");
        File objectsDir = new File(assetsDir, "objects");
        File indexFile = new File(indexDir, assetVersion + ".json");
        try {
            AssetsIndex index = (AssetsIndex) C.GSON.fromJson(FileUtils.readFileToString(indexFile, "UTF-8"), AssetsIndex.class);

            String hash = ((AssetsObject) index.getFileMap().get(name)).getHash();
            return new File(objectsDir, hash.substring(0, 2) + "/" + hash);
        } catch (JsonSyntaxException e) {
            throw new IOException("Assets file format malformed.", e);
        }
    }

    private boolean checkAssetsExistance(AssetIndexDownloadInfo assetIndex) {
        File assetsDir = getAssets();
        File indexDir = new File(assetsDir, "indexes");
        File objectDir = new File(assetsDir, "objects");
        File indexFile = new File(indexDir, assetIndex.getId() + ".json");

        if (!assetsDir.exists() || !indexFile.isFile())
            return false;

        try {
            String assetIndexContent = FileUtils.readFileToString(indexFile, "UTF-8");
            AssetsIndex index = (AssetsIndex) C.GSON.fromJson(assetIndexContent, AssetsIndex.class);

            if (index == null)
                return false;
            for (Map.Entry entry : index.getFileMap().entrySet())
                if (!new File(new File(objectDir, ((AssetsObject) entry.getValue()).getHash().substring(0, 2)), ((AssetsObject) entry.getValue()).getHash()).exists())
                    return false;
            return true;
        } catch (IOException | JsonSyntaxException e) {
            return false;
        }
    }

    private File reconstructAssets(AssetIndexDownloadInfo assetIndex) {
        File assetsDir = getAssets();
        File indexDir = new File(assetsDir, "indexes");
        File objectDir = new File(assetsDir, "objects");
        String assetVersion = assetIndex.getId();
        File indexFile = new File(indexDir, assetVersion + ".json");
        File virtualRoot = new File(new File(assetsDir, "virtual"), assetVersion);

        if (!indexFile.isFile()) {
            HMCLog.warn("No assets index file " + virtualRoot + "; can't reconstruct assets");
            return assetsDir;
        }

        try {
            String assetIndexContent = FileUtils.readFileToString(indexFile, "UTF-8");
            AssetsIndex index = (AssetsIndex) C.GSON.fromJson(assetIndexContent, AssetsIndex.class);

            if (index == null)
                return assetsDir;
            if (index.isVirtual()) {
                int cnt = 0;
                HMCLog.log("Reconstructing virtual assets folder at " + virtualRoot);
                int tot = index.getFileMap().entrySet().size();
                for (Map.Entry entry : index.getFileMap().entrySet()) {
                    File target = new File(virtualRoot, (String) entry.getKey());
                    File original = new File(new File(objectDir, ((AssetsObject) entry.getValue()).getHash().substring(0, 2)), ((AssetsObject) entry.getValue()).getHash());
                    if (original.exists()) {
                        cnt++;
                        if (!target.isFile())
                            FileUtils.copyFile(original, target, false);
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

    public final BiFunction<MinecraftVersion, Boolean, String> ASSET_PROVIDER_IMPL = (t, allow) -> {
        if (allow && !checkAssetsExistance(t.getAssetsIndex()))
            if (MessageBox.Show(C.i18n("assets.no_assets"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                TaskWindow.execute(downloadAssets(t));
        return reconstructAssets(t.getAssetsIndex()).getAbsolutePath();
    };
}
