/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * WinUtils.java at 2015-2-1 20:25:37, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.utils;

import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.UIManager;

/**
 * The Class WinUtils.
 *
 * @see com.sun.java.swing.plaf.windows.WindowsGraphicsUtils
 */
public class WinUtils {
    //* copy from WindowsLookAndFeel START 未做修改
    // Toggle flag for drawing the mnemonic state

    /**
     * The is mnemonic hidden.
     */
    private static boolean isMnemonicHidden = true;

    /**
     * Gets the state of the hide mnemonic flag. This only has meaning if this
     * feature is supported by the underlying OS.
     *
     * @return true if mnemonics are hidden, otherwise, false
     * @see #setMnemonicHidden
     * @since 1.4
     */
    public static boolean isMnemonicHidden() {
        if (UIManager.getBoolean("Button.showMnemonics") == true)
            // Do not hide mnemonics if the UI defaults do not support this
            isMnemonicHidden = false;
        return isMnemonicHidden;
    }
    //* copy from WindowsLookAndFeel END

    //* copy from WindowsGraphicsUtils START (modified by jack jiang)
    /**
     * Renders a text String in Windows without the mnemonic. This is here
     * because the WindowsUI hiearchy doesn't match the Component heirarchy. All
     * the overriden paintText methods of the ButtonUI delegates will call this
     * static method.
     * <p>
     *
     * @param g Graphics context
     * @param b Current button to render
     * @param textRect Bounding rectangle to render the text.
     * @param text String to render
     * @param textShiftOffset the text shift offset
     */
    public static void paintText(Graphics g, AbstractButton b,
            Rectangle textRect, String text,
            int textShiftOffset) {
        FontMetrics fm = MySwingUtilities2.getFontMetrics(b, g);

        int mnemIndex = b.getDisplayedMnemonicIndex();
        // W2K Feature: Check to see if the Underscore should be rendered.
        if (isMnemonicHidden() == true)
            mnemIndex = -1;
        paintClassicText(b, g, textRect.x + textShiftOffset,
                textRect.y + fm.getAscent() + textShiftOffset,
                text, mnemIndex);
    }

    /**
     * Paint classic text.
     *
     * @param b the b
     * @param g the g
     * @param x the x
     * @param y the y
     * @param text the text
     * @param mnemIndex the mnem index
     */
    static void paintClassicText(AbstractButton b, Graphics g, int x, int y,
            String text, int mnemIndex) {
        ButtonModel model = b.getModel();

        /* Draw the Text */
        Color color = b.getForeground();
        if (model.isEnabled()) {
            /**
             * * paint the text normally
             */
            if (!(b instanceof JMenuItem && model.isArmed())
                    && !(b instanceof JMenu && (model.isSelected() || model.isRollover())))
                /* We shall not set foreground color for selected menu or
				 * armed menuitem. Foreground must be set in appropriate
				 * Windows* class because these colors passes from
				 * BasicMenuItemUI as protected fields and we can't
				 * reach them from this class */
                g.setColor(b.getForeground());
            MySwingUtilities2.drawStringUnderlineCharAt(b, g, text, mnemIndex, x, y);
        } else {
            /**
             * * paint the text disabled **
             */
            color = UIManager.getColor("Button.shadow");
            Color shadow = UIManager.getColor("Button.disabledShadow");
            if (model.isArmed())
                color = UIManager.getColor("Button.disabledForeground");
            else {
                if (shadow == null)
                    shadow = b.getBackground().darker();
                g.setColor(shadow);
                MySwingUtilities2.drawStringUnderlineCharAt(b, g, text, mnemIndex, x + 1, y + 1);
            }
            if (color == null)
                color = b.getBackground().brighter();
            g.setColor(color);
            MySwingUtilities2.drawStringUnderlineCharAt(b, g, text, mnemIndex, x, y);

        }
    }
    //* copy from WindowsGraphicsUtils END (modified by jack jiang)

    //* copy from WindowsGraphicsUtils START
    /**
     * 是否组件的排列方向是从左到右.
     *
     * @param c the c
     * @return true, if is left to right
     */
    public static boolean isLeftToRight(Component c) {
        return c.getComponentOrientation().isLeftToRight();
    }
    //* copy from WindowsGraphicsUtils END
}
