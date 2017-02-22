/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEDashedRoundRecBorder.java at 2015-2-1 20:25:39, original version by Jack Jiang.
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
 * 虚线圆角边框Border.
 * <p>
 * TODO 目前圆角大小和虚线间隔等目前都是固定的，进一步重构后可以进行重用哦.
 *
 * @author Jack Jiang(jb2011@163.com)
 */
public class BEDashedRoundRecBorder extends LineBorder implements UIResource {

    /**
     * The separator space.
     */
    private int arcWidth = 6, arcHeight = 6, separatorSolid = 2, separatorSpace = 2;

    /**
     * 构造方法.
     *
     * @param color 虚线颜色
     */
    public BEDashedRoundRecBorder(Color color) {
        super(color);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color oldColor = g.getColor();
        g.setColor(lineColor);
        BEUtils.drawDashedRect(g, x, y, width, height, arcWidth, arcHeight, separatorSolid, separatorSpace);
        g.setColor(oldColor);
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
     * @return the bE dashed round rec border
     */
    public BEDashedRoundRecBorder setArcWidth(int arcWidth) {
        this.arcWidth = arcWidth;
        return this;
    }

    /**
     * Gets the arc height.
     *
     * @return the arc height
     */
    public int getArcHeight() {
        return arcHeight;
    }

    /**
     * Sets the arc height.
     *
     * @param arcHeight the arc height
     * @return the bE dashed round rec border
     */
    public BEDashedRoundRecBorder setArcHeight(int arcHeight) {
        this.arcHeight = arcHeight;
        return this;
    }

    /**
     * Gets the separator solid.
     *
     * @return the separator solid
     */
    public int getSeparatorSolid() {
        return separatorSolid;
    }

    /**
     * Sets the separator solid.
     *
     * @param separatorSolid the separator solid
     * @return the bE dashed round rec border
     */
    public BEDashedRoundRecBorder setSeparatorSolid(int separatorSolid) {
        this.separatorSolid = separatorSolid;
        return this;
    }

    /**
     * Gets the separator space.
     *
     * @return the separator space
     */
    public int getSeparatorSpace() {
        return separatorSpace;
    }

    /**
     * Sets the separator space.
     *
     * @param separatorSpace the separator space
     * @return the bE dashed round rec border
     */
    public BEDashedRoundRecBorder setSeparatorSpace(int separatorSpace) {
        this.separatorSpace = separatorSpace;
        return this;
    }

}
