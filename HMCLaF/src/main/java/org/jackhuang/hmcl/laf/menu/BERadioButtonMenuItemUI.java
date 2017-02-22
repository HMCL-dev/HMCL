/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BERadioButtonMenuItemUI.java at 2015-2-1 20:25:37, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.menu;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.Serializable;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicRadioButtonMenuItemUI;
import org.jackhuang.hmcl.laf.utils.IconFactory;

/**
 * JRadioButtonMenuItem的UI实现.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @version 1.0
 */
public class BERadioButtonMenuItemUI extends BasicRadioButtonMenuItemUI {

    private static final IconFactory ICON = new IconFactory("menu_radio");

    public static ComponentUI createUI(JComponent b) {
        return new BERadioButtonMenuItemUI();
    }

    /**
     * Draws the background of the menu item.
     *
     * @param g the paint graphics
     * @param menuItem menu item to be painted
     * @param bgColor selection background color
     * @since 1.4
     * @see
     * javax.swing.plaf.basic.BasicMenuItemUI#paintBackground(java.awt.Graphics,
     * javax.swing.JMenuItem, java.awt.Color)
     */
    @Override
    protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
        ButtonModel model = menuItem.getModel();
        Color oldColor = g.getColor();
        int menuWidth = menuItem.getWidth();
        int menuHeight = menuItem.getHeight();

        if (menuItem.isOpaque()) {
            if (model.isArmed() || (menuItem instanceof JMenu && model.isSelected())) {
                g.setColor(bgColor);
                g.fillRect(0, 0, menuWidth, menuHeight);
            } else {
                g.setColor(menuItem.getBackground());
                g.fillRect(0, 0, menuWidth, menuHeight);
            }
            g.setColor(oldColor);
        } else if (model.isArmed() || (menuItem instanceof JMenu
                && model.isSelected()))
//    		g.setColor(bgColor);
//    		g.fillRect(0,0, menuWidth, menuHeight);
//    		g.setColor(oldColor);

            //由jb2011改用NinePatch图来填充
            BEMenuUI.drawSelectedBackground(g, 0, 0, menuWidth, menuHeight);
    }

    /**
     * @see com.sun.java.swing.plaf.windows.WindowsIconFactory.RadioButtonMenuItemIcon
     */
    public static class RadioButtonMenuItemIcon implements Icon, UIResource, Serializable {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            //* 注意：当用于windows平台专用主类且处于Vista及更高版win时要做不一样的处理哦
            g.drawImage(ICON.get(((AbstractButton) c).getModel().isSelected() ? "checked" : "normal").getImage(),
                    x - 4, y - 4, null);
        }

        @Override
        public int getIconWidth() {
            return 16;
        }//default 6

        @Override
        public int getIconHeight() {
            return 16;
        }//default 6

    }
}
