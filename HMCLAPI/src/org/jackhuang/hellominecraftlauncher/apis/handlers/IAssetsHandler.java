/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.handlers;

import org.jackhuang.hellominecraftlauncher.apis.DoneListener;
import org.jackhuang.hellominecraftlauncher.apis.IPluginHandler;

/**
 * Assets
 * @author hyh
 */
public abstract class IAssetsHandler extends IPluginHandler {
    
    /**
     * 本assets下载接口的名称
     * @return 
     */
    public abstract String getName();
    
    /**
     * assets所有要下载的文件
     * @param dl 
     */
    public abstract void getList(DoneListener<String[], Void> dl);
    
    /**
     * 用户点击全部下载会触发此事件
     */
    public abstract void beginDownloading();
    
    /**
     * assets路径
     */
    protected String assets;
    
    /**
     * 由HMCL通知AssetsHandler assets路径
     * @param assets 
     */
    public void setAssets(String assets) {
        this.assets = assets;
    }

    @Override
    public String getRegistratorName() {
        return "HMCL";
    }
    
}
