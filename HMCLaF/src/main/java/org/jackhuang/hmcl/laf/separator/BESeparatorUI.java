/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BESeparatorUI.java at 2015-2-1 20:25:39, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.separator;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;

import javax.swing.JComponent;
import javax.swing.JSeparator;
import javax.swing.LookAndFeel;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSeparatorUI;

/**
 * Separator外观实现类.
 * <p>
 *
 * @author Jack Jiang(jb2011@163.com), 2012-11-05
 * @version 1.0
 */
public class BESeparatorUI extends BasicSeparatorUI {

    public static ComponentUI createUI(JComponent c) {
        return new BESeparatorUI();
    }

    @Override
    protected void installDefaults(JSeparator s) {
        LookAndFeel.installColors(s, "Separator.background", "Separator.foreground");
        LookAndFeel.installProperty(s, "opaque", Boolean.FALSE);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        //** 绘制border的底线
        //虚线样式
        Stroke oldStroke = ((Graphics2D) g).getStroke();
        Stroke sroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL, 0, new float[] { 2, 2 }, 0);//实线，空白
        ((Graphics2D) g).setStroke(sroke);//
//		super.paint(g, c);

        Dimension s = c.getSize();

        if (((JSeparator) c).getOrientation() == JSeparator.VERTICAL) {
//        	System.out.println("c.getBackground()c.getBackground()c.getBackground()="+c.getBackground());
            g.setColor(c.getForeground());
            g.drawLine(0, 0, 0, s.height);

            g.setColor(c.getBackground());
            g.drawLine(1, 0, 1, s.height);
        } else // HORIZONTAL
        {
            g.setColor(c.getForeground());
            g.drawLine(0, 0, s.width, 0);

            g.setColor(c.getBackground());
            g.drawLine(0, 1, s.width, 1);
        }

        ((Graphics2D) g).setStroke(oldStroke);
    }
}
