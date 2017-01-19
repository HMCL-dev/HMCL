/*
 * Hello Minecraft!.
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
package org.jackhuang.hellominecraft.util.ui;

import java.awt.Color;

/**
 *
 * @author huangyuhui
 */
public final class BasicColors {

    private BasicColors() {
    }
    
    private static Color getWebColor(String c) {
        return new Color(
            Integer.parseInt(c.substring(0, 2), 16),
            Integer.parseInt(c.substring(2, 4), 16),
            Integer.parseInt(c.substring(4, 6), 16)
        );
    }

    public static final Color COLOR_RED = new Color(229, 0, 0);
    public static final Color COLOR_RED_DARKER = new Color(157, 41, 51);
    public static final Color COLOR_GREEN = new Color(90, 184, 96);
    public static final Color COLOR_BLUE = new Color(16, 108, 163);
    public static final Color COLOR_BLUE_DARKER = new Color(12, 94, 145);
    public static final Color COLOR_WHITE_TEXT = new Color(254, 254, 254);
    public static final Color COLOR_CENTRAL_BACK = new Color(25, 30, 34, 160);

    public static final Color[] BG_COLORS = new Color[] {
        COLOR_BLUE,
        getWebColor("1ABC9C"),
        getWebColor("9B59B6"),
        getWebColor("34495E"),
        getWebColor("E67E22"),
        getWebColor("E74C3C")
    };
    
    public static final Color[] BG_COLORS_DARKER = new Color[] {
        COLOR_BLUE_DARKER,
        getWebColor("16A085"),
        getWebColor("8E44AD"),
        getWebColor("2C3E50"),
        getWebColor("D35400"),
        getWebColor("C0392B")
    };
}
