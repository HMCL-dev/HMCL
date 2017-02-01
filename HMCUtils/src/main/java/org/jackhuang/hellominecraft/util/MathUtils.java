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
package org.jackhuang.hellominecraft.util;

/**
 *
 * @author huang
 */
public final class MathUtils {
    
    private MathUtils() {
    }

    public static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean canParseInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static int parseMemory(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            int a = parseInt(s.substring(0, s.length() - 1), def);
            if (s.endsWith("g"))
                return a * 1024;
            else if (s.endsWith("k"))
                return a / 1024;
            else
                return a;
        }
    }

}
