/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import org.jackhuang.hellominecraft.C;

/**
 * @author hyh
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
