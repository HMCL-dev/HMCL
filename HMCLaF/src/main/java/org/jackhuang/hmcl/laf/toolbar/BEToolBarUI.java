/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEToolBarUI.java at 2015-2-1 20:25:41, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.toolbar;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Stroke;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicToolBarUI;

import org.jackhuang.hmcl.laf.button.BEButtonUI;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;
import org.jb2011.ninepatch4j.NinePatch;

/**
 * JToolBar的UI实现类。
 *
 * <p>
 * <b>Since v3.4：</b><br>
 * 可以使用"ToolBar.isPaintPlainBackground"来控制BEToolBarUI还是
 * 默认的渐变NinePatch图实现背景的填充（效果好，但是背景比较强烈，不适于组件密集的场景）.
 * 全局控制可以通过UIManager.put("ToolBar.isPaintPlainBackground",
 * Boolean.FALSE)（UIManager中默认是false）
 * 或者通过JToolBar.putClientProperty("ToolBar.isPaintPlainBackground",
 * Boolean.FALSE) 独立控制，ClientProperty中的设置拥有最高优先级。
 * 
 * 特别说明：JToolBar比较特殊，加入到JToolBar中的组件，其UI（主要是Border）将由ToolBarUI额
 * 外控制而不受自身UI控制，比如放入到JToolBar中的JToggleButton，它的border就是受ToolBarUI
 * 控制，这些JToggleButton将无论如何修改ToolgleButtonUI.border也不会起效。
 *
 * @author Jack Jiang(jb2011@163.com)
 * @see com.sun.java.swing.plaf.windows.WindowsToolBarUI
 */
