/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BESplitPaneDivider.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.split;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;

import javax.swing.JButton;
import javax.swing.JSplitPane;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import org.jackhuang.hmcl.laf.BEUtils;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;

/**
 * 分栏面板上的分隔线实现类.
 * <p>
 * TODO 两个Touch按钮的位置等都是可以定制的，目前没有更好的美化灵感，以后可以再深入优先。
 *
 * @author Jack Jiang(jb2011@163.com), 2012-07-13
 * @version 1.0
 */
public class BESplitPaneDivider extends BasicSplitPaneDivider {

    private static final Icon9Factory ICON_9 = new Icon9Factory("split_touch");

    private final int oneTouchSize;

    /**
     * TODO 本常量可以做成UIManager属性以便未来使用者进行配置哦.
     */
    protected final Color TOUCH_BUTTON_COLOR = new Color(58, 135, 173);
    
    /**
     * 在水平SplitePane状态下，中间触碰装饰区装饰按钮的宽度.
     */
    protected final static int TOUCH_DECRATED_BUTTON_W = 5;//* TODO 本常量可以做成UIManager属性以便未来使用者进行配置哦

    /**
     * 在水平SplitePane状态下，中间触碰装饰区装饰按钮的高度.
     */
    protected final static int TOUCH_DECRATED_BUTTON_H = 30;//* TODO 本常量可以做成UIManager属性以便未来使用者进行配置哦

    /**
     * 分隔条线直线的颜色.
     */
    protected final static Color TOUCH_DECRATED_BUTTON_COLOR = new Color(180, 180, 180);//* TODO 本颜色常量可以做成UIManager属性以便未来使用者进行配置哦
    
    /**
     * 分隔条线直线的高亮颜色（用来形成高对比度的立体效果） .
     */
    protected final static Color TOUCH_DECRATED_BUTTON_HILIGHT_COLOR = Color.white;//* TODO 本颜色常量可以做成UIManager属性以便未来使用者进行配置哦

    public BESplitPaneDivider(BasicSplitPaneUI ui) {
        super(ui);
        oneTouchSize = UIManager.getInt("SplitPane.oneTouchButtonSize", ui.getSplitPane().getLocale());
    }

