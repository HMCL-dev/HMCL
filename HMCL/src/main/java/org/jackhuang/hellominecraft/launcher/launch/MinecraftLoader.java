/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.launcher.launch;

import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.launcher.utils.assets.AssetsIndex;
import org.jackhuang.hellominecraft.launcher.utils.assets.AssetsObject;
import org.jackhuang.hellominecraft.launcher.utils.assets.IAssetsHandler;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.utils.system.OS;
import org.jackhuang.hellominecraft.launcher.version.MinecraftLibrary;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.StrUtils;

/**
 *
 * @author huangyuhui
 */
public class MinecraftLoader extends AbstractMinecraftLoader {

    DownloadType dt;
    String text;

    public MinecraftLoader(IMinecraftService provider, UserProfileProvider lr) throws GameException {
        super(provider.profile, provider, lr);
    }

    @Override
    protected void makeSelf(List<String> res) {
        String library = v.isCanceledWrapper() ? "" : "-cp=";
        for (MinecraftLibrary l : version.libraries) {
            l.init();
            if (l.allow() && !l.isRequiredToUnzip())
                library += l.getFilePath(gameDir).getAbsolutePath() + File.pathSeparator;
        }
        library += IOUtils.tryGetCanonicalFilePath(version.getJar(v.getCanonicalGameDirFile())) + File.pathSeparator;
        library = library.substring(0, library.length() - File.pathSeparator.length());
        if (v.isCanceledWrapper())
            res.add("-cp");
        res.add(library);
        String mainClass = version.mainClass;
        res.add((v.isCanceledWrapper() ? "" : "-mainClass=") + mainClass);

        String arg = service.version().getSelectedVersion().minecraftArguments;
        String[] splitted = StrUtils.tokenize(arg);

        if (!checkAssetsExist())
            if (MessageBox.Show(C.i18n("assets.no_assets"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION) {
                IAssetsHandler.ASSETS_HANDLER.getList(version, service.asset()).subscribe(a -> {
                });
                TaskWindow.getInstance().addTask(IAssetsHandler.ASSETS_HANDLER.getDownloadTask(service.getDownloadType().getProvider())).start();
            }

        String game_assets = reconstructAssets().getAbsolutePath();

        for (String t : splitted) {
            t = t.replace("${auth_player_name}", lr.getUserName());
            t = t.replace("${auth_session}", lr.getSession());
            t = t.replace("${auth_uuid}", lr.getUserId());
            t = t.replace("${version_name}", Main.makeTitle());
            t = t.replace("${profile_name}", v.getName());
            t = t.replace("${game_directory}", service.version().getRunDirectory(version.id).getAbsolutePath());
            t = t.replace("${game_assets}", game_assets);
            t = t.replace("${assets_root}", service.asset().getAssets().getAbsolutePath());
            t = t.replace("${auth_access_token}", lr.getAccessToken());
            t = t.replace("${user_type}", lr.getUserType());
            t = t.replace("${assets_index_name}", version.getAssets());
            t = t.replace("${user_properties}", lr.getUserProperties());
            res.add(t);
        }

        if (res.indexOf("--gameDir") != -1 && res.indexOf("--workDir") != -1) {
            res.add("--workDir");
            res.add(gameDir.getAbsolutePath());
        }
    }

    @Override
    protected void appendJVMArgs(List<String> list) {
        super.appendJVMArgs(list);

        try {
            if (OS.os() == OS.OSX) {
                list.add("-Xdock:icon=" + service.asset().getAssetObject(version.assets, "icons/minecraft.icns").getAbsolutePath());
                list.add("-Xdock:name=Minecraft");
            }
        } catch (IOException e) {
            HMCLog.err("Failed to append jvm arguments when searching for asset objects.", e);
        }
    }

    private boolean checkAssetsExist() {
        File assetsDir = new File(service.baseFolder, "assets");
        File indexDir = new File(assetsDir, "indexes");
        File objectDir = new File(assetsDir, "objects");
        File indexFile = new File(indexDir, version.getAssets() + ".json");

        if (!assetsDir.exists() && !indexFile.isFile())
            return false;

        try {
            AssetsIndex index = (AssetsIndex) C.gson.fromJson(FileUtils.readFileToString(indexFile, "UTF-8"), AssetsIndex.class);

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

    private File reconstructAssets() {
        File assetsDir = new File(service.baseFolder, "assets");
        File indexDir = new File(assetsDir, "indexes");
        File objectDir = new File(assetsDir, "objects");
        String assetVersion = version.getAssets();
        File indexFile = new File(indexDir, assetVersion + ".json");
        File virtualRoot = new File(new File(assetsDir, "virtual"), assetVersion);

        if (!indexFile.isFile()) {
            HMCLog.warn("No assets index file " + virtualRoot + "; can't reconstruct assets");
            return assetsDir;
        }

        try {
            AssetsIndex index = (AssetsIndex) C.gson.fromJson(FileUtils.readFileToString(indexFile, "UTF-8"), AssetsIndex.class);

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
}
