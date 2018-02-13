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
package org.jackhuang.hmcl.setting;

import java.util.Optional;

public enum Theme {
    BLUE,
    DARK_BLUE,
    GREEN,
    ORANGE,
    PURPLE,
    RED;

    public String[] getStylesheets() {
        return new String[]{
                Theme.class.getResource("/css/jfoenix-fonts.css").toExternalForm(),
                Theme.class.getResource("/css/jfoenix-design.css").toExternalForm(),
                Theme.class.getResource("/assets/css/" + name().toLowerCase() + ".css").toExternalForm(),
                Theme.class.getResource("/assets/css/root.css").toExternalForm()
        };
    }

    public static Optional<Theme> getTheme(String name) {
        for (Theme theme : values())
            if (theme.name().equalsIgnoreCase(name))
                return Optional.of(theme);
        return Optional.empty();
    }
}
