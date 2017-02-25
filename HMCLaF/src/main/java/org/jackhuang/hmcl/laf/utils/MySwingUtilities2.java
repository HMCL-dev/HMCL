/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * MySwingUtilities2.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.utils;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;

import javax.swing.JComponent;

/**
 * 本类中的方法一一对应于SUN未公开的类SwingUtilities2中的方法.
 *
 * 部分代码摘自JDK 1.5.
 * 
 * @author Jack Jiang(jb2011@163.com)
 * @author huangyuhui
 */
public class MySwingUtilities2 {

    /**
     * Returns the FontMetrics for the current Font of the passed in Graphics.
     * This method is used when a Graphics is available, typically when
     * painting. If a Graphics is not available the JComponent method of the
     * same name should be used.
     * <p>
     * Callers should pass in a non-null JComponent, the exception to this is if
     * a JComponent is not readily available at the time of painting.
     * <p>
     * This does not necessarily return the FontMetrics from the Graphics.
     *
     * @param c JComponent requesting FontMetrics, may be null
     * @param g Graphics Graphics
     * @return the font metrics
     */
    public static FontMetrics getFontMetrics(JComponent c, Graphics g) {
        return getFontMetrics(c, g, g.getFont());
    }

    /**
     * Returns the FontMetrics for the specified Font. This method is used when
     * a Graphics is available, typically when painting. If a Graphics is not
     * available the JComponent method of the same name should be used.
     * <p>
     * Callers should pass in a non-null JComonent, the exception to this is if
     * a JComponent is not readily available at the time of painting.
     * <p>
     * This does not necessarily return the FontMetrics from the Graphics.
     *
     * @param c Graphics Graphics
     * @param g the g
     * @param font Font to get FontMetrics for
     * @return the font metrics
     */
    public static FontMetrics getFontMetrics(JComponent c, Graphics g,
            Font font) {
        if (c != null)
            // Note: We assume that we're using the FontMetrics
            // from the widget to layout out text, otherwise we can get
            // mismatches when printing.
            return c.getFontMetrics(font);
        return Toolkit.getDefaultToolkit().getFontMetrics(font);
    }

    /**
     * Returns the width of the passed in String.
     *
     * @param c JComponent that will display the string, may be null
     * @param fm FontMetrics used to measure the String width
     * @param string String to get the width of
     * @return the int
     */
    public static int stringWidth(JComponent c, FontMetrics fm, String string) {
        return fm.stringWidth(string);
    }

    /**
     * Draws the string at the specified location.
     *
     * @param c JComponent that will display the string, may be null
     * @param g Graphics to draw the text to
     * @param text String to display
     * @param x X coordinate to draw the text at
     * @param y Y coordinate to draw the text at
     */
    public static void drawString(JComponent c, Graphics g, String text,
            int x, int y) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawString(text, x, y);
    }

    /**
     * Draws the string at the specified location underlining the specified
     * character.
     *
     * @param c JComponent that will display the string, may be null
     * @param g Graphics to draw the text to
     * @param text String to display
     * @param underlinedIndex Index of a character in the string to underline
     * @param x X coordinate to draw the text at
     * @param y Y coordinate to draw the text at
     */
    public static void drawStringUnderlineCharAt(JComponent c, Graphics g,
            String text, int underlinedIndex, int x, int y) {
        drawString(c, g, text, x, y);
        if (underlinedIndex >= 0 && underlinedIndex < text.length()) {
            // PENDING: this needs to change.
            FontMetrics fm = g.getFontMetrics();
            int underlineRectX = x + stringWidth(c,
                    fm, text.substring(0, underlinedIndex));
            int underlineRectY = y;
            int underlineRectWidth = fm.charWidth(text.
                    charAt(underlinedIndex));
            int underlineRectHeight = 1;
            g.fillRect(underlineRectX, underlineRectY + 1,
                    underlineRectWidth, underlineRectHeight);
        }
    }

    /**
     * Clips the passed in String to the space provided.
     *
     * @param c JComponent that will display the string, may be null
     * @param fm FontMetrics used to measure the String width
     * @param string String to display
     * @param availTextWidth Amount of space that the string can be drawn in
     * @return Clipped string that can fit in the provided space.
     */
    public static String clipStringIfNecessary(JComponent c, FontMetrics fm,
            String string, int availTextWidth) {
        if ((string == null) || (string.equals("")))
            return "";
        int textWidth = stringWidth(c, fm, string);
        if (textWidth > availTextWidth)
            return clipString(c, fm, string, availTextWidth);
        return string;
    }

    /**
     * Clips the passed in String to the space provided. NOTE: this assumes the
     * string does not fit in the available space.
     *
     * @param c JComponent that will display the string, may be null
     * @param fm FontMetrics used to measure the String width
     * @param string String to display
     * @param availTextWidth Amount of space that the string can be drawn in
     * @return Clipped string that can fit in the provided space.
     */
    public static String clipString(JComponent c, FontMetrics fm,
            String string, int availTextWidth) {
        // c may be null here.
        String clipString = "...";
        int width = stringWidth(c, fm, clipString);
        // NOTE: This does NOT work for surrogate pairs and other fun
        // stuff
        int nChars = 0;
        for (int max = string.length(); nChars < max; nChars++) {
            width += fm.charWidth(string.charAt(nChars));
            if (width > availTextWidth)
                break;
        }
        string = string.substring(0, nChars) + clipString;
        return string;
    }
}
