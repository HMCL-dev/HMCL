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
package org.jackhuang.hellominecraft.util.sys;

/**
 *
 * @author huangyuhui
 */
public enum Platform {

    UNKNOWN {

        @Override
        public String getBit() {
            return "unknown";
        }

    },
    BIT_32 {

        @Override
        public String getBit() {
            return "32";
        }

    },
    BIT_64 {

        @Override
        public String getBit() {
            return "64";
        }

    };

    public abstract String getBit();

    public static Platform getPlatform() {
        return System.getProperty("os.arch").contains("64") ? BIT_64 : BIT_32;
    }
}
