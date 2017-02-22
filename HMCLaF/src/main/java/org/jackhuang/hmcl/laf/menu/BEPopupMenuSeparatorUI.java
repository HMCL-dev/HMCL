/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEPopupMenuSeparatorUI.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.menu;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;

import javax.swing.JComponent;
import javax.swing.JSeparator;
import javax.swing.LookAndFeel;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.SeparatorUI;

/**
 * JPopupMenuSeparator的UI实现.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @version 1.0
 */
public class BEPopupMenuSeparatorUI extends SeparatorUI {

    public static ComponentUI createUI(JComponent c) {
        return new BEPopupMenuSeparatorUI();
    }

    @Override
    public void installUI(JComponent c) {
        installDefaults((JSeparator) c);
        installListeners((JSeparator) c);
    }

    @Override
    public void uninstallUI(JComponent c) {
        uninstallDefaults((JSeparator) c);
        uninstallListeners((JSeparator) c);
    }

    protected void installDefaults(JSeparator s) {
        LookAndFeel.installColors(s, "Separator.background", "Separator.foreground");
        LookAndFeel.installProperty(s, "opaque", Boolean.FALSE);
    }

    protected void uninstallDefaults(JSeparator s) {
    }

    protected void installListeners(JSeparator s) {
    }

    protected void uninstallListeners(JSeparator s) {
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        int w = c.getWidth(), h = c.getHeight();
        Graphics2D g2 = (Graphics2D) g;

        if (((JSeparator) c).getOrientation() == JSeparator.VERTICAL) {
            //垂直坚线原始代码
            //TODO 垂直样式的竖线有时间再实现吧，垂直竖线默认用于JToolBar里
            g.setColor(c.getForeground());
            g.drawLine(0, 0, 0, c.getHeight());
            g.setColor(c.getBackground());
            g.drawLine(1, 0, 1, c.getHeight());
        } else // HORIZONTAL
        
            drawHorizonal(g2, c, w, h);
    }

    /**
     * 以水平方向绘制分隔线样式.
     */
    private void drawHorizonal(Graphics2D g2, JComponent c, int w, int h) {
        //** 绘制border的底线
        //虚线样式
        Stroke oldStroke = g2.getStroke();
        Stroke sroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL, 0, new float[] { 2, 2 }, 0);//实线，空白
        g2.setStroke(sroke);
        //底边上（浅灰色）
//		g2.setColor(new Color(180,180,180));
        g2.setColor(c.getForeground());
        g2.drawLine(0, h - 2, w - 1, h - 2); // draw bottom1
        //底边下（白色）：绘制一条白色虚线的目的是与上面的灰线产生较强对比度从而形成立体效果
        //，本L&F实现中因与Panel的底色对比度不够强烈而立体感不明显（颜色越深的底色最终效果越明显）
//		g2.setColor(Color.white);
        g2.setColor(c.getBackground());
        g2.drawLine(0, h - 1, w - 1, h - 1);//draw bottom2

        g2.setStroke(oldStroke);
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        if (((JSeparator) c).getOrientation() == JSeparator.VERTICAL)
            return new Dimension(2, 0);
        else
            return new Dimension(0, 3);
    }

    @Override
    public Dimension getMinimumSize(JComponent c) {
        return null;
    }

    @Override
    public Dimension getMaximumSize(JComponent c) {
        return null;
    }

}