    @Override
    public void paint(Graphics g) {
        Color bgColor = (splitPane.hasFocus())
                ? UIManager.getColor("SplitPane.shadow") : getBackground();
        Dimension size = getSize();
        Graphics2D g2 = ((Graphics2D) g);
        BEUtils.setAntiAliasing((Graphics2D) g, true);
//		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//				RenderingHints.VALUE_ANTIALIAS_ON);
        if (bgColor != null) {
            int orient = this.splitPane.getOrientation();
            if (orient == JSplitPane.HORIZONTAL_SPLIT) {
                int halfWidth = size.width / 2;
                int halfHeight = size.height / 2;

                //------------------------先水平居中画隔条竖线
                //虚线样式
                Stroke oldStroke = ((Graphics2D) g).getStroke();
                Stroke sroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_BEVEL, 0, new float[] { 2, 2 }, 0);//实线，空白
                ((Graphics2D) g).setStroke(sroke);

                g.setColor(TOUCH_DECRATED_BUTTON_COLOR);//bgColor);
//				g.fillRect(0, 0, size.width, size.height);
                g.drawLine(halfWidth + 0, 0, halfWidth + 0, size.height);
                //在竖线右边再画一个高对比度的竖线从而形成立体效果
                g.setColor(TOUCH_DECRATED_BUTTON_HILIGHT_COLOR);
                g.drawLine(halfWidth + 1, 0, halfWidth + 1, size.height);

                ((Graphics2D) g).setStroke(oldStroke);

                //------------------------再填充触碰装饰区
                int decratedButton_w = TOUCH_DECRATED_BUTTON_W;
                int decratedButton_h = TOUCH_DECRATED_BUTTON_H;//18;
                int diverTouchStartX = halfWidth - decratedButton_w / 2;
                ICON_9.get("bg1")
                        .draw((Graphics2D) g, diverTouchStartX, halfHeight - decratedButton_h / 2,
                                decratedButton_w, decratedButton_h);
            } else {
                int halfHeight = size.height / 2;
                int halfWidth = size.width / 2;

                //------------------------先垂直居中画分隔条横线
                //虚线样式
                Stroke oldStroke = ((Graphics2D) g).getStroke();
                Stroke sroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_BEVEL, 0, new float[] { 2, 2 }, 0);//实线，空白
                ((Graphics2D) g).setStroke(sroke);

                g.setColor(TOUCH_DECRATED_BUTTON_COLOR);//bgColor);
//				g.fillRect(0, 0, size.width, size.height);
                g.drawLine(0, halfHeight + 0, size.width, halfHeight + 0);
                //在竖线下边再画一个高对比度的横线从而形成立体效果
                g.setColor(TOUCH_DECRATED_BUTTON_HILIGHT_COLOR);
                g.drawLine(0, halfHeight + 1, size.width, halfHeight + 1);

                ((Graphics2D) g).setStroke(oldStroke);

                //------------------------再填充触碰装饰区
                int decratedButton_w = TOUCH_DECRATED_BUTTON_W;
                int decratedButton_h = TOUCH_DECRATED_BUTTON_H;//18;
                int diverTouchStartY = halfHeight - decratedButton_w / 2;
                ICON_9.get("bg1")
                        .draw((Graphics2D) g, halfWidth - decratedButton_h, diverTouchStartY,
                                decratedButton_h, decratedButton_w);
            }
//			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//					RenderingHints.VALUE_ANTIALIAS_OFF);
            BEUtils.setAntiAliasing((Graphics2D) g, false);
        }

        super.paint(g);
    }

    /**
     * Creates and return an instance of JButton that can be used to collapse
     * the left component in the split pane.
     * 
     * 因父类方法继承重用设计不佳，此处只能全部拷过来代码，再行修改。
     * 至2012-07-13：本方法只作了关于箭头按钮的填充颜色的改变。
     *
     * @return the j button
     * @see javax.swing.plaf.basic.BasicSplitPaneDivider#createLeftOneTouchButton() 
     */
    @Override
    protected JButton createLeftOneTouchButton() {
        JButton b = new JButton() {
            @Override
            public void setBorder(Border b) {
            }

            @Override
            public void paint(Graphics g) {
                if (splitPane != null) {
                    int[] xs = new int[3];
                    int[] ys = new int[3];
                    int blockSize;

                    // Fill the background first ...
                    g.setColor(this.getBackground());
                    g.fillRect(0, 0, this.getWidth(), this.getHeight());

                    // ... then draw the arrow.
                    g.setColor(TOUCH_BUTTON_COLOR);//Color.black);

                    //* 开启反走样
                    BEUtils.setAntiAliasing((Graphics2D) g, true);

                    if (orientation == JSplitPane.VERTICAL_SPLIT) {
                        blockSize = Math.min(getHeight(), oneTouchSize);
                        xs[0] = blockSize;
                        xs[1] = 0;
                        xs[2] = blockSize << 1;
                        ys[0] = 0;
                        ys[1] = ys[2] = blockSize;
                        g.drawPolygon(xs, ys, 3); // Little trick to make the
                        // arrows of equal size
                    } else {
                        blockSize = Math.min(getWidth(), oneTouchSize);
                        xs[0] = xs[2] = blockSize;
                        xs[1] = 0;
                        ys[0] = 0;
                        ys[1] = blockSize;
                        ys[2] = blockSize << 1;
                    }
                    g.fillPolygon(xs, ys, 3);

                    //* 关闭反走样
                    BEUtils.setAntiAliasing((Graphics2D) g, false);
                }
            }

            // Don't want the button to participate in focus traversable.
            @Override
            public boolean isFocusTraversable() {
                return false;
            }
        };
        b.setMinimumSize(new Dimension(oneTouchSize, oneTouchSize));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setRequestFocusEnabled(false);
        return b;
    }

    /**
     * Creates and return an instance of JButton that can be used to collapse
     * the right component in the split pane.
     * 
     * 因父类方法继承重用设计不佳，此处只能全部拷过来代码，再行修改。
     * 至2012-07-13：本方法只作了关于箭头按钮的填充颜色的改变。
     *
     * @return the j button
     * @see javax.swing.plaf.basic.BasicSplitPaneDivider#createRightOneTouchButton() 
     */
    @Override
    protected JButton createRightOneTouchButton() {
        JButton b = new JButton() {
            @Override
            public void setBorder(Border border) {
            }

            @Override
            public void paint(Graphics g) {
                if (splitPane != null) {
                    int[] xs = new int[3];
                    int[] ys = new int[3];
                    int blockSize;

                    // Fill the background first ...
                    g.setColor(this.getBackground());
                    g.fillRect(0, 0, this.getWidth(),
                            this.getHeight());

                    //* 开启反走样
                    BEUtils.setAntiAliasing((Graphics2D) g, true);

                    // ... then draw the arrow.
                    if (orientation == JSplitPane.VERTICAL_SPLIT) {
                        blockSize = Math.min(getHeight(), oneTouchSize);
                        xs[0] = blockSize;
                        xs[1] = blockSize << 1;
                        xs[2] = 0;
                        ys[0] = blockSize;
                        ys[1] = ys[2] = 0;
                    } else {
                        blockSize = Math.min(getWidth(), oneTouchSize);
                        xs[0] = xs[2] = 0;
                        xs[1] = blockSize;
                        ys[0] = 0;
                        ys[1] = blockSize;
                        ys[2] = blockSize << 1;
                    }

                    g.setColor(TOUCH_BUTTON_COLOR);//Color.black);

                    g.fillPolygon(xs, ys, 3);

                    //* 关闭反走样
                    BEUtils.setAntiAliasing((Graphics2D) g, false);
                }
            }

            // Don't want the button to participate in focus traversable.
            @Override
            public boolean isFocusTraversable() {
                return false;
            }
        };
        b.setMinimumSize(new Dimension(oneTouchSize, oneTouchSize));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setRequestFocusEnabled(false);
        return b;
    }
}
