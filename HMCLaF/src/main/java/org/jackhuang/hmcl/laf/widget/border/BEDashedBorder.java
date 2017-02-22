/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEDashedBorder.java at 2015-2-1 20:25:41, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.widget.border;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

import javax.swing.border.LineBorder;
import javax.swing.plaf.UIResource;

import org.jackhuang.hmcl.laf.BEUtils;

/**
 * 虚线边框Border.
 *
 * @author Jack Jiang(jb2011@163.com)
 */
public class BEDashedBorder extends LineBorder implements UIResource {

    /**
     * 虚线段绘制步进.
     */
    private int step = 3;

    private boolean top = true, left = true, bottom = true, right = true;

    public BEDashedBorder(Color color, boolean top, boolean left, boolean bottom, boolean right) {
        super(color);
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    /**
     * 构造方法.
     *
     * @param color 虚线颜色
     * @param thickness 线框宽度
     * @param step 步进
     * @param top the top
     * @param left the left
     * @param bottom the bottom
     * @param right the right
     */
    public BEDashedBorder(Color color, int thickness, int step,
            boolean top, boolean left, boolean bottom, boolean right) {
        super(color, thickness);
        this.step = step;
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color oldColor = g.getColor();
        int i;

        g.setColor(lineColor);
        for (i = 0; i < thickness; i++)
            BEUtils.drawDashedRect(g, x + i, y + i, width - i - i, height - i - i, step, top, left, bottom, right);
        g.setColor(oldColor);
    }
}
