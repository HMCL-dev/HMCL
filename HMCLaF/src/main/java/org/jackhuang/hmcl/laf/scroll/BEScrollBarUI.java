/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEScrollBarUI.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.scroll;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicScrollBarUI;

import org.jackhuang.hmcl.laf.BEUtils;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;
import org.jb2011.ninepatch4j.NinePatch;

/**
 * 本类是滚动条的UI实现.
 *
 * 注：滚动条的两端箭头按钮参考自xp主题的实现，未作修改，因而这部分逻辑代码与WindowsScrollBarUI中是完全一样的.
 *
 * @author Jack Jiang(jb2011@163.com), 2009-09-01
 * @version 1.0
 * @see com.sun.java.swing.plaf.windows.WindowsScrollBarUI
 */
public class BEScrollBarUI extends BasicScrollBarUI {

    public static final Icon9Factory ICON_9 = new Icon9Factory("scroll_bar");

    public static ComponentUI createUI(JComponent c) {
        return new BEScrollBarUI();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return new BEArrowButton(orientation,
                UIManager.getColor("ScrollBar.thumb"),
                UIManager.getColor("ScrollBar.thumbShadow"),
                UIManager.getColor("ScrollBar.thumbDarkShadow"),
                UIManager.getColor("ScrollBar.thumbHighlight"));
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return new BEArrowButton(orientation,
                UIManager.getColor("ScrollBar.thumb"),
                UIManager.getColor("ScrollBar.thumbShadow"),
                UIManager.getColor("ScrollBar.thumbDarkShadow"),
                UIManager.getColor("ScrollBar.thumbHighlight"));
    }

    /**
     * WindowsArrowButton is used for the buttons to position the document
     * up/down. It differs from BasicArrowButton in that the preferred size is
     * always a square.
     */
    protected class BEArrowButton extends BasicArrowButton {

        /**
         * Instantiates a new windows arrow button.
         *
         * @param direction the direction
         * @param background the background
         * @param shadow the shadow
         * @param darkShadow the dark shadow
         * @param highlight the highlight
         */
        public BEArrowButton(int direction, Color background, Color shadow,
                Color darkShadow, Color highlight) {
            super(direction, background, shadow, darkShadow, highlight);
        }

        public BEArrowButton(int direction) {
            super(direction);
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            String id = "arrow_";
            switch (direction) {
                case NORTH:
                    id += "top";
                    break;
                case SOUTH:
                    id += "bottom";
                    break;
                case WEST:
                    id += "left";
                    break;
                case EAST:
                    id += "right";
                    break;
            }
            ICON_9.get(id, getModel().isRollover() ? "rollover" : "")
                    .draw(g2, 0, 0, getWidth(), getHeight());
        }

        @Override
        public Dimension getPreferredSize() {
            int size = 16;
            if (scrollbar != null) {
                switch (scrollbar.getOrientation()) {
                    case JScrollBar.VERTICAL:
                        size = scrollbar.getWidth();
                        break;
                    case JScrollBar.HORIZONTAL:
                        size = scrollbar.getHeight();
                        break;
                }
                size = Math.max(size, 5);
            }
            return new Dimension(size, size);
        }
    }
    //----------------------------------------------------------------------------------- END

    //----------------------------------------------------------------------------------- 本次改造的主体部分
    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        if (c == null || g == null)
            return;
        Graphics2D g2 = (Graphics2D) g;

        Paint oldp = g2.getPaint();
        int w = trackBounds.width;
        int h = trackBounds.height;
        int x = trackBounds.x;
        int y = trackBounds.y;

