/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEMenuItemUI.java at 2015-2-1 20:25:38, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.menu;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicMenuItemUI;

/**
 * JMenuItem的UI实现类.
 * 
 * 特别注意：BasicMenuItemUI中针对Vista及后来的Windows版本预设了很多其它LNF不需要的属性，
 * 预设的属性详见BasicMenuItemUI及WindowsLookAndFeel中的initVistaComponentDefaults(..)方法.
 * 这些属性只能会在vista及更新的windows平台上过运行时才会起效，所以除此之外的windows测不出来,
 * 容易出现ui视觉差异.
 *
 * @author Jack Jiang(jb2011@163.com)
 */
public class BEMenuItemUI extends BasicMenuItemUI {

    /**
     * 是否强制单项透明(当强制不透明时，在普通状态下该item将不会被绘制背景）.
     */
    private static boolean enforceTransparent = true;// TODO 可以提炼成UI属性

    public static ComponentUI createUI(JComponent c) {
        return new BEMenuItemUI();
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.sun.java.swing.plaf.windows.WindowsMenuItemUI#paintBackground(java.awt.Graphics, javax.swing.JMenuItem, java.awt.Color)
     * @see javax.swing.plaf.basic.BasicMenuItemUI#paintBackground(java.awt.Graphics, javax.swing.JMenuItem, java.awt.Color)
     */
    @Override
    protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
        ButtonModel model = menuItem.getModel();
        Color oldColor = g.getColor();
        int menuWidth = menuItem.getWidth();
        int menuHeight = menuItem.getHeight();

        Graphics2D g2 = (Graphics2D) g;

        if (model.isArmed()
                || (menuItem instanceof JMenu && model.isSelected()))
            //菜单项的样式绘制(用NinePatch图来填充)
            BEMenuUI.drawSelectedBackground(g, 0, 0, menuWidth, menuHeight);
        else if (!enforceTransparent) {
            g.setColor(menuItem.getBackground());
            g.fillRect(0, 0, menuWidth, menuHeight);
        }
        g.setColor(oldColor);
    }
}
