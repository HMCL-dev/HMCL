/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public enum UserType {
    LEGACY,
    MOJANG;

    public static UserType fromName(String name) {
        return BY_NAME.get(name.toLowerCase());
    }

    public static UserType fromLegacy(boolean isLegacy) {
        return isLegacy ? LEGACY : MOJANG;
    }

    static {
        HashMap<String, UserType> byName = new HashMap<>();
        for (UserType type : values())
            byName.put(type.name().toLowerCase(), type);
        BY_NAME = Collections.unmodifiableMap(byName);
    }

    public static final Map<String, UserType> BY_NAME;

}
