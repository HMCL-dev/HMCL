/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.utils;

/**
 * 判断minecraft.jar类型反馈
 * @author hyh
 */
public class MinecraftVersionRequest {
    public static final int Unkown = 0, Invaild = 1, InvaildJar = 2,
            Modified = 3, OK = 4, NotFound = 5, NotReadable = 6, NotAFile = 7;
    public int type;
    public String version;
}
