/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.cbplugins;

import java.util.List;

/**
 *
 * @author huangyuhui
 */
public class BukkitPlugin {
    
    public String description, plugin_name, slug;
    public List<PluginVersion> versions;
    
    public String getLatestVersion() {
        if(versions != null) {
            PluginVersion v = versions.get(0);
            return v.version;
        }
        return null;
    }
    
    public String getLatestBukkit() {
        if(versions != null) {
            PluginVersion v = versions.get(0);
            List<String> al = v.game_versions;
            if(al != null && !al.isEmpty()) {
                return al.get(0);
            }
        }
        return "";
    }
    
}
