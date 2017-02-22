/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * SplitPaneDividerBorder.java at 2015-2-1 20:25:41, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.split;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.JSplitPane;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * 分隔条的border实现类.
 * <p>
 * Draws the border around the divider in a splitpane. To get the appropriate
 * effect, this needs to be used with a SplitPaneBorder.
 *
 * 主要修改了UI填充实现部分
 *
 * @author Jack Jiang(jb2011@163.com)
 * @see javax.swing.plaf.basic.BasicBorders
 */
public class SplitPaneDividerBorder implements Border, UIResource {
//	javax.swing.plaf.basic.BasicBorders.SplitPaneDividerBorder
//	private Color highlight;
//	private Color shadow;

//	public SplitPaneDividerBorder(Color highlight, Color shadow)
//	{
//		this.highlight = highlight;
//		this.shadow = shadow;
//	}
    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width,
            int height) {
        //在目前的视觉效果下不需要这个border的绘制哦
//		Graphics2D g2d = (Graphics2D) g;
//		Component child;
//		Rectangle cBounds;
//		JSplitPane splitPane = ((BasicSplitPaneDivider) c).getBasicSplitPaneUI().getSplitPane();
//		Dimension size = c.getSize();
//
//		child = splitPane.getLeftComponent();
//		// This is needed for the space between the divider and end of
//		// splitpane.
//		g.setColor(c.getBackground());
//		g.drawRect(x, y, width - 1, height - 1);
//		
//		if (splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT)
//		{
////			if (child != null)
////			{
////				g.setColor(shadow);//highlight);
////				g.drawLine(0, 0, 0, size.height);
////			}
////			child = splitPane.getRightComponent();
////			if (child != null)
////			{
////				g.setColor(shadow);
////				g.drawLine(size.width - 1, 0, size.width - 1, size.height);
////			}
//		}
//		else
//		{
////			if (child != null)
////			{
////				g.setColor(shadow);//highlight);
////				g.drawLine(0, 0, size.width, 0);
////			}
////			child = splitPane.getRightComponent();
////			if (child != null)
////			{
////				g.setColor(shadow);
////			    g.drawLine(0, size.height - 1, size.width,size.height - 1);
////
////			}
//		}
    }

    @Override
    public Insets getBorderInsets(Component c) {
        Insets insets = new Insets(0, 0, 0, 0);
        if (c instanceof BasicSplitPaneDivider) {
            BasicSplitPaneUI bspui = ((BasicSplitPaneDivider) c)
                    .getBasicSplitPaneUI();

            if (bspui != null) {
                JSplitPane splitPane = bspui.getSplitPane();

                if (splitPane != null) {
                    if (splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT) {
                        insets.top = insets.bottom = 0;
                        insets.left = insets.right = 1;
                        return insets;
                    }
                    // VERTICAL_SPLIT
                    insets.top = insets.bottom = 1;
                    insets.left = insets.right = 0;
                    return insets;
                }
            }
        }
        insets.top = insets.bottom = insets.left = insets.right = 1;
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return true;
    }
}
