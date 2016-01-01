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
import org.jackhuang.hellominecraftlauncher.plugin.settings.assets.AssetsLoader;
import org.jackhuang.hellominecraftlauncher.plugin.settings.assets.AssetsLoaderListener;
import org.jackhuang.hellominecraftlauncher.plugin.settings.assets.Contents;

/**
 *
 * @author hyh
 */
public class AssetsMojangOldLoader extends IAssetsHandler {

    ArrayList<DoneListener<Integer, Integer>> listeners = new ArrayList<DoneListener<Integer, Integer>>();
    private ArrayList<String> assetsDownloadURLs, assetsLocalNames;
    private String url_assets, name;

    public AssetsMojangOldLoader(String url, String name) {
        url_assets = url;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void getList(final DoneListener<String[], Void> dl) {
        AssetsLoader.getAssets(url_assets, new AssetsLoaderListener() {
            @Override
            public void OnDone(ArrayList<Contents> loader) {
                assetsDownloadURLs = new ArrayList<String>();
                assetsLocalNames = new ArrayList<String>();
                for (Contents c : loader) {
                    assetsDownloadURLs.add(c.key);
                    assetsLocalNames.add(c.key);
                }
                dl.onDone(assetsLocalNames.toArray(new String[1]), null);
            }

            @Override
            public void OnFailed(Exception e) {
                e.printStackTrace();
                dl.onDone(new String[]{"获取列表失败，请刷新重试"}, null);
            }
        });
    }

    void onEvent() {
        for (DoneListener<Integer, Integer> dl : listeners) {
            dl.onDone(progress, max);
        }
    }
    int progress, max;

    @Override
    public void beginDownloading() {
        String path3 = assets;
        String assetsDir = Utils.addSeparator(path3);
        File assetsFile = new File(assetsDir);
        if (!assetsFile.exists()) {
            assetsFile.mkdirs();
        }
        progress = 0;
        for (int i = 0; i < assetsDownloadURLs.size(); i++) {
            File file = new File(assetsDir + assetsLocalNames.get(i).replace("/", File.separator));
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
        }
        max = assetsDownloadURLs.size();
        DownloadWindow dw = new DownloadWindow();
        for (int i = 0; i < max; i++) {
            String mark = assetsDownloadURLs.get(i);
            String url = url_assets + mark;
            String location = assetsDir + assetsLocalNames.get(i).replace("/", File.separator);
            if (new File(location).isDirectory()) {
                continue;
            }
            dw.addDownloadURL(mark, url, location);
        }
        dw.setVisible(true);
    }
}
