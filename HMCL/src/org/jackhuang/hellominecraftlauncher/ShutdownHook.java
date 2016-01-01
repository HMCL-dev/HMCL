/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher;

import org.jackhuang.hellominecraftlauncher.apis.IPlugin;
import org.jackhuang.hellominecraftlauncher.plugin.PluginManager;

/**
 *
 * @author hyh
 */
public final class ShutdownHook implements Runnable {

    @Override
    public final void run() {
        Launcher.restoreMods();
        Launcher.moveBackMods();
        Launcher.moveBackModDir();
        
        
        try {
            for(IPlugin p : PluginManager.plugins) {
                p.minecraftStopped();
            }
        } catch(Throwable t) {
        }
    }
}
