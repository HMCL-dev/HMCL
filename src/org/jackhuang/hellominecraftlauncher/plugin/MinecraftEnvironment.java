/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.plugin;

import java.io.File;
import org.jackhuang.hellominecraftlauncher.apis.IMinecraftEnvironment;
import org.jackhuang.hellominecraftlauncher.apis.utils.MinecraftVersionRequest;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.utilities.SettingsManager;
import org.jackhuang.hellominecraftlauncher.settings.Version;
import org.jackhuang.hellominecraftlauncher.views.MainWindow;

/**
 *
 * @author hyh
 */
public class MinecraftEnvironment implements IMinecraftEnvironment {

    @Override
    public String clientToken() {
        return SettingsManager.settings.clientToken;
    }

    @Override
    public Version getVersion() {
        return MainWindow.getInstance().getVersion();
    }

    @Override
    public String getDefaultGameDir() {
        return SettingsManager.settings.publicSettings.gameDir;
    }

    @Override
    public MinecraftVersionRequest getMinecraftVersion() {
        return Utils.minecraftVersion(new File(getVersion().getMinecraftJar()));
    }

    @Override
    public int getSourceType() {
        return SettingsManager.settings.downloadtype;
    }
    
}
