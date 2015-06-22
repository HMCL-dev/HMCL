/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.assets;

import java.io.File;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.utils.download.IDownloadProvider;
import org.jackhuang.hellominecraft.utils.VersionNumber;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.utils.functions.Consumer;

/**
 *
 * @author hyh
 */
public class AssetsMojangOldLoader extends IAssetsHandler {

    private static final String URL = "http://bmclapi.bangbang93.com/resources/";

    public AssetsMojangOldLoader(String name) {
        super(name);
    }

    @Override
    public void getList(final Consumer<String[]> dl) {
        AssetsLoader al = new AssetsLoader(URL);
        al.failedEvent.register((sender, e) -> {
            HMCLog.warn("Failed to get assets list.", e);
            dl.accept(null);
            return true;
        });
        al.successEvent.register((sender, t) -> {
            assetsDownloadURLs = new ArrayList<>();
            assetsLocalNames = new ArrayList<>();
            contents = t;
            for (Contents c : t) {
                assetsDownloadURLs.add(c.key);
                assetsLocalNames.add(new File(mp.getAssets(), c.key.replace("/", File.separator)));
            }
            dl.accept(assetsDownloadURLs.toArray(new String[1]));
            return true;
        });
        new Thread(al).start();
    }

    @Override
    public boolean isVersionAllowed(String formattedVersion) {
        VersionNumber r = VersionNumber.check(formattedVersion);
        if(r == null) return false;
        return VersionNumber.check("1.7.2").compareTo(r) >= 0 &&
                VersionNumber.check("1.6.0").compareTo(r) <= 0;
    }
    
    @Override
    public Task getDownloadTask(IDownloadProvider sourceType) {
        return new AssetsTask(URL);
    }
}
