/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.plugin;

import org.jackhuang.hellominecraftlauncher.plugin.editpanels.SaveWorldPanel;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraftlauncher.apis.Configuration;
import org.jackhuang.hellominecraftlauncher.apis.IMinecraftEnvironment;
import org.jackhuang.hellominecraftlauncher.apis.IPlugin;
import org.jackhuang.hellominecraftlauncher.apis.Plugin;
import org.jackhuang.hellominecraftlauncher.apis.PluginHandlerType;
import org.jackhuang.hellominecraftlauncher.apis.events.HMCLPluginLoadEvent;
import org.jackhuang.hellominecraftlauncher.apis.events.HMCLPluginUnloadEvent;
import org.jackhuang.hellominecraftlauncher.apis.events.HMCLPluginVersionChangedEvent;
import org.jackhuang.hellominecraftlauncher.apis.handlers.IAssetsHandler;
import org.jackhuang.hellominecraftlauncher.apis.handlers.Login;
import org.jackhuang.hellominecraftlauncher.plugin.editpanels.AssetsPanel;
import org.jackhuang.hellominecraftlauncher.plugin.editpanels.InstallersPanel;
import org.jackhuang.hellominecraftlauncher.plugin.editpanels.ServerEditPanel;
import org.jackhuang.hellominecraftlauncher.plugin.editpanels.TexturepackEditPanel;
import org.jackhuang.hellominecraftlauncher.plugin.logins.LegacyLogin;
import org.jackhuang.hellominecraftlauncher.plugin.logins.LoginPluginSettings;
import org.jackhuang.hellominecraftlauncher.plugin.logins.MineLogin;
import org.jackhuang.hellominecraftlauncher.plugin.logins.OfflineLogin;
import org.jackhuang.hellominecraftlauncher.plugin.logins.SkinmeLogin;
import org.jackhuang.hellominecraftlauncher.plugin.logins.YggdrasilLogin;
import org.jackhuang.hellominecraftlauncher.plugin.mainpanels.GameDownloadPanel;
import org.jackhuang.hellominecraftlauncher.plugin.mainpanels.IntegrateDownloadPanel;
import org.jackhuang.hellominecraftlauncher.plugin.settings.AssetsMojangLoader;
import org.jackhuang.hellominecraftlauncher.plugin.settings.AssetsMojangOldLoader;
import org.jackhuang.hellominecraftlauncher.plugin.settings.AssetsZipHandler;
import org.jackhuang.hellominecraftlauncher.utilities.C;

/**
 *
 * @author hyh
 */
@Plugin(apiVer = 1, pluginVer = "1.9.4", pluginId = "hmcl", pluginName = "Hello Minecraft! Launcher",
        authors = "huangyuhui", description = "Too many features.", requires = {})
public class HMCLPlugin extends IPlugin {
    
    YggdrasilLogin yggdrasilLogin;
    LegacyLogin legacyLogin;
    OfflineLogin offlineLogin;
    SkinmeLogin skinmeLogin;
    MineLogin mineLogin;
    Configuration<LoginPluginSettings> config;
    
    public static IMinecraftEnvironment env;
    
    //assets handlers
    AssetsZipHandler assets17, assets174, assets173, assets16;
    AssetsMojangLoader assetsMojang;
    AssetsMojangLoader assetsBMCL;
    AssetsMojangOldLoader assetsOldBMCL;
    
    //Version edit panels
    SaveWorldPanel saveWorldPanel;
    ServerEditPanel serverEditPanel;
    TexturepackEditPanel texturepackEditPanel;
    InstallersPanel installersPanel;
    AssetsPanel assetsPanel;
    
    //Main panels
    IntegrateDownloadPanel integrateDownloadPanel;
    GameDownloadPanel gameDownloadPanel;

