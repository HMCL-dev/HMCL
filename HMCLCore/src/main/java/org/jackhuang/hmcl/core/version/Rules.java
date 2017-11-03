/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.core.version;

import com.google.gson.annotations.SerializedName;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public class Rules {

    @SerializedName("action")
    private String action;
    @SerializedName("os")
    private OSRestriction os;
    @SerializedName("features")
    private Map<String, Boolean> features;

    public Rules() {
    }
    
    public Rules(String action, OSRestriction os) {
        this.action = action;
        this.os = os;
    }
    
    public Rules(String action, Map<String, Boolean> features) {
        this.action = action;
        this.features = features;
    }

    public String action(Map<String, Boolean> features) {
        if (os != null && !os.isCurrentOS()) return null;
        if (this.features != null) {
            for (Map.Entry<String, Boolean> entry : this.features.entrySet())
                if (!features.containsKey(entry.getKey()))
                    return null;
                else
                    if (!features.get(entry.getKey()).equals(entry.getValue()))
                        return null;
        }
        return action;
    }
    
    public static boolean allow(Collection<Rules> c) {
        return allow(c, Collections.EMPTY_MAP);
    }
    
    public static boolean allow(Collection<Rules> c, Map<String, Boolean> features) {
        if (c != null) {
            boolean flag = false;
            for (Rules r : c)
                if ("disallow".equals(r.action(features)))
                    return false;
                else if ("allow".equals(r.action(features)))
                    flag = true;
            return flag;
        } else
            return true;
    }

}
