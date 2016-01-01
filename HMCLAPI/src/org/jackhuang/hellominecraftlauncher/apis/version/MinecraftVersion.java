/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.version;

import java.util.ArrayList;

/**
 *
 * @author hyh
 */
public class MinecraftVersion {
    public String minecraftArguments, mainClass, time, id, type, processArguments,
            releaseTime, assets;
    public int minimumLauncherVersion;
    public ArrayList<MinecraftLibrary> libraries;
}