    @Override
    public void load(HMCLPluginLoadEvent event) {
        
        PluginHandlerType.registerPluginHandlerType("ASSETS", IAssetsHandler.class);
        PluginHandlerType.registerPluginHandlerType("LOGIN", Login.class);
        String clientToken = event.getMinecraftEnvironment().clientToken();
        PluginHandlerType login = PluginHandlerType.getType("LOGIN");
        event.getPluginRegister().registerPluginHandler(login,
                offlineLogin = new OfflineLogin(clientToken));
        event.getPluginRegister().registerPluginHandler(login,
                yggdrasilLogin = new YggdrasilLogin(clientToken));
        event.getPluginRegister().registerPluginHandler(login,
                legacyLogin = new LegacyLogin(clientToken));
        event.getPluginRegister().registerPluginHandler(login,
                skinmeLogin = new SkinmeLogin(clientToken));
        event.getPluginRegister().registerPluginHandler(login,
                mineLogin = new MineLogin(clientToken));
        config = new Configuration<LoginPluginSettings>(event.getSuggestedConfigurationFile());
        config.load(LoginPluginSettings.class);
        if(config.get() != null) {
            legacyLogin.onLoadSettings(config.get().legacy);
            yggdrasilLogin.onLoadSettings(config.get().yggdrasil);
        }
        
        env = event.getMinecraftEnvironment();
        assets174 = new AssetsZipHandler("1.7.4(googlecode)", "assets-175.zip", "http://hmcl.googlecode.com/svn/trunk/assets-175.zip");
        assets173 = new AssetsZipHandler("1.7.3(googlecode)", "assets-174.zip", "http://hmcl.googlecode.com/svn/trunk/assets-174.zip");
        assets17 = new AssetsZipHandler("1.7.2(googlecode)", "assets-173.zip", "http://hmcl.googlecode.com/svn/trunk/assets-173.zip");
        assets16 = new AssetsZipHandler("1.6(googlecode)", "assets-164.zip", "http://hmcl.googlecode.com/svn/trunk/assets-164.zip");
        assetsMojang = new AssetsMojangLoader(C.URL_ASSETS_MOJANG, "1.7.4(Mojang)");
        assetsBMCL = new AssetsMojangLoader(C.URL_ASSETS_BMCL, "1.7.4(BMCL)");
        assetsOldBMCL = new  AssetsMojangOldLoader(C.URL_OLD_ASSETS_BMCL, "1.6(BMCL)");
        PluginHandlerType assets = PluginHandlerType.getType("ASSETS");
        event.getPluginRegister().registerPluginHandler(assets, assets174);
        event.getPluginRegister().registerPluginHandler(assets, assets173);
        event.getPluginRegister().registerPluginHandler(assets, assets17);
        event.getPluginRegister().registerPluginHandler(assets, assets16);
        event.getPluginRegister().registerPluginHandler(assets, assetsMojang);
        event.getPluginRegister().registerPluginHandler(assets, assetsBMCL);
        event.getPluginRegister().registerPluginHandler(assets, assetsOldBMCL);
        
        saveWorldPanel = new SaveWorldPanel();
        serverEditPanel = new ServerEditPanel();
        texturepackEditPanel = new TexturepackEditPanel();
        installersPanel = new InstallersPanel();
        assetsPanel = new AssetsPanel();
        assetsPanel.assetsType = config.get().assetsType;
        assetsPanel.prepareAssets();
        event.getUIRegister().addPanelToVersionEdit(saveWorldPanel);
        event.getUIRegister().addPanelToVersionEdit(serverEditPanel);
        event.getUIRegister().addPanelToVersionEdit(texturepackEditPanel);
        event.getUIRegister().addPanelToVersionEdit(installersPanel);
        event.getUIRegister().addPanelToVersionEdit(assetsPanel);
        
        integrateDownloadPanel = new IntegrateDownloadPanel();
        gameDownloadPanel = new GameDownloadPanel();
        event.getUIRegister().addPanelToMain(gameDownloadPanel);
        event.getUIRegister().addPanelToMain(integrateDownloadPanel);
    }

    @Override
    public void unload(HMCLPluginUnloadEvent event) {
        LoginPluginSettings s = new LoginPluginSettings();
        s.legacy = legacyLogin.onSaveSettings();
        s.yggdrasil = yggdrasilLogin.onSaveSettings();
        s.assetsType = assetsPanel.assetsType;
        config.set(s);
        try {
            config.save();
        } catch (IOException ex) {
            Logger.getLogger(HMCLPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void versionChanged(HMCLPluginVersionChangedEvent event) {
        saveWorldPanel.loadSaves();
        serverEditPanel.loadServers();
        texturepackEditPanel.loadPacks();
        installersPanel.reloadForgeList();
        installersPanel.reloadOptifineList();
    }
    
}
