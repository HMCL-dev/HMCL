/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.loaders;

import java.util.List;

/**
 *
 * @author hyh
 */
public class LaunchRequest {
    
    public boolean windowMaximized;
    public boolean windowFullscreen;
    public boolean showLog;
    public int width, height;
    public String classPath;
    
    public String[] inactiveExtMods, inactiveCoreMods;
    public List<String> minecraftArguments;
    public List<String> launchStrings;
    
}
