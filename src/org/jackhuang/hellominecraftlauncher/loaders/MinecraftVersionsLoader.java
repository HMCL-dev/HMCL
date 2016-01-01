/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.loaders;

import com.google.gson.Gson;

/**
 *
 * @author hyh
 */
public class MinecraftVersionsLoader {
    
    String JSON;
    Gson gson;
    private MinecraftVersions versions;
    
    public MinecraftVersionsLoader(String j) {
        gson = new Gson();
        
        JSON = j;
        versions = gson.fromJson(JSON, MinecraftVersions.class);
    }

    /**
     * @return the versions
     */
    public MinecraftVersions getVersions() {
        return versions;
    }
    
}
