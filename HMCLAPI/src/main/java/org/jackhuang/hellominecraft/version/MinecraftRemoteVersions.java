/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.version;

import java.util.ArrayList;
import org.jackhuang.hellominecraft.C;

/**
 *
 * @author hyh
 */
public class MinecraftRemoteVersions {
    
    public ArrayList<MinecraftRemoteVersion> versions;
    public MinecraftRemoteLatestVersion latest;
    
    public static MinecraftRemoteVersions fromJson(String s) {
        return C.gson.fromJson(s, MinecraftRemoteVersions.class);
    }
    
}
