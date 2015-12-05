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
package org.jackhuang.hellominecraft.version;

import org.jackhuang.hellominecraft.C;

/**
 * @author huangyuhui
 */
public class MinecraftVersionRequest {

    public static final int UNKOWN = 0, INVALID = 1, INVALID_JAR = 2,
    MODIFIED = 3, OK = 4, NOT_FOUND = 5, UNREADABLE = 6, NOT_FILE = 7;
    public int type;
    public String version;

    public static String getResponse(MinecraftVersionRequest minecraftVersion) {
        String text = "";
        switch (minecraftVersion.type) {
            case MinecraftVersionRequest.INVALID:
                text = C.i18n("minecraft.invalid");
                break;
            case MinecraftVersionRequest.INVALID_JAR:
                text = C.i18n("minecraft.invalid_jar");
                break;
            case MinecraftVersionRequest.NOT_FILE:
                text = C.i18n("minecraft.not_a_file");
                break;
            case MinecraftVersionRequest.NOT_FOUND:
                text = C.i18n("minecraft.not_found");
                break;
            case MinecraftVersionRequest.UNREADABLE:
                text = C.i18n("minecraft.not_readable");
                break;
            case MinecraftVersionRequest.MODIFIED:
                text = C.i18n("minecraft.modified") + " ";
            case MinecraftVersionRequest.OK:
                text += minecraftVersion.version;
                break;
            case MinecraftVersionRequest.UNKOWN:
            default:
                text = "???";
                break;
        }
        return text;
    }
}
