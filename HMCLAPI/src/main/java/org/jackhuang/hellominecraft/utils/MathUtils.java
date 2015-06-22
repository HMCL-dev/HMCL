/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.utils;

/**
 *
 * @author huang
 */
public class MathUtils {

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
            if(s.endsWith("g")) return a * 1024;
            else if(s.endsWith("k")) return a / 1024;
            else return a;
        }
    }
    
}
