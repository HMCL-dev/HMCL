/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers.forge.vanilla;

import java.util.Map;

/**
 *
 * @author hyh
 */
public class MinecraftForgeVersionRoot {
    public String artifact, webpath, adfly, homepage, name;
    public Map<String, int[]> branches, mcversion;
    public Map<String, Integer> promos;
    public Map<Integer, MinecraftForgeVersion> number;
}
