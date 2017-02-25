/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hmcl.laf.utils;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.DimensionUIResource;
import javax.swing.plaf.InsetsUIResource;

/**
 *
 * @author huang
 */
public class UI {
    
    protected static void put(String key, int i) {
        UIManager.put(key, i);
    }
    
    protected static void put(String key, boolean b) {
        UIManager.put(key, b);
    }

    protected static void put(String key, Color c) {
        UIManager.put(key, new ColorUIResource(c));
    }

    protected static void put(String key, Border b) {
        UIManager.put(key, new BorderUIResource(b));
    }
    
    protected static void putDim(String key, int w, int h) {
        UIManager.put(key, new DimensionUIResource(h, h));
    }
    
    protected static void putInsets(String key, int top, int left, int bottom, int right) {
        UIManager.put(key, new InsetsUIResource(top, left, bottom, right));
    }
    
    protected static void putBorder(String key) {
        UIManager.put(key, BorderFactory.createEmptyBorder());
    }
    
    protected static void putBorder(String key, int top, int left, int bottom, int right) {
        UIManager.put(key, new BorderUIResource(BorderFactory.createEmptyBorder(top, left, bottom, right)));
    }
    
    protected static void putColor(String key, int r, int g, int b) {
        put(key, new Color(r, g, b));
    }
    
    protected static void putColor(String key, int r, int g, int b, int a) {
        put(key, new Color(r, g, b, a));
    }
    
    protected static void put(String key, Class c) {
        UIManager.put(key, c.getName());
    }
}
