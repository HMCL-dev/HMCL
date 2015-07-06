/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.version;

import org.jackhuang.hellominecraft.C;

/**
 * @author huangyuhui
 */
public class MinecraftVersionRequest {
    public static final int Unkown = 0, Invaild = 1, InvaildJar = 2,
            Modified = 3, OK = 4, NotFound = 5, NotReadable = 6, NotAFile = 7;
    public int type;
    public String version;
    
    public static String getResponse(MinecraftVersionRequest minecraftVersion) {
        String text = "";
        switch (minecraftVersion.type) {
            case MinecraftVersionRequest.Invaild:
                text = C.i18n("minecraft.invalid");
                break;
            case MinecraftVersionRequest.InvaildJar:
                text = C.i18n("minecraft.invalid_jar");
                break;
            case MinecraftVersionRequest.NotAFile:
                text = C.i18n("minecraft.not_a_file");
                break;
            case MinecraftVersionRequest.NotFound:
                text = C.i18n("minecraft.not_found");
                break;
            case MinecraftVersionRequest.NotReadable:
                text = C.i18n("minecraft.not_readable");
                break;
            case MinecraftVersionRequest.Modified:
                text = C.i18n("minecraft.modified") + " ";
            case MinecraftVersionRequest.OK:
                text += minecraftVersion.version;
                break;
            case MinecraftVersionRequest.Unkown:
            default:
                text = "???";
                break;
        }
        return text;
    }
}
