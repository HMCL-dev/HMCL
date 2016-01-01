/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.plugin.settings;

import java.util.ArrayList;
import org.jackhuang.hellominecraftlauncher.apis.DoneListener;
import org.jackhuang.hellominecraftlauncher.apis.DownloadWindow;
import org.jackhuang.hellominecraftlauncher.apis.handlers.IAssetsHandler;
import org.jackhuang.hellominecraftlauncher.apis.utils.Compressor;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;

/**
 *
 * @author hyh
 */
public class AssetsZipHandler extends IAssetsHandler {
    
    ArrayList<DoneListener<Integer, Integer>> listeners = new ArrayList<DoneListener<Integer, Integer>>();
    
    String name, filename, url;

    public AssetsZipHandler(String name, String filename, String url) {
        this.name = name;
        this.filename = filename;
        this.url = url;
    }
    
    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void beginDownloading() {
        final String filePath = Utils.addSeparator(Utils.currentDir()) + filename;
        DownloadWindow dw = new DownloadWindow();
        dw.addDownloadURL(filename, url, filePath);
        dw.setModal(true);
        dw.setVisible(true);
        if(dw.isDownloadSuccessfully())
            Compressor.unzip(filePath, assets);
    }

    @Override
    public void getList(DoneListener<String[], Void> dl) {
        dl.onDone(new String[]{name}, null);
    }
    
}