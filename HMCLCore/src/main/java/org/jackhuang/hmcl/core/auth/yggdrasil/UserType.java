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
package org.jackhuang.hmcl.core.auth.yggdrasil;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author huang
 */
public enum UserType {

    LEGACY("legacy"), MOJANG("mojang");

    private static final Map<String, UserType> BY_NAME;
    private final String name;

    private UserType(String name) {
        this.name = name;
    }

    public static UserType byName(String name) {
        return BY_NAME.get(name.toLowerCase());
    }
    
    public static UserType byLegacy(boolean isLegacy) {
        return isLegacy ? LEGACY : MOJANG;
    }

    public String getName() {
        return this.name;
    }

    static {
        BY_NAME = new HashMap();
        for (UserType type : values())
            BY_NAME.put(type.name, type);
    }
}
