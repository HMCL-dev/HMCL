/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BETableUI.java at 2015-2-1 20:25:41, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.table;

import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTableUI;

/**
 * JTable的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 */
public class BETableUI extends BasicTableUI {

    UIDefaults defaultRenderersByColumnClass;

    public static ComponentUI createUI(JComponent c) {
        return new BETableUI();
    }

    /**
     * Initialize JTable properties, e.g. font, foreground, and background. The
     * font, foreground, and background properties are only set if their current
     * value is either null or a UIResource, other properties are set if the
     * current value is null.
     *
     * @see #installUI
     */
    @Override
    protected void installDefaults() {
        super.installDefaults();
        //行高设置为25看起来会舒服些
        table.setRowHeight(25);
        //不显示垂直的网格线
        table.setShowVerticalLines(false);
        //设置单元格间的空白（默认是1个像素宽和高）
        //说明：设置本参数可以实现单元格间的间隔空制，间隔里通常是实现网格线的绘制，但
        //网格维绘制与否并不影响间隔的存在（如果间隔存在但网格线不绘的话它就是是透明的空
        //间），参数中width表示水平间隔，height表示垂直间隔，为0则表示没有间隔
        table.setIntercellSpacing(new Dimension(0, 1));
        LookAndFeel.installProperty(table, "opaque", Boolean.FALSE);
    }
}
