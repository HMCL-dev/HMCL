/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BERoundBorder.java at 2015-2-1 20:25:39, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.widget.border;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.AbstractButton;
import javax.swing.JToolBar;
import javax.swing.border.LineBorder;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;

import org.jackhuang.hmcl.laf.BEUtils;

/**
 * 圆角实线边框Border.
 *
 * @author Jack Jiang(jb2011@163.com)
 */
public class BERoundBorder extends LineBorder implements UIResource {

    /**
     * 默认绘制的色调.
     */
    public final static Color defaultLineColor = new Color(188, 188, 188);//154,154,155);// new Color(171,168,163);

    /**
     * 圆角的半径.
     */
    protected int arcWidth = 0;//6

    /**
     * Instantiates a new bE round border.
     */
    public BERoundBorder() {
        this(defaultLineColor, 1);
    }

    /**
     * Instantiates a new bE round border.
     *
     * @param color the color
     */
    public BERoundBorder(Color color) {
        this(color, 1);
    }

    /**
     * Instantiates a new bE round border.
     *
     * @param thickness the thickness
     */
    public BERoundBorder(int thickness) {
        this(defaultLineColor, thickness);
    }

    /**
     * Instantiates a new bE round border.
     *
     * @param color the color
     * @param thickness the thickness
     */
    public BERoundBorder(Color color, int thickness) {
        super(color, thickness);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return getBorderInsets(c, new Insets(0, 0, 0, 0));
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        Insets margin = null;

        if (c instanceof AbstractButton)
            margin = ((AbstractButton) c).getMargin();
        else if (c instanceof JToolBar)
            margin = ((JToolBar) c).getMargin();
        else if (c instanceof JTextComponent)
            margin = ((JTextComponent) c).getMargin();
        insets.top = (margin != null ? margin.top : 0) + thickness;
        insets.left = (margin != null ? margin.left : 0) + thickness;
        insets.bottom = (margin != null ? margin.bottom : 0) + thickness;
        insets.right = (margin != null ? margin.right : 0) + thickness;

        return insets;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color oldColor = g.getColor();

        BEUtils.setAntiAliasing((Graphics2D) g, true);
//		((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//				RenderingHints.VALUE_ANTIALIAS_ON);
        Component cp = c.getParent();
        if (cp != null) {
            //** 因java的textField边框默认是直角的，为了在LNF级别使加上圆连框后自然些，就得给这原始的直角先做隐藏处理（不要看见它）
            //				Color parentBackground=c.getParent().getBackground();
            //				g.setColor(parentBackground);
            //				g.drawRect(x, y, width-1, height-1);
        }

        //据thickness画出边框（当前是圆角的）
        g.setColor(lineColor);
        int i;
        for (i = 0; i < thickness; i++) {
            g.drawRoundRect(x + i, y + i, width - i - i - 1, height - i - i - 1, this.arcWidth, this.arcWidth);
            if (thickness > 1)
                g.setColor(BEUtils.getColor(g.getColor(), 70, 70, 70, -50));
        }

        g.setColor(oldColor);
//		((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//				RenderingHints.VALUE_ANTIALIAS_OFF);
        BEUtils.setAntiAliasing((Graphics2D) g, false);
    }

    /**
     * Sets the line color.
     *
     * @param c the c
     * @return the bE round border
     */
    public BERoundBorder setLineColor(Color c) {
        lineColor = c;
        return this;
    }

    @Override
    public Color getLineColor() {
        return lineColor;
    }

    /**
     * Sets the thickness.
     *
     * @param t the t
     * @return the bE round border
     */
    public BERoundBorder setThickness(int t) {
        thickness = t;
        return this;
    }

    @Override
    public Object clone() {
        BERoundBorder bb = new BERoundBorder(this.getLineColor(), this.getThickness());
        return bb;
    }

    /**
     * Gets the arc width.
     *
     * @return the arc width
     */
    public int getArcWidth() {
        return arcWidth;
    }

    /**
     * Sets the arc width.
     *
     * @param arcWidth the arc width
     * @return the bE round border
     */
    public BERoundBorder setArcWidth(int arcWidth) {
        this.arcWidth = arcWidth;
        return this;
    }
}
