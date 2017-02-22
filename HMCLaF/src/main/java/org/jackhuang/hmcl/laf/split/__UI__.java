/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * __UI__.java at 2015-2-1 20:25:39, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.split;

import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {

    public static void uiImpl() {
        putColor("SplitPane.shadow", 200, 200, 200); // 本属性在BE LNF中暂时没用到
        //JSplitePane的默认背景色
        putColor("SplitPane.background", 250, 250, 250); // 238, 241, 243
        //JSplitePane的边框实现
        put("SplitPane.border", new org.jackhuang.hmcl.laf.scroll.ScrollPaneBorder());// 0, 0, 0, 0
        put("SplitPaneUI", BESplitPaneUI.class);

        //分隔条拖动时的颜色（说明：此值可以设置alpha通道以便达到半透明效果哦）
        putColor("SplitPaneDivider.draggingColor", 0, 0, 0, 50);
        //触碰按钮的默认大小
        // see javax.swing.plaf.basic.BasicSplitPaneDivider.ONE_TOUCH_SIZE = 6
        put("SplitPane.oneTouchButtonSize", 4);
        //分隔条的默认大小
        put("SplitPane.dividerSize", 7); //drfault is 5
        //分隔条的边框实现
        put("SplitPaneDivider.border", new SplitPaneDividerBorder());
    }
}
