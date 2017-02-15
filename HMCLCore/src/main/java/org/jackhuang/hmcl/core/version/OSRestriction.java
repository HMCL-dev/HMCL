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
import org.jackhuang.hmcl.util.StrUtils;
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
        return StrUtils.isBlank(getName()) || OS.os().name().equalsIgnoreCase(getName());
    }
}
