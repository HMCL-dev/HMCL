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
import java.awt.Rectangle;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicScrollBarUI;
import org.jackhuang.hmcl.laf.button.BEButtonUI;
import org.jackhuang.hmcl.laf.utils.AnimationController;

import org.jackhuang.hmcl.laf.utils.Icon9Factory;
import org.jackhuang.hmcl.laf.utils.Skin;
import org.jackhuang.hmcl.laf.utils.TMSchema;
import org.jackhuang.hmcl.laf.utils.TMSchema.State;
import org.jackhuang.hmcl.util.ui.GraphicsUtils;

/**
 * 本类是滚动条的UI实现.
 *
 * 注：滚动条的两端箭头按钮参考自xp主题的实现，未作修改，因而这部分逻辑代码与WindowsScrollBarUI中是完全一样的.
 *
 * @author Jack Jiang(jb2011@163.com), 2009-09-01
 * @version 1.0
 * @see com.sun.java.swing.plaf.windows.WindowsScrollBarUI
 */
public class BEScrollBarUI extends BasicScrollBarUI implements Skin {

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
    protected class BEArrowButton extends BasicArrowButton implements Skin {

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
            switch (direction) {
                case NORTH:
                    break;
                case SOUTH:
                    g2.translate(getWidth(), getHeight());
                    g2.rotate(Math.PI);
                    break;
                case WEST:
                    g2.translate(0, getHeight());
                    g2.rotate(-Math.PI / 2);
                    break;
                case EAST:
                    g2.translate(getWidth(), 0);
                    g2.rotate(Math.PI / 2);
                    break;
            }
            AnimationController.paintSkin(this, this, g, 0, 0, getWidth(), getHeight(), BEButtonUI.getXPButtonState(this));
        }

        @Override
        public Dimension getPreferredSize() {
            int size = 12;
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

        @Override
        public void paintSkinRaw(Graphics g, int x, int y, int w, int h, TMSchema.State state) {
            ICON_9.get("arrow", state.toString()).draw((Graphics2D) g, x, y, w, h);
        }

        @Override
        public TMSchema.Part getPart(JComponent c) {
            return TMSchema.Part.SBP_ARROWBTN;
        }
    }
    //----------------------------------------------------------------------------------- END

    //----------------------------------------------------------------------------------- 本次改造的主体部分
    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        if (c == null || g == null)
            return;
        Color color = GraphicsUtils.getWebColor("#F2F2F2");
        g.setColor(color);
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
    }

    /**
     * 滚动条绘制.
     */
    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled())
            return;
        AnimationController.paintSkin(c, this, g, thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, isDragging ? State.PRESSED : isThumbRollover() ? State.ROLLOVER : State.NORMAL);
    }

    @Override
    public void paintSkinRaw(Graphics g, int x, int y, int w, int h, State state) {
        Color color = GraphicsUtils.getWebColor(state == State.PRESSED ? "#616161" : state == State.ROLLOVER ? "#919191" : "#C2C2C2");
        g.setColor(color);
        g.fillRect(x, y, w, h);
    }

    @Override
    public TMSchema.Part getPart(JComponent c) {
        return TMSchema.Part.SBP_THUMBBTNHORZ;
    }
    
    //----------------------------------------------------------------------------------- END

}
