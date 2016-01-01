/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraftlauncher.Main;
import org.jackhuang.hellominecraftlauncher.apis.IPlugin;
import org.jackhuang.hellominecraftlauncher.apis.IPluginHandler;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.views.MainWindow;

import org.jackhuang.hellominecraftlauncher.apis.Plugin;
import org.jackhuang.hellominecraftlauncher.apis.PluginHandlerType;
import org.jackhuang.hellominecraftlauncher.apis.events.HMCLPluginLoadEvent;
import org.jackhuang.hellominecraftlauncher.apis.handlers.IAssetsHandler;
import org.jackhuang.hellominecraftlauncher.utilities.C;
import org.jackhuang.hellominecraftlauncher.settings.Version;
import org.jackhuang.hellominecraftlauncher.utilities.SettingsManager;

/**
 *
 * @author hyh
 */
public class PluginManager {
    
    public static void preparePlugins() throws IOException {
        String s = Utils.addSeparator(Utils.currentDir()) + C.FILE_PLUGINS + File.separator;
        ArrayList<String> jars = Utils.findAllFileWithFullName(new File(s));
        File[] files = new File[jars.size()];
        URL[] urls = new URL[jars.size()];
        URL temp;
        pluginFiles = new ArrayList<JarFile>();
        for (int j = 0; j < jars.size(); j++) {
            try {
                files[j] = new File(jars.get(j));
                if(files[j].isDirectory()) continue;
                temp = files[j].toURI().toURL();
                urls[j] = temp;
                pluginFiles.add(new JarFile(files[j]));
            } catch (MalformedURLException ex) {
                Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        URLClassLoader loader = new URLClassLoader(urls);
        plugins = new ArrayList<IPlugin>();
        pluginAnnotations = new ArrayList<Plugin>();
        
        pluginAnnotations.add(HMCLPlugin.class.getAnnotation(Plugin.class));
        plugins.add(new HMCLPlugin());
        
        for (int i = 0; i < pluginFiles.size(); i++) {
            Enumeration<JarEntry> entries = pluginFiles.get(i).entries();
            while(entries.hasMoreElements()) {
                try {
                    JarEntry jarEntry = entries.nextElement();
                    String clsName = jarEntry.getName();
                    if(!clsName.endsWith(".class")) continue;
                    if(clsName.contains("$")) continue;
                    clsName = clsName.replace("/", ".");
                    clsName = clsName.replace(".class", "");
                    Class c = loader.loadClass(clsName);
                    if(c.isAnnotationPresent(Plugin.class)) {
                        Plugin p = (Plugin)c.getAnnotation(Plugin.class);
                        if(p.apiVer() > Main.pluginVersion)
                            continue;
                        IPlugin plugin = (IPlugin) c.newInstance();
                        if (plugin != null) {
                            pluginAnnotations.add(p);
                            plugins.add(plugin);
                        }
                    }
                } catch (Throwable ex) {
                    Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        preparedPlugins = true;
    }
    
    public static void loadPlugin() {
        if(!preparedPlugins) return;
        pluginError = new ArrayList<String>();
        PluginHandler ph = new PluginHandler();
        MinecraftEnvironment me = new MinecraftEnvironment();
        for(int i = 0; i < plugins.size(); i++) {
            IPlugin l = plugins.get(i);
            Plugin p = pluginAnnotations.get(i);
            try {
                l.load(new HMCLPluginLoadEvent(ph, ph, me,
                        Utils.addSeparator(Utils.currentDir()) + C.FILE_PLUGINS +
                        File.separator + "config" + File.separator +
                        p.pluginId() + ".json"));
                pluginError.add(null);
            } catch(Exception e) {
                pluginError.add(Utils.makeStackTrace(e));
            }
        }
    }
    
    public static void unloadPlugin() {
        if(!preparedPlugins) return;
        for(IPlugin l : plugins) {
            l.unload(null);
        }
        plugins = null;
        pluginFiles = null;
        pluginAnnotations = null;
        pluginError = null;
        preparedPlugins = false;
    }
    
    public static void versionChanged() {
        for(IPlugin p : PluginManager.plugins) {
            try {
                p.versionChanged(null);
            } catch(Throwable t) {
                t.printStackTrace();
            }
        }
        Version v = MainWindow.getInstance().getVersion(false);
        String assets = v.gameAssets;
        if(Utils.isEmpty(assets))
            assets = Utils.getGameDir(v, SettingsManager.settings.publicSettings.gameDir) + "assets";
        for(IPluginHandler ph : PluginHandler.getPluginHandlers(PluginHandlerType.getType("ASSETS"))) {
            try {
                IAssetsHandler ah = (IAssetsHandler) ph;
                ah.setAssets(assets);
            } catch(Exception e) {
                
            }
        }
    }
    
    public static void showSettingsDialog(int ind) {
        plugins.get(ind).showSettingsDialog();
    }
    
    public static boolean preparedPlugins = false;
    public static ArrayList<IPlugin> plugins;
    public static ArrayList<Plugin> pluginAnnotations;
    public static ArrayList<JarFile> pluginFiles;
    public static ArrayList<String> pluginError;
}
