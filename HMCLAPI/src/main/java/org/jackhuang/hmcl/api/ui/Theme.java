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
package org.jackhuang.hmcl.api.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author huang
 */
public class Theme {
    
    public static final Map<String, Theme> THEMES = new HashMap<>();
    
    public final String id;
    public final String localizedName;
    public final Map<String, String> settings;

    public Theme(String id, String localizedName, Map<String, String> settings) {
        this.id = id;
        this.localizedName = localizedName;
        this.settings = Objects.requireNonNull(settings, "Theme settings map may not be null.");
    }

    @Override
    public String toString() {
        return localizedName;
    }

    public String getId() {
        return id;
    }
}
