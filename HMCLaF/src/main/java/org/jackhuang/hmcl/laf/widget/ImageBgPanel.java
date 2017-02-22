/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * ImageBgPanel.java at 2015-2-1 20:25:38, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.widget;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.jb2011.ninepatch4j.NinePatch;

/**
 * 一个使用NinePatch图作为背景的面板实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 */
public class ImageBgPanel extends JPanel {

    private boolean drawBg = true;

    private NinePatch n9 = null;

    public ImageBgPanel() {
        this.setOpaque(false);
    }

    @Override
    public void paintChildren(Graphics g) {
        if (drawBg && n9 != null)
            n9.draw((Graphics2D) g, 0, 0, this.getWidth(), this.getHeight());
        super.paintChildren(g);
    }

    /**
     * 重写父类方法，以实现添加到它的所有子组件的透明性是按字段childOpaque指明的方式呈现.
     *
     * @param comp the comp
     * @param constraints the constraints
     * @param index the index
     */
    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        if (comp != null && comp instanceof JComponent)
            ((JComponent) comp).setOpaque(false);
        super.addImpl(comp, constraints, index);
    }

    /**
     * Checks if is draw bg.
     *
     * @return true, if is draw bg
     */
    public boolean isDrawBg() {
        return drawBg;
    }

    /**
     * Sets the draw bg.
     *
     * @param drawBg the draw bg
     * @return the image bg panel
     */
    public ImageBgPanel setDrawBg(boolean drawBg) {
        this.drawBg = drawBg;
        return this;
    }

    /**
     * Gets the n9.
     *
     * @return the n9
     */
    public NinePatch getN9() {
        return n9;
    }

    /**
     * Sets the n9.
     *
     * @param n9 the n9
     * @return the image bg panel
     */
    public ImageBgPanel setN9(NinePatch n9) {
        this.n9 = n9;
        return this;
    }
}
