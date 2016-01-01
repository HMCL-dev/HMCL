package org.jackhuang.hellominecraftlauncher.apis;

import java.util.HashMap;
import java.util.Map;

/**
 * 提供的Handlers类型
 * @author hyh
 */
public class PluginHandlerType {
    
    public Class<? extends IPluginHandler> claSS;
    public String name;
    
    private PluginHandlerType(String name, Class<? extends IPluginHandler> c) {
        claSS = c;
        this.name = name;
    }
    
    public static boolean registerPluginHandlerType(String mark, Class<? extends IPluginHandler> cla) {
        if(map.containsKey(mark)) return false;
        map.put(mark, new PluginHandlerType(mark, cla));
        return true;
    }
    
    private static Map<String, PluginHandlerType> map;
    
    public static PluginHandlerType getType(String mark) {
        return map.get(mark);
    }
    
    static {
        map = new HashMap<String, PluginHandlerType>();
    }
}