public class BEToolBarUI extends BasicToolBarUI
        implements org.jackhuang.hmcl.laf.BeautyEyeLNFHelper.__UseParentPaintSurported {

    private static final Icon9Factory ICON_9 = new Icon9Factory("toolbar");

    public static ComponentUI createUI(JComponent c) {
        return new BEToolBarUI();
    }

    @Override
    protected void installDefaults() {
        setRolloverBorders(true);
        super.installDefaults();
    }

    //* 本方法由Jack Jiang于2012-09-07日加入
    /**
     * 是否使用父类的绘制实现方法，true表示是.
     * <p>
     * 因为在BE LNF中，工具条背景是使用N9图，没法通过设置背景色和前景
     * 色来控制工具条的颜色，本方法的目的就是当用户设置了工具条的Background 时告之本实现类不使用BE
     * LNF中默认的N9图填充绘制而改用父类中的方法（父类中的方法 就可以支持颜色的设置罗，只是丑点，但总归是能适应用户的需求场景要求，其实用户完全可以
     * 通过JToolBar.setUI(new MetalToolBar())等方式来自定义UI哦）.
     *
     * @return true, if is use parent paint
     */
    @Override
    public boolean isUseParentPaint() {
        return toolBar != null
                && (!(toolBar.getBackground() instanceof UIResource));
    }

    @Override
    protected Border createRolloverBorder() {
        return new EmptyBorder(3, 3, 3, 3);
    }

    @Override
    protected Border createNonRolloverBorder() {
        return new EmptyBorder(3, 3, 3, 3);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        //~* @since 3.4, add by Jack Jiang 2012-11-05
        //~* 【BeautyEye外观的特有定制属性】：true表示BEToolBarUI里，将使用其它典型外观
        //~*  一样的默认纯色填充背景（颜色由ToolBar.background属性指定）, 否则将使用BeautyEye
        //~*  默认的渐变NinePatch图实现背景的填充。另外，还可以使用
        //~*  JToolBar.putClientProperty("ToolBar.isPaintPlainBackground", Boolean.TRUE);来进行
        //~*  独立控制背景的填充方法，ClientProperty相比UIManager中的本方法拥有最高优先级
        boolean isPaintPlainBackground = false;
        String isPaintPlainBackgroundKey = "ToolBar.isPaintPlainBackground";//~* 【BeautyEye外观的特有定制属性】@since 3.4
        //首先看看有没有独立在ClientProperty中设置"ToolBar.isPaintPlainBackground"属性（ClientProperty中设置拥有最高优先级）
        Object isPaintPlainBackgroundObj = c.getClientProperty(isPaintPlainBackgroundKey);
        //如果ClientProperty中没有设置，则尝试取UIManager中的该属性默认值
        if (isPaintPlainBackgroundObj == null)
            isPaintPlainBackgroundObj = UIManager.getBoolean(isPaintPlainBackgroundKey);
        if (isPaintPlainBackgroundObj != null)
            isPaintPlainBackground = (Boolean) isPaintPlainBackgroundObj;

        //* 如果用户作了自定义颜色设置则使用父类方法来实现绘制，否则BE LNF中没法支持这些设置哦
        if (isPaintPlainBackground || isUseParentPaint())
            super.paint(g, c);
        else {
            //* 根据工具条所放在父类的位置不同来决定它的背景该使用哪个图片（图片的差别在于方向不同，主要是边缘阴影的方向）
            NinePatch np = ICON_9.get("north");
            //int orientation = toolBar.getOrientation();
            Container parent = toolBar.getParent();
            if (parent != null) {
                LayoutManager lm = parent.getLayout();
                if (lm instanceof BorderLayout) {
                    Object cons = ((BorderLayout) lm).getConstraints(toolBar);
                    if (cons != null)
                        if (cons.equals(BorderLayout.NORTH))
                            np = ICON_9.get("north");
                        else if (cons.equals(BorderLayout.SOUTH))
                            np = ICON_9.get("south");
                        else if (cons.equals(BorderLayout.WEST))
                            np = ICON_9.get("west");
                        else if (cons.equals(BorderLayout.EAST))
                            np = ICON_9.get("east");
                }
            }
            np.draw((Graphics2D) g, 0, 0, c.getWidth(), c.getHeight());
        }
    }

    /**
     * Gets the rollover border.
     *
     * @param b the b
     * @return the rollover border {@inheritDoc}
     */
    @Override
    protected Border getRolloverBorder(AbstractButton b) {
        return new BEButtonUI.BEEmptyBorder(new Insets(3, 3, 3, 3));
    }

    //* 由jb2011修改，只加了一行代码哦
    /**
     * 重写父类方法实现自已的容器监听器. 自定义的目的就是为了把加入到其中的组件设置为透明，因为BE LNF的工具栏是有背景，否则
     * 因有子组件的背景存在而使得整体很难看.
     *
     * @return the container listener
     */
    @Override
    protected ContainerListener createToolBarContListener() {
        return new ToolBarContListenerJb2011();
    }
    //* 由jb2011修改自父类的Handler里的ContainerListener监听代码

    /**
     * The Class ToolBarContListenerJb2011.
     */
    protected class ToolBarContListenerJb2011 implements ContainerListener {
        //
        // ContainerListener
        //

    @Override
        public void componentAdded(ContainerEvent evt) {
            Component c = evt.getChild();

            if (toolBarFocusListener != null)
                c.addFocusListener(toolBarFocusListener);

            if (isRolloverBorders())
                setBorderToRollover(c);
            else
                setBorderToNonRollover(c);

            //## Bug FIX：Issue 51(https://code.google.com/p/beautyeye/issues/detail?id=51)
            //* 由Jack Jiang201210-12日注释掉：它样做将导致各种放入的组
            //* 件（如文本框）等都将透明，从而不绘制该 组件的背景，那就错误了哦
            //* 其实以下代码原本是为了解决放入到JToggleButton的白色背景问题，现在它
            //* 已经在BEToolgleButtonUI里解决了，此处就不需要了，也不应该要！
//            //* 只有它一行是由jb2011加的
//            if(c instanceof JComponent)
//            	((JComponent)c).setOpaque(false);
        }

    @Override
        public void componentRemoved(ContainerEvent evt) {
            Component c = evt.getChild();

            if (toolBarFocusListener != null)
                c.removeFocusListener(toolBarFocusListener);

            // Revert the button border
            setBorderToNormal(c);
        }
    }

    //* 本类由Jack Jiang实现，参考了com.sun.java.swing.plaf.windows.WindowsBorders.getToolBarBorder
    /**
     * 工具条边框，左边（或右、或上方）有拖动触点的绘制，方便 告之用户它是可以拖动的 A border for the ToolBar. If the
     * ToolBar is floatable then the handle grip is drawn
     * <p>
     * @since 1.4
     */
    public static class ToolBarBorder extends AbstractBorder implements UIResource, SwingConstants {

        /**
         * The shadow.
         */
        protected Color shadow;
        /**
         * The highlight.
         */
        protected Color highlight;
        protected Insets insets;

        /**
         * Instantiates a new tool bar border.
         *
         * @param shadow the shadow
         * @param highlight the highlight
         */
        public ToolBarBorder(Color shadow, Color highlight, Insets insets) {
            this.highlight = highlight;
            this.shadow = shadow;
            this.insets = insets;
        }

    @Override
        public void paintBorder(Component c, Graphics g, int x, int y,
                int width, int height) {
            g.translate(x, y);

            //需要绘制拖动触点
            if (((JToolBar) c).isFloatable()) {
                boolean vertical = ((JToolBar) c).getOrientation() == VERTICAL;

                //虚线样式
                Stroke oldStroke = ((Graphics2D) g).getStroke();
                Stroke sroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_BEVEL, 0, new float[] { 1, 1 }, 0);//实线，空白
                ((Graphics2D) g).setStroke(sroke);
//    	        int gap_top = 5,gap_bottom = 4; //！！10,-1
                if (!vertical) {
                    int gap_top = 8, gap_bottom = 8;
                    if (c.getComponentOrientation().isLeftToRight()) {
                        int drawX = 3;
                        drawTouchH(g, drawX, gap_top - 1, drawX, height - gap_bottom - 1);
                    } else {
                        int drawX = 3 - 1;
                        drawTouchH(g, width - drawX, gap_top - 1, width - drawX, height - gap_bottom - 1);
                    }
                } else // Vertical
                {
                    int gap_left = 8, gap_right = 8;
                    int drawY = 3;
                    drawTouchV(g, gap_left - 1, drawY, width - gap_right - 1, drawY);
                }

                ((Graphics2D) g).setStroke(oldStroke);
            }

            g.translate(-x, -y);
        }

        //水平工具条的触点绘制方法
        private void drawTouchH(Graphics g, int x1, int y1, int x2, int y2) {
            //第1条竖虚线（深色）
            g.setColor(shadow);
            g.drawLine(x1, y1, x1, y2 - 1);
            //第2条竖虚线（与第1条形成立体效果的浅色，Y坐标相对第1条下偏一个像素）
            g.setColor(highlight);
            g.drawLine(x1 + 1, y1 + 1, x1 + 1, y2);

            //第3条竖虚线
            g.setColor(shadow);
            g.drawLine(x1 + 2, y1, x1 + 2, y2 - 1);
            //第4条竖虚线（与第3条形成立体效果的浅色，Y坐标相对第3条下偏一个像素）
            g.setColor(highlight);
            g.drawLine(x1 + 3, y1 + 1, x1 + 3, y2);
        }
        //垂直工具条的触点绘制方法

        private void drawTouchV(Graphics g, int x1, int y1, int x2, int y2) {
            //第1条横虚线（深色）
            g.setColor(shadow);
            g.drawLine(x1, y1, x2 - 1, y2);
            //第2条横虚线（与第1条形成立体效果的浅色，X坐标相对第1条右偏一个像素）
            g.setColor(highlight);
            g.drawLine(x1 + 1, y1 + 1, x2, y2 + 1);

            //第3条横虚线
            g.setColor(shadow);
            g.drawLine(x1, y1 + 2, x2 - 1, y2 + 2);
            //第4条横虚线（与第3条形成立体效果的浅色，X坐标相对第3条右偏一个像素）
            g.setColor(highlight);
            g.drawLine(x1 + 1, y1 + 3, x2, y2 + 3);
        }

    @Override
        public Insets getBorderInsets(Component c) {
            //** 根据toolbar所放面板的方位不同而设置不一样的border（参照的目标是水平toolbar时的insets）！
            //tollbar上下设置的空白多一点看起来大气一些（它也将决定toolbar的整体高度和宽度哦）
            final Insets DEFAILT_IS = insets;//8,0,9,0);//6, 0, 11, 0);
            Insets is = DEFAILT_IS;
            if (c instanceof JToolBar) {
                Container parent = c.getParent();
                if (parent != null) {
                    LayoutManager lm = parent.getLayout();
                    if (lm instanceof BorderLayout) {
                        Object cons = ((BorderLayout) lm).getConstraints((JToolBar) c);
                        if (cons != null)
                            if (cons.equals(BorderLayout.NORTH))
                                is = DEFAILT_IS;
                            else if (cons.equals(BorderLayout.SOUTH))
                                is = new Insets(DEFAILT_IS.bottom, 0, DEFAILT_IS.top, 0);
                            else if (cons.equals(BorderLayout.WEST))
                                is = new Insets(0, DEFAILT_IS.top, 0, DEFAILT_IS.bottom);
                            else if (cons.equals(BorderLayout.EAST))
                                is = new Insets(0, DEFAILT_IS.bottom, 0, DEFAILT_IS.top);
                    }
                }
            }

            return getBorderInsets(c, is);//5, 0, 10, 0));//默认是 1,1,1,1
        }

    @Override
        public Insets getBorderInsets(Component c, Insets insets) {
//    		insets.top = insets.left = insets.bottom = insets.right = 1;
            if (((JToolBar) c).isFloatable()) {
//    			int gripInset = (XPStyle.getXP() != null) ? 12 : 9;//原windows外观中默认的
                int gripInset = 9;
                if (((JToolBar) c).getOrientation() == HORIZONTAL)
                    if (c.getComponentOrientation().isLeftToRight())
                        insets.left = gripInset;
                    else
                        insets.right = gripInset;
                else
                    insets.top = gripInset;
            }
            return insets;
        }
    }
}
