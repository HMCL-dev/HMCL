/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BESliderUI.java at 2015-2-1 20:25:38, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.slider;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSliderUI;

import org.jackhuang.hmcl.laf.BEUtils;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;
import org.jackhuang.hmcl.laf.utils.IconFactory;

/**
 * JSlider的ui实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @version 1.0
 *
 * @see com.sun.java.swing.plaf.windows.WindowsComboBoxUI
 */
public class BESliderUI extends BasicSliderUI {

    private static final Icon9Factory ICON_9 = new Icon9Factory("slider_track");
    private static final IconFactory ICON = new IconFactory("slider");

    /**
     * 水平Slider的Thumb高度.
     */
    protected final int THUMB_HEIGHT_HORIZONAL = 7;// TODO 此属性可提取为Ui属性，方便以后配置（大小应是NP图的最小高度，最大值得看JSlider的高度了）

    /**
     * 垂直Slider的Thumb宽度.
     */
    protected final int THUMB_WIDTH_VERTICAL = 7;// TODO 此属性可提取为Ui属性，方便以后配置（大小应是NP图的最小高度，最大值得看JSlider的高度了）

    public BESliderUI(JSlider b) {
        super(b);
    }

    public static ComponentUI createUI(JComponent b) {
        return new BESliderUI((JSlider) b);
    }

    @Override
    public void paintTrack(Graphics g) {
        Rectangle trackBounds = trackRect;
        if (slider.getOrientation() == JSlider.HORIZONTAL) {
            int cy = (trackBounds.height / 2) - 2;
            int cw = trackBounds.width;

            g.translate(trackBounds.x, trackBounds.y + cy);

            //轨道背景
            ICON_9.getWithEnabled("", slider.isEnabled())
                    .draw((Graphics2D) g, 0, 0, cw, THUMB_HEIGHT_HORIZONAL);
            //轨道（填充到当前刻度值处）
            ICON_9.getWithEnabled("foreground", slider.isEnabled())
                    .draw((Graphics2D) g, 0, 0, thumbRect.x, THUMB_HEIGHT_HORIZONAL);
            g.translate(-trackBounds.x, -(trackBounds.y + cy));
        } else {
            int cx = (trackBounds.width / 2) - 2;
            int ch = trackBounds.height;

            g.translate(trackBounds.x + cx, trackBounds.y);

            //轨道背景
            ICON_9.getWithEnabled("vertical", slider.isEnabled())
                    .draw((Graphics2D) g, 0, 0, THUMB_WIDTH_VERTICAL, ch);
            //轨道（填充到当前刻度值处）
            // TODO BUG：当前有个bug，即在SwingSets2演示中，当thumb高度较小时，轨道半圆形被画出，这可能父类中thumbRect中计算有部碮同，有时间再研究吧，官方以后版本或许能解决哦
            ICON_9.getWithEnabled("vertical_foreground", slider.isEnabled())
                    .draw((Graphics2D) g, 0, thumbRect.y, THUMB_WIDTH_VERTICAL, ch - thumbRect.y);

            g.translate(-(trackBounds.x + cx), -trackBounds.y);
        }
    }

    @Override
    public void paintFocus(Graphics g) {
        g.setColor(getFocusColor());
        BEUtils.drawDashedRect(g, focusRect.x, focusRect.y,
                focusRect.width, focusRect.height);
    }

    /**
     * {@inheritDoc}
     *
     * @see javax.swing.plaf.basic.BasicSliderUI#paintThumb(java.awt.Graphics)
     */
    @Override
    public void paintThumb(Graphics g) {
        Rectangle knobBounds = thumbRect;
        int w = knobBounds.width;
        int h = knobBounds.height;

        g.translate(knobBounds.x, knobBounds.y);
        if (slider.isEnabled())
            g.setColor(slider.getBackground());
        else
            g.setColor(slider.getBackground().darker());

        g.drawImage(ICON.get(isPaintNoTriangleThumb() ? "notriangle" : "",
                slider.getOrientation() == JSlider.HORIZONTAL ? "" : "vertical",
                slider.isEnabled() ? "" : "disabled").getImage(), 0, 0, null);

        g.translate(-knobBounds.x, -knobBounds.y);
    }

    /**
     * Checks if is paint no triangle thumb.
     * 该thumb是否是无3角箭头的样式，true表示无3解箭头（即圆形thumb），false表示有3角箭头样式
     *
     * @return true, if is paint no trangle thumb
     */
    protected boolean isPaintNoTriangleThumb() {
        Boolean paintThumbArrowShape = (Boolean) slider
                .getClientProperty("Slider.paintThumbArrowShape");

        //不绘制有箭头标识的thumb样式(即普通圆形thumb)
        return !slider.getPaintTicks() && paintThumbArrowShape == null
                || Boolean.FALSE.equals(paintThumbArrowShape);
    }

    /**
     * {@inheritDoc}
     *
     * @see javax.swing.plaf.basic.BasicSliderUI#getThumbSize()
     */
    @Override
    protected Dimension getThumbSize() {
        boolean isPaintNoTrangle = isPaintNoTriangleThumb();

        Dimension size = new Dimension();
        if (slider.getOrientation() == JSlider.VERTICAL) {
            size.width = 17;//20;
            size.height = isPaintNoTrangle ? 16 : 12;//14;
        } else {
            size.width = isPaintNoTrangle ? 16 : 12;//14;
            size.height = 17;//20;
        }
        return size;
    }
}
