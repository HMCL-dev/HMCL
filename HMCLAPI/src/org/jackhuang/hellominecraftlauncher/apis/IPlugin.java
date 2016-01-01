/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis;

import org.jackhuang.hellominecraftlauncher.apis.events.HMCLPluginLoadEvent;
import org.jackhuang.hellominecraftlauncher.apis.events.HMCLPluginUnloadEvent;
import org.jackhuang.hellominecraftlauncher.apis.events.HMCLPluginVersionChangedEvent;

/**
 * 此类为插件主类必须实现的接口
 * @author hyh
 */
public abstract class IPlugin {
    /**
     * 插件加载事件<br />
     * 可以用来注册Handlers、读取设置
     * @param event 
     */
    public void load(HMCLPluginLoadEvent event) {
        
    }
    
    /**
     * 插件卸载事件<br />
     * 可以用来保存设置
     * @param event 
     */
    public void unload(HMCLPluginUnloadEvent event) {
        
    }
    
    /**
     * 当用户切换版本时会触发此事件
     * @param event 
     */
    public void versionChanged(HMCLPluginVersionChangedEvent event) {
        
    }
    
    /**
     * 显示插件设置对话框
     */
    public void showSettingsDialog() {
        
    }
    
    /**
     * Minecraft启动前触发该事件
     */
    public void minecraftStarting() {
        
    }
    
    /**
     * Minecraft终止后触发该事件
     */
    public void minecraftStopped() {
    }
}
