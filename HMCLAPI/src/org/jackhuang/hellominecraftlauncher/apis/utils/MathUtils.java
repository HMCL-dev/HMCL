/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraftlauncher.apis.utils;

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
    
}
