/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BETabbedPaneUI.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.tab;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * JTabbedPane的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com), 2012-01-12
 * @version 1.1
 * @see com.sun.java.swing.plaf.windows.WindowsTabbedPaneUI
 */
public class BETabbedPaneUI extends BasicTabbedPaneUI {

    public static ComponentUI createUI(JComponent c) {
        return new BETabbedPaneUI();
    }

    /**
     * 本方法的重写copy自 com.sun.java.swing.plaf.windows.WindowsTabbedPaneUI，基本未修改代码
     * 重写父类方法实现rover状态下的tab的ui重绘（父类方法只是实现了rolloverTab 的设置，不步及ui重绘制，因Basic
     * LNF中没有实现rover状态的ui样式）
     *
     * @see javax.swing.plaf.basic.BasicTabbedPaneUI#setRolloverTab(int)
     */
    @Override
    protected void setRolloverTab(int index) {
        int oldRolloverTab = getRolloverTab();
        super.setRolloverTab(index);
        Rectangle r1 = null;
        Rectangle r2 = null;
        if ((oldRolloverTab >= 0) && (oldRolloverTab < tabPane.getTabCount()))
            r1 = getTabBounds(tabPane, oldRolloverTab);
        if (index >= 0)
            r2 = getTabBounds(tabPane, index);
        if (r1 != null)
            if (r2 != null)
                tabPane.repaint(r1.union(r2));
            else
                tabPane.repaint(r1);
        else if (r2 != null)
            tabPane.repaint(r2);
    }

    /**
     * this function draws the border around each tab note that this function
     * does now draw the background of the tab. that is done elsewhere.
     *
     * @param g the g
     * @param tabPlacement the tab placement
     * @param tabIndex the tab index
     * @param x the x
     * @param y the y
     * @param w the w
     * @param h the h
     * @param isSelected the is selected
     * @see javax.swing.plaf.basic.BasicTabbedPaneUI#paintTabBorder(java.awt.Graphics, int, int, int, int, int, int, boolean)
     */
    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement,
            int tabIndex, int x, int y, int w, int h, boolean isSelected) {
//		g.setColor(lightHighlight);  
//		Graphics2D g2d = (Graphics2D)g;
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.translate(x, y);

        //* 由Jack Jiang添加：true表示该tab当前正处于鼠标rover其上的状态
        //* this.getRolloverTab()的返回值由父类方法 setRolloverTab()设定并实现ui重绘的
        boolean isRover = (this.getRolloverTab() == tabIndex);
        //* 由Jack Jiang添加：true表示该tab处于可用状态，否则表示处于禁用状态
        boolean isEnableAt = this.tabPane.isEnabledAt(tabIndex);

        switch (tabPlacement) {
            case LEFT:
                g2d.scale(-1.0, 1.0);
                g2d.rotate(Math.toRadians(90.0));
                paintTabBorderImpl(g2d, isEnableAt, isSelected, isRover, 0, 0, h, w);
                break;
            case RIGHT:
                g2d.translate(w, 0);
                g2d.rotate(Math.toRadians(90.0));
                paintTabBorderImpl(g2d, isEnableAt, isSelected, isRover, 0, 0, h, w);
                break;
            case BOTTOM:
                g2d.translate(0, h);
                g2d.scale(-1.0, 1.0);
                g2d.rotate(Math.toRadians(180.0));
                paintTabBorderImpl(g2d, isEnableAt, isSelected, isRover, 0, 0, w, h);
                break;
            case TOP:
            default:
                paintTabBorderImpl(g2d, isEnableAt, isSelected, isRover, 0, 0, w, h);
                break;
        }
    }
    //* paintTabBorder的绘制实现方法，2012-01-12日 BY Jack Jiang

    /**
     * Paint tab border impl.
     *
     * @param g2d the g2d
     * @param isEnableAt the is enable at
     * @param isSelected the is selected
     * @param isRover the is rover
     * @param x the x
     * @param y the y
     * @param w the w
     * @param h the h
     */
    private void paintTabBorderImpl(Graphics2D g2d, boolean isEnableAt, boolean isSelected,
            boolean isRover, int x, int y, int w, int h) {
        //** modified by jb2011 改为NinePatch图片实现，Y + 1 的目的是使得选中时的底线能往下画一个像素（这样好看点）
        if (isSelected) {
            g2d.setColor(UIManager.getColor("TabbedPane.selectedForeground"));
            g2d.fillRect(x, y + h - 1, w, 2);
        }
        //ICON_9.get("", isSelected ? "selected" : (isEnableAt && isRover ? "normal_rollover" : "normal"))
        //        .draw(g2d, x, y + 1, w, h);
    }

    /**
     * 重写本方法的目的仅是把原来的默认实线变成虚线而已哦
     *
     * @see
     * javax.swing.plaf.basic.BasicTabbedPaneUI#paintContentBorder(java.awt.Graphics,
     * int, int)
     */
    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        /*//*** add by jb2011 2012-08-23 START
        //虚线样式
        Stroke oldStroke = ((Graphics2D) g).getStroke();
        Stroke sroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL, 0, new float[] { 2, 2 }, 0);//实线，空白
        ((Graphics2D) g).setStroke(sroke);
        //*** add by jb2011 2012-08-23 END
*/
        //调用父类默认实现
        super.paintContentBorder(g, tabPlacement, selectedIndex);
