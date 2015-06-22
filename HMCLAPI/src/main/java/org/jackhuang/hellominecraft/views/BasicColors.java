/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.views;

import java.awt.Color;

/**
 *
 * @author hyh
 */
public class BasicColors {
    
    private static Color getWebColor(String c){
        return new Color(
                Integer.parseInt(c.substring(0,2),16),
                Integer.parseInt(c.substring(2,4),16),
                Integer.parseInt(c.substring(4,6),16)
        );
    }

    public static final Color COLOR_RED = new Color(229, 0, 0);
    public static final Color COLOR_RED_DARKER = new Color(157, 41, 51);
    public static final Color COLOR_GREEN = new Color(90, 184, 96);
    public static final Color COLOR_BLUE = new Color(16, 108, 163);
    public static final Color COLOR_BLUE_DARKER = new Color(12, 94, 145);
    public static final Color COLOR_WHITE_TEXT = new Color(254, 254, 254);
    public static final Color COLOR_CENTRAL_BACK = new Color(25, 30, 34, 160);
    
    public static final Color bgcolors[] = new Color[] {
        COLOR_BLUE,
        getWebColor("1ABC9C"),
        getWebColor("9B59B6"),
        getWebColor("34495E"),
        getWebColor("E67E22"),
        getWebColor("E74C3C")
    };
    public static final Color bgcolors_darker[] = new Color[] {
        COLOR_BLUE_DARKER,
        getWebColor("16A085"),
        getWebColor("8E44AD"),
        getWebColor("2C3E50"),
        getWebColor("D35400"),
        getWebColor("C0392B")
    };
}
