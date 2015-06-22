/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.utils.settings.Profile;
import org.jackhuang.hellominecraft.utils.IOUtils;
import org.jackhuang.hellominecraft.launcher.utils.MCUtils;
import org.jackhuang.hellominecraft.launcher.utils.assets.AssetsIndex;
import org.jackhuang.hellominecraft.launcher.utils.assets.AssetsObject;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.utils.OS;
import org.jackhuang.hellominecraft.launcher.utils.version.MinecraftLibrary;
import org.jackhuang.hellominecraft.launcher.utils.version.MinecraftVersion;
import org.jackhuang.hellominecraft.utils.FileUtils;
import org.jackhuang.hellominecraft.utils.MessageBox;

/**
 *
 * @author hyh
 */
public class MinecraftLoader extends IMinecraftLoader {

    private MinecraftVersion version;
    String text;

    public MinecraftLoader(Profile ver, IMinecraftProvider provider, UserProfileProvider lr, File minecraftJar) throws IllegalStateException {
        this(ver, provider, lr, minecraftJar, DownloadType.Mojang);
    }

    public MinecraftLoader(Profile ver, IMinecraftProvider provider, UserProfileProvider lr, File minecraftJar, DownloadType downloadtype) throws IllegalStateException {
        super(ver, provider, lr, minecraftJar);
        version = ver.getSelectedMinecraftVersion().resolve(provider, downloadtype);
    }

    @Override
    protected void makeSelf(List<String> res) {
        String library = v.isCanceledWrapper() ? "" : "-cp=";
        for (MinecraftLibrary l : version.libraries) {
            l.init();
            if (l.allow())
                library += l.getFilePath(gameDir).getAbsolutePath() + File.pathSeparator;
        }
        library += IOUtils.tryGetCanonicalFilePath(minecraftJar) + File.pathSeparator;
        library = library.substring(0, library.length() - File.pathSeparator.length());
        if (v.isCanceledWrapper()) res.add("-cp");
        res.add(library);
        String mainClass = version.mainClass;
        res.add((v.isCanceledWrapper() ? "" : "-mainClass=") + mainClass);

        String arg = v.getSelectedMinecraftVersion().minecraftArguments;
        String[] splitted = org.jackhuang.hellominecraft.utils.StrUtils.tokenize(arg);

        if (!new File(v.getGameDirFile(), "assets").exists())
            MessageBox.Show(C.i18n("assets.no_assets"));

        String game_assets = reconstructAssets().getAbsolutePath();

        for (String t : splitted) {
            t = t.replace("${auth_player_name}", lr.getUserName());
            t = t.replace("${auth_session}", lr.getSession());
            t = t.replace("${auth_uuid}", lr.getUserId());
            t = t.replace("${version_name}", version.id);
            t = t.replace("${profile_name}", provider.profile.getName());
            t = t.replace("${game_directory}", provider.getRunDirectory(version.id).getAbsolutePath());
            t = t.replace("${game_assets}", game_assets);
            t = t.replace("${assets_root}", provider.getAssets().getAbsolutePath());
            t = t.replace("${auth_access_token}", lr.getAccessToken());
            t = t.replace("${user_type}", lr.getUserType());
            t = t.replace("${assets_index_name}", version.assets == null ? "legacy" : version.assets);
            t = t.replace("${user_properties}", lr.getUserProperties());
            t = t.replace("${user_property_map}", lr.getUserPropertyMap());
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
                list.add("-Xdock:icon=" + MCUtils.getAssetObject(C.gson, v.getCanonicalGameDir(), version.assets, "icons/minecraft.icns").getAbsolutePath());
                list.add("-Xdock:name=Minecraft");
            }
        } catch (IOException e) {
            HMCLog.err("Failed to append jvm arguments when searching for asset objects.", e);
        }
    }

    private File reconstructAssets() {
        File assetsDir = new File(provider.getBaseFolder(), "assets");
        File indexDir = new File(assetsDir, "indexes");
        File objectDir = new File(assetsDir, "objects");
        String assetVersion = version.assets == null ? "legacy" : version.assets;
        File indexFile = new File(indexDir, assetVersion + ".json");
        File virtualRoot = new File(new File(assetsDir, "virtual"), assetVersion);

        if (!indexFile.isFile()) {
            HMCLog.warn("No assets index file " + virtualRoot + "; can't reconstruct assets");
            return virtualRoot;
        }

        try {
            AssetsIndex index = (AssetsIndex) C.gson.fromJson(FileUtils.readFileToString(indexFile, "UTF-8"), AssetsIndex.class);

            if (index.isVirtual()) {
                HMCLog.log("Reconstructing virtual assets folder at " + virtualRoot);
                for (Map.Entry entry : index.getFileMap().entrySet()) {
                    File target = new File(virtualRoot, (String) entry.getKey());
                    File original = new File(new File(objectDir, ((AssetsObject) entry.getValue()).getHash().substring(0, 2)), ((AssetsObject) entry.getValue()).getHash());

                    if (!target.isFile())
                        FileUtils.copyFile(original, target, false);
                }
            }
        } catch (IOException e) {
            HMCLog.warn("Failed to create virutal assets.", e);
        }

        return virtualRoot;
    }
}
