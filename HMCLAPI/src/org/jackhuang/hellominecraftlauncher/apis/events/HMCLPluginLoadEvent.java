/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.events;

import org.jackhuang.hellominecraftlauncher.apis.IMinecraftEnvironment;
import org.jackhuang.hellominecraftlauncher.apis.IPluginRegister;
import org.jackhuang.hellominecraftlauncher.apis.IUIRegister;


/**
 * 插件加载事件
 * @author hyh
 */
public class HMCLPluginLoadEvent {
    private final IPluginRegister register;
    private final IUIRegister uiRegister;
    private final IMinecraftEnvironment env;
    private final String suggestedFile;

    public HMCLPluginLoadEvent(IPluginRegister register, IUIRegister uiRegister, IMinecraftEnvironment env, String suggestedFile) {
        this.register = register;
        this.uiRegister = uiRegister;
        this.env = env;
        this.suggestedFile = suggestedFile;
    }
    
    

    /**
     * 获取Handler注册类
     * @return 
     */
    public IPluginRegister getPluginRegister() {
        return register;
    }
    
    /**
     * 获取启动器储存的Minecraft环境
     * @return 
     */
    public IMinecraftEnvironment getMinecraftEnvironment() {
        return env;
    }
    
    /**
     * 获取插件被推荐的配置文件地址
     * @return 
     */
    public String getSuggestedConfigurationFile() {
        return suggestedFile;
    }
    
    public IUIRegister getUIRegister() {
        return uiRegister;
    }
}
