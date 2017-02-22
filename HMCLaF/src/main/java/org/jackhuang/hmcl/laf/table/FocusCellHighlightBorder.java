/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * FocusCellHighlightBorder.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.table;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;

import org.jackhuang.hmcl.laf.BEUtils;

/**
 * 表格单元获得焦点时的Border实现类.
 * 
 * 本border由Jack Jiang实现，它是表格单元获得焦点时的边框（类似的功能在windows LNF下是一个距形虚线框）
 *
 * @author Jack Jiang(jb2011@163.com)
 */
class FocusCellHighlightBorder extends AbstractBorder {

    @Override
    public Insets getBorderInsets(Component c) {
//		return new Insets(0,3,0,1);
        return new Insets(2, 2, 2, 2); // @since 3.5
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        return getBorderInsets(c);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        //* old impl
//		//在左边划一条2像素宽的竖线
//		g.setColor(UIManager.getColor("Table.focusCellHighlightBorderColor"));
//		g.fillRect(x, y, 2, height );//上下各空白一个像素，目的是为了与render的N9图片背景配合形成更好的视觉效果
//		
//		//再在上面的竖线右边划一条1像素宽的亮色竖线，以便为上面的2像素竖线营造立体效果
//		/* ~~注：这个属性是jb2011为了更好的ui效果自已加的属性，目的是使Table.focusCellHighlightBorder有点立体效果哦 */
//		g.setColor(UIManager.getColor("Table.focusCellHighlightBorderHighlightColor"));
//		g.fillRect(x+2, y, 1, height );

        //* @since 3.5
        BEUtils.draw4RecCorner(g, x, y, width - 2, height - 2, 5,
                 UIManager.getColor("Table.focusCellHighlightBorderColor"));
        BEUtils.draw4RecCorner(g, x + 1, y + 1, width - 2, height - 2, 5,
                 UIManager.getColor("Table.focusCellHighlightBorderHighlightColor"));
    }
}
