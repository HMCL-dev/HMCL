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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jackhuang.hmcl.util.sys.OS;

/**
 *
 * @author huangyuhui
 */
public class OSRestriction {

    @SerializedName("version")
    private String version;
    @SerializedName("name")
    public String name;
    @SerializedName("arch")
    public String arch;
    
    public OSRestriction() {
        this(null);
    }
    
    public OSRestriction(String name) {
        this(name, null);
    }
    
    public OSRestriction(String name, String version) {
        this(name, version, null);
    }
    
    public OSRestriction(String name, String version, String arch) {
        this.name = name;
        this.version = version;
        this.arch = arch;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isCurrentOS() {
        if (name != null && !OS.os().name().equalsIgnoreCase(name))
            return false;
        if (version != null) {
            try {
                Pattern pattern = Pattern.compile(version);
                Matcher matcher = pattern.matcher(System.getProperty("os.version"));
                if (!matcher.matches())
                    return false;
            } catch (Throwable t) {}
        }
        if (arch != null) {
            try {
                Pattern pattern = Pattern.compile(arch);
                Matcher matcher = pattern.matcher(System.getProperty("os.arch"));
                if (!matcher.matches())
                    return false;
            } catch (Throwable t) {}
        }
        return true;
    }
}
