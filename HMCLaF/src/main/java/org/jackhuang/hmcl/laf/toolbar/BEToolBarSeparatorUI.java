/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEToolBarSeparatorUI.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.toolbar;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;

import javax.swing.JComponent;
import javax.swing.JSeparator;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicToolBarSeparatorUI;

/**
 * JToolBar的分隔条UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @see com.sun.java.swing.plaf.windows.WindowsToolBarSeparatorUI
 */
public class BEToolBarSeparatorUI extends BasicToolBarSeparatorUI {

    public static ComponentUI createUI(JComponent c) {
        return new BEToolBarSeparatorUI();
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        Dimension size = ((JToolBar.Separator) c).getSeparatorSize();

        if (size != null)
            size = size.getSize();
        else {
            size = new Dimension(6, 6);

            if (((JSeparator) c).getOrientation() == SwingConstants.VERTICAL)
                size.height = 0;
            else
                size.width = 0;
        }
        return size;
    }

    @Override
    public Dimension getMaximumSize(JComponent c) {
        Dimension pref = getPreferredSize(c);
        if (((JSeparator) c).getOrientation() == SwingConstants.VERTICAL)
            return new Dimension(pref.width, Short.MAX_VALUE);
        else
            return new Dimension(Short.MAX_VALUE, pref.height);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        boolean vertical = ((JSeparator) c).getOrientation() == SwingConstants.VERTICAL;
        Dimension size = c.getSize();

        //虚线样式
        Stroke oldStroke = ((Graphics2D) g).getStroke();
        Stroke sroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL, 0, new float[] { 2, 2 }, 0);//实线，空白
        ((Graphics2D) g).setStroke(sroke);//

        Color temp = g.getColor();
        UIDefaults table = UIManager.getLookAndFeelDefaults();
        Color shadow = table.getColor("ToolBar.shadow");
        Color highlight = table.getColor("ToolBar.highlight");

        // TODO BUG_001：不知何故，垂直分隔条并不能像水平分隔条一样，拥有默认设置的new Dimension(6, 6)
        // 而只有new Dimension(1, ...)，而当它floating时却能正常表现(只能绘出hilight而不能绘出shadow)
        //，有待深入研究，垂直的分隔条则不会有此种情况
        if (vertical) {
            int x = (size.width / 2) - 1;

            //* 当BUG_001存在时，暂时使用以下代码解决：把本该显示hilight的
            //* 线条用shadow颜色绘制，最大可能保证ui的正常展现
//			g.setColor(shadow);
//			g.drawLine(x, 2, x, size.height - 2);
            g.setColor(shadow);//highlight);
            g.drawLine(x + 1, 2, x + 1, size.height - 2);

            //* 当BUG_001不存在时，应该使用以下代码
//			g.setColor(shadow);
//			g.drawLine(x, 2, x, size.height - 2);
//			g.setColor(highlight);
//			g.drawLine(x + 1, 2, x + 1, size.height - 2);
        } else {
            int y = (size.height / 2) - 1;
            g.setColor(shadow);
            g.drawLine(2, y, size.width - 2, y);

            g.setColor(highlight);
            g.drawLine(2, y + 1, size.width - 2, y + 1);
        }
        g.setColor(temp);

        ((Graphics2D) g).setStroke(oldStroke);
    }

}
