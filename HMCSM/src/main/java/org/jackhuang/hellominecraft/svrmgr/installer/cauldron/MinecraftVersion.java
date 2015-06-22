/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.installer.cauldron;

import java.util.List;

/**
 *
 * @author hyh
 */
public class MinecraftVersion {

    public String minecraftArguments, mainClass, time, id, type, processArguments,
	    releaseTime, assets, jar, inheritsFrom;
    public int minimumLauncherVersion;

    public List<MinecraftLibrary> libraries;
}