/*
        //*** add by jb2011 2012-08-23 START
        ((Graphics2D) g).setStroke(oldStroke);
        //*** add by jb2011 2012-08-23 END*/
    }

    //
    /**
     * JTabbedPaneUI 内容面板的上边框绘制方法（默认就是内容面板上方的那条灰色）.
     *
     * @see
     * javax.swing.plaf.basic.BasicTabbedPaneUI#paintContentBorderTopEdge(java.awt.Graphics,
     * int, int, int, int, int, int)
     */
    @Override
    protected void paintContentBorderTopEdge(Graphics g, int tabPlacement,
            int selectedIndex,
            int x, int y, int w, int h) {
        //此模式下不绘制其它3条边框，视觉上好看一些
        if (tabPlacement == TOP)
            //调用父类默认实现
            super.paintContentBorderTopEdge(g, tabPlacement, selectedIndex, x, y, w, h);
    }

    /**
     * JTabbedPaneUI 内容面板的左边框绘制方法.
     *
     * @see
     * javax.swing.plaf.basic.BasicTabbedPaneUI#paintContentBorderLeftEdge(java.awt.Graphics,
     * int, int, int, int, int, int)
     */
    @Override
    protected void paintContentBorderLeftEdge(Graphics g, int tabPlacement,
            int selectedIndex,
            int x, int y, int w, int h) {
        //此模式下不绘制其它3条边框，视觉上好看一些
        if (tabPlacement == LEFT)
            //调用父类默认实现
            super.paintContentBorderLeftEdge(g, tabPlacement, selectedIndex, x, y, w, h);
    }

    /**
     * JTabbedPaneUI 内容面板的底边框绘制方法.
     *
     * @see
     * javax.swing.plaf.basic.BasicTabbedPaneUI#paintContentBorderBottomEdge(java.awt.Graphics,
     * int, int, int, int, int, int)
     */
    @Override
    protected void paintContentBorderBottomEdge(Graphics g, int tabPlacement,
            int selectedIndex,
            int x, int y, int w, int h) {
        //此模式下不绘制其它3条边框，视觉上好看一些
        if (tabPlacement == BOTTOM)
            //调用父类默认实现
            super.paintContentBorderBottomEdge(g, tabPlacement, selectedIndex, x, y, w, h);
    }

    /**
     * JTabbedPaneUI 内容面板的右边框绘制方法.
     *
     * @see
     * javax.swing.plaf.basic.BasicTabbedPaneUI#paintContentBorderRightEdge(java.awt.Graphics,
     * int, int, int, int, int, int)
     */
    @Override
    protected void paintContentBorderRightEdge(Graphics g, int tabPlacement,
            int selectedIndex,
            int x, int y, int w, int h) {
        //此模式下不绘制其它3条边框，视觉上好看一些
        if (tabPlacement == RIGHT)
            //调用父类默认实现
            super.paintContentBorderRightEdge(g, tabPlacement, selectedIndex, x, y, w, h);
    }

    /**
     * 获得焦点时的虚线框绘制方法
     *
     * @see
     * javax.swing.plaf.basic.BasicTabbedPaneUI#paintFocusIndicator(java.awt.Graphics,
     * int, java.awt.Rectangle[], int, java.awt.Rectangle, java.awt.Rectangle,
     * boolean)
     */
    @Override
    protected void paintFocusIndicator(Graphics g, int tabPlacement,
            Rectangle[] rects, int tabIndex,
            Rectangle iconRect, Rectangle textRect,
            boolean isSelected) {
    }

    /**
     * 重写并修改本方法的目的是修正tab上的文本显示Y坐标方向上的偏移，以便与背景协调
     * @see javax.swing.plaf.basic.BasicTabbedPaneUI#getTabLabelShiftY(int, int, boolean)
     */
    @Override
    protected int getTabLabelShiftY(int tabPlacement, int tabIndex, boolean isSelected) {
        return 0;
        /*
        switch (tabPlacement) {
            case BOTTOM:
                return isSelected ? 1 : -1;
            case LEFT:
            case RIGHT:
                return rects[tabIndex].height % 2;
            case TOP:
            default:
                //** 由jb2011 2012-08-24修改：目的是使得选中时和未选中时的文本（包括图标
                //** 默认实现中之所以要产生它个效果是为了营造立体效果，而BE LNF中并不需要
                //** ）不要往上或往下偏移的太多（太多则相当难看）
//				nudge = isSelected? -1 : 1;//本行是原父类中的默认实现哦
                return -2;//由jb2011修改，目的是让文本相对现在的背景往上偏移一点，好看一些
        }*/
    }
    
    @Override
    protected int getTabLabelShiftX(int tabPlacement, int tabIndex, boolean isSelected) {
        Rectangle tabRect = rects[tabIndex];
        String propKey = "labelShift";
        int nudge = UIManager.getInt("TabbedPane." + propKey);

        switch (tabPlacement) {
            case LEFT:
                return nudge;
            case RIGHT:
                return -nudge;
            case BOTTOM:
            case TOP:
            default:
                return tabRect.width % 2;
        }
    }
}