        if (this.scrollbar.getOrientation() == JScrollBar.VERTICAL) {
//			//1/2处渐变
//			GradientPaint gp = new GradientPaint(x, y
//					, GraphicHandler.getColor(trackColor,-15,-15,-15), w/2, y,trackColor);
//			g2.setPaint(gp);
//			g2.fillRect(x, y, w/2, h);
//
//			g2.setPaint(oldp);
//			g2.setColor(trackColor);
//			g2.fillRect(w/2, y, w/2, h);

            //** 简洁版轨迹实现
            int hhhWidth = 5;
            int px = (w - hhhWidth) / 2;
            int delta = 50;
            //第1条
            g2.setColor(new Color(150 + delta, 151 + delta, 146 + delta));
            g2.drawLine(px + 0, y + 10, px + 0, y + h - 10);
            //第2条
            g2.setColor(new Color(160 + delta, 160 + delta, 162 + delta));
            g2.drawLine(px + 1, y + 10, px + 1, y + h - 10);
            //第3条
            g2.setColor(new Color(163 + delta, 162 + delta, 167 + delta));
            g2.drawLine(px + 2, y + 10, px + 2, y + h - 10);
            //第4条
            g2.setColor(new Color(162 + delta, 162 + delta, 162 + delta));
            g2.drawLine(px + 3, y + 10, px + 3, y + h - 10);
            //第5条
            g2.setColor(new Color(150 + delta, 150 + delta, 150 + delta));
            g2.drawLine(px + 4, y + 10, px + 4, y + h - 10);
        } else {
            //1/2处渐变
//			GradientPaint gp = new GradientPaint(x, y
//					, GraphicHandler.getColor(trackColor,-15,-15,-15), x, h/2,trackColor);
//			g2.setPaint(gp);
//			g2.fillRect(x, y, w, h/2);
//
//			g2.setPaint(oldp);
//			g2.setColor(trackColor);
//			g2.fillRect(x, h/2, w, h);

            //** 简洁版轨迹实现
            int hhhWidth = 5;
            int py = (h - hhhWidth) / 2;
            int delta = 50;
            //第1条
            g2.setColor(new Color(150 + delta, 151 + delta, 146 + delta));
            g2.drawLine(x + 10, py + 0, x + w - 10, py + 0);
            //第2条
            g2.setColor(new Color(160 + delta, 160 + delta, 162 + delta));
            g2.drawLine(x + 10, py + 1, x + w - 10, py + 1);
            //第3条
            g2.setColor(new Color(163 + delta, 162 + delta, 167 + delta));
            g2.drawLine(x + 10, py + 2, x + w - 10, py + 2);
            //第4条
            g2.setColor(new Color(162 + delta, 162 + delta, 162 + delta));
            g2.drawLine(x + 10, py + 3, x + w - 10, py + 3);
            //第5条
            g2.setColor(new Color(150 + delta, 150 + delta, 150 + delta));
            g2.drawLine(x + 10, py + 4, x + w - 10, py + 4);
        }
    }

    /**
     * 滚动条绘制.
     */
    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled())
            return;
        Graphics2D g2 = (Graphics2D) g;
        int w = thumbBounds.width - 4;
        int h = thumbBounds.height - 4;
        g2.translate(thumbBounds.x + 2, thumbBounds.y + 2);

//		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        BEUtils.setAntiAliasing(g2, true);

        //防止thunmb的最小高度比图片的最小高度还要小，这样在绘制时就会出问题
        //起实，目前没还没有办法很好解决，因为即使在这里作处理，但是thumb本身
        //还是那么小，所以绘图还是会有问题，但起码在不拖动时看起来是正常的，以后再解决吧！
        if (this.scrollbar.getOrientation() == JScrollBar.VERTICAL) {
            NinePatch np = ICON_9.getWithScrollState("vertical", isDragging, isThumbRollover());
            if (h < np.getHeight())
                paintThumbIfSoSmall(g2, 0, 0, w, h);
            else
                np.draw(g2, 0, 0, w, h);
        } else {
            NinePatch np = ICON_9.getWithScrollState("horizontal", isDragging, isThumbRollover());

            if (w < np.getWidth())
                paintThumbIfSoSmall(g2, 0, 0, w, h);
            else
                np.draw(g2, 0, 0, w, h);
        }
        //如果滚动行宽度小于NP图的最小宽度时则交给此方法绘制（否则NP图的填充将出现虚绘，而影响滚动条的体验哦）

        g2.translate(-thumbBounds.x, -thumbBounds.y);
//		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        BEUtils.setAntiAliasing(g2, false);
    }
    //----------------------------------------------------------------------------------- END

    /**
     * 如果滚动条非常小（小到小于NP图的最小大小）时调用此方法实现滚动条的精确绘制.
     *
     * @see javax.swing.plaf.basic.BasicScrollBarUI#paintThumb(java.awt.Graphics, javax.swing.JComponent, java.awt.Rectangle)
     */
    protected void paintThumbIfSoSmall(Graphics2D g2, int x, int y, int w, int h) {
        final int NORMAL_ARC = 6;//定义圆角直径
        //如果w或h太小时，则就不绘制圆角了(直角即可)，要不然就没法绘全圆角而很难看
        int arc = ((w <= NORMAL_ARC || h <= NORMAL_ARC) ? 0 : NORMAL_ARC);
        g2.setColor(thumbDarkShadowColor);
        g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc);//画滚动条的外层
        g2.setColor(thumbColor);
        g2.fillRoundRect(x + 1, y + 1, w - 2, h - 2, arc, arc);//填充滚动条的内层
    }
}
