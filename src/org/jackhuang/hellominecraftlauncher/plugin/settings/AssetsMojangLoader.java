/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.plugin.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import org.jackhuang.hellominecraftlauncher.apis.DoneListener;
import org.jackhuang.hellominecraftlauncher.apis.DownloadWindow;
import org.jackhuang.hellominecraftlauncher.apis.handlers.IAssetsHandler;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.plugin.HMCLPlugin;
import org.jackhuang.hellominecraftlauncher.plugin.settings.assets.Contents;
import org.jackhuang.hellominecraftlauncher.plugin.settings.assets.NewAssetsLoader;
import org.jackhuang.hellominecraftlauncher.plugin.settings.assets.Objects;
import org.jackhuang.hellominecraftlauncher.plugin.settings.assets.ObjectsItem;
import org.jackhuang.hellominecraftlauncher.settings.Version;

/**
 *
 * @author hyh
 */
public class AssetsMojangLoader extends IAssetsHandler {

    ArrayList<DoneListener<Integer, Integer>> listeners = new ArrayList<DoneListener<Integer, Integer>>();
    private ArrayList<String> assetsDownloadURLs, assetsLocalNames;
    private String url_assets, name;

    public AssetsMojangLoader(String URLASSETS, String name) {
        url_assets = URLASSETS;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void getList(final DoneListener<String[], Void> dl) {
        Version v = HMCLPlugin.env.getVersion();
        String assetsIndex = Utils.getMinecraftVersion(v, HMCLPlugin.env.getDefaultGameDir()).assets;
        File f = new File(Utils.addSeparator(assets) + File.separator + "indexes" + File.separator + assetsIndex + ".json");
        NewAssetsLoader loader = new NewAssetsLoader(Utils.readToEnd(f));
        Objects o = loader.format();
        assetsDownloadURLs = new ArrayList<String>();
        assetsLocalNames = new ArrayList<String>();
        ArrayList<String> al = new ArrayList<String>();
        for (Map.Entry<String, ObjectsItem> e : o.objects.entrySet()) {
            Contents c = new Contents();
            c.key = e.getValue().hash.substring(0, 2) + "/" + e.getValue().hash;
            assetsDownloadURLs.add(c.key);
            assetsLocalNames.add(c.key);
            al.add(e.getKey());
        }

        dl.onDone(al.toArray(new String[1]), null);
    }

    void onEvent() {
        for (DoneListener<Integer, Integer> dl : listeners) {
            dl.onDone(progress, max);
        }
    }
    int progress, max;

    @Override
    public void beginDownloading() {
        Version version = HMCLPlugin.env.getVersion();
        String path = Utils.getGameDir(version, HMCLPlugin.env.getDefaultGameDir());
        String path3 = assets;
        String assetsDir = Utils.addSeparator(path3);
        File assetsFile = new File(assetsDir);
        if (!assetsFile.exists()) {
            assetsFile.mkdirs();
        }
        progress = 0;
        for (int i = 0; i < assetsDownloadURLs.size(); i++) {
            File file = new File(assetsDir + "objects" + File.separator + assetsLocalNames.get(i).replace("/", File.separator));
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
        }
        max = assetsDownloadURLs.size();
        DownloadWindow dw = new DownloadWindow();
        for (int i = 0; i < max; i++) {
            String mark = assetsDownloadURLs.get(i);
            String url = url_assets + mark;
            String location = assetsDir + "objects" + File.separator + assetsLocalNames.get(i).replace("/", File.separator);
            if (new File(location).isDirectory()) {
                continue;
            }
            dw.addDownloadURL(mark, url, location);
        }
        dw.setVisible(true);
    }
}
