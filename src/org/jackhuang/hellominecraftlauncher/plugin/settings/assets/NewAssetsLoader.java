/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.plugin.settings.assets;

import com.google.gson.Gson;

/**
 *
 * @author hyh
 */
public class NewAssetsLoader {
    private String indexesJson;

    public NewAssetsLoader(String indexesJson) {
        this.indexesJson = indexesJson;
    }
    
    public Objects format() {
        return new Gson().fromJson(indexesJson, Objects.class);
    }
}
