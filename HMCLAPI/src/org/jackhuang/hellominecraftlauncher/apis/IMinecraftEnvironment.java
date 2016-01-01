/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis;

import org.jackhuang.hellominecraftlauncher.apis.utils.MinecraftVersionRequest;
import org.jackhuang.hellominecraftlauncher.settings.Version;

/**
 * 获取一些Minecraft的环境信息
 * @author hyh
 */
public interface IMinecraftEnvironment {
    /**
     * 获取Client Token
     * @return clientToken
     */
    String clientToken();
    
    Version getVersion();
    
    MinecraftVersionRequest getMinecraftVersion();
    
    String getDefaultGameDir();
    /**
     * 0 -- Mojang
     * 1 -- bangbang93
     */
    int getSourceType();
}
