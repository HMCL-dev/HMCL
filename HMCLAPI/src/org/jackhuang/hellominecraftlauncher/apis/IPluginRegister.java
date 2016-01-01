/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis;

/**
 * 注册各种功能
 * @author hyh
 */
public interface IPluginRegister {
    void registerPluginHandler(PluginHandlerType type, IPluginHandler handler);
}
