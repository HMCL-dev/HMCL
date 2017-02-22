/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * TableScrollBorder.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.table;

import java.awt.Insets;

import org.jackhuang.hmcl.laf.widget.border.NinePatchBorder;

/**
 * 表格UI所在滚动条的边框实现类.
 *
 * @author Jack Jiang(jb2011@163.com), 2012-08-30
 * @version 1.0
 */
class TableScrollBorder extends NinePatchBorder {

    public TableScrollBorder() {
        super(new Insets(3, 5, 10, 5), BETableHeaderUI.ICON_9.get("scroll_border"));
    }
}
