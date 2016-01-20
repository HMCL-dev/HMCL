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

import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftAssetService;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import rx.concurrency.Schedulers;

/**
 *
 * @author huangyuhui
 */
public class MinecraftAssetService extends IMinecraftAssetService {

    public MinecraftAssetService(IMinecraftService service) {
        super(service);
    }

    @Override
    public Task downloadAssets(String mcVersion) {
        return new Task() {

            @Override
            public void executeTask() throws Throwable {
                IAssetsHandler type = IAssetsHandler.ASSETS_HANDLER;
                type.getList(service.version().getVersionById(mcVersion), service.asset())
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(Schedulers.eventQueue())
                    .subscribe((t) -> TaskWindow.getInstance().addTask(type.getDownloadTask(service.getDownloadType().getProvider())).start());
            }

            @Override
            public String getInfo() {
                return "Download Assets";
            }
        };
    }

    @Override
    public boolean refreshAssetsIndex(String id) {
        MinecraftVersion mv = service.version().getVersionById(id);
        if (mv == null)
            return false;
        return downloadMinecraftAssetsIndex(mv.assets);
    }

    @Override
    public boolean downloadMinecraftAssetsIndex(String assetsId) {
        String aurl = service.getDownloadType().getProvider().getIndexesDownloadURL();

        File assetsLocation = getAssets();
        assetsLocation.mkdirs();
        File assetsIndex = new File(assetsLocation, "indexes/" + assetsId + ".json");
        File renamed = null;
        if (assetsIndex.exists()) {
            renamed = new File(assetsLocation, "indexes/" + assetsId + "-renamed.json");
            assetsIndex.renameTo(renamed);
        }
        if (TaskWindow.getInstance()
            .addTask(new FileDownloadTask(aurl + assetsId + ".json", IOUtils.tryGetCanonicalFile(assetsIndex)).setTag(assetsId + ".json"))
            .start()) {
            if (renamed != null)
                renamed.delete();
            return true;
        }
        if (renamed != null)
            renamed.renameTo(assetsIndex);
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
            AssetsIndex index = (AssetsIndex) C.gson.fromJson(FileUtils.readFileToString(indexFile, "UTF-8"), AssetsIndex.class);

            String hash = ((AssetsObject) index.getFileMap().get(name)).getHash();
            return new File(objectsDir, hash.substring(0, 2) + "/" + hash);
        } catch (JsonSyntaxException e) {
            throw new IOException("Assets file format malformed.", e);
        }
    }
}
