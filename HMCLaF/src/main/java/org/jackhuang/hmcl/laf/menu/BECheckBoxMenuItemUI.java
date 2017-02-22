/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BECheckBoxMenuItemUI.java at 2015-2-1 20:25:39, original version by Jack Jiang.
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
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI;
import org.jackhuang.hmcl.laf.utils.IconFactory;

/**
 * JCheckBoxMenuItem的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 */
public class BECheckBoxMenuItemUI extends BasicCheckBoxMenuItemUI {

    private static final IconFactory ICON = new IconFactory("menu_check");

    /**
     * 是否强制单项透明(当强制不透明时，在普通状态下该item将不会被绘制背景）.
     */
    private static boolean enforceTransparent = true;

    public static ComponentUI createUI(JComponent b) {
        return new BECheckBoxMenuItemUI();
    }

    @Override
    protected void paintBackground(Graphics g, JMenuItem menuItem,
            Color bgColor) {
//        if (WindowsMenuItemUI.isVistaPainting()) {
//            WindowsMenuItemUI.paintBackground(accessor, g, menuItem, bgColor);
//            return;
//        }
//        super.paintBackground(g, menuItem, bgColor);
        // see parent!
        ButtonModel model = menuItem.getModel();
        Color oldColor = g.getColor();
        int menuWidth = menuItem.getWidth();
        int menuHeight = menuItem.getHeight();

        Graphics2D g2 = (Graphics2D) g;

        if (model.isArmed()
                || (menuItem instanceof JMenu && model.isSelected())) {
            g.setColor(bgColor);

            BEMenuUI.drawSelectedBackground(g, 0, 0, menuWidth, menuHeight);

//			/****** 选中的背景样式现在是渐变加圆角填充了,impled by js */
//			NLLookAndFeel.setAntiAliasing(g2, true);
//			//矩形填充
//			Paint oldpaint = g2.getPaint();
//			GradientPaint gp = new GradientPaint(0, 0
//					,GraphicHandler.getColor(bgColor, 35, 35, 35)
//					,0, menuHeight,bgColor
//	                );
//			g2.setPaint(gp);
//			g.fillRoundRect(0, 0, menuWidth, menuHeight,5,5);
//			g2.setPaint(oldpaint);
//			NLLookAndFeel.setAntiAliasing(g2, false);
        } else if (!enforceTransparent) {
            g.setColor(menuItem.getBackground());
            g.fillRect(0, 0, menuWidth, menuHeight);
        }
        g.setColor(oldColor);
    }

    /**
     * @see
     * com.sun.java.swing.plaf.windows.WindowsIconFactory.CheckBoxMenuItemIcon
     */
    public static class CheckBoxMenuItemIcon implements Icon, UIResource, Serializable {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.drawImage(ICON.get(((AbstractButton) c).getModel().isSelected() ? "checked" : "normal").getImage(),
                    x - 4, y - 3, null);
        }

        @Override
        public int getIconWidth() {
            return 16;
        }// default 9

        @Override
        public int getIconHeight() {
            return 16;
        }// default 9
    }
}
