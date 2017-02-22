/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEShadowBorder.java at 2015-2-1 20:25:39, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.widget.border;

import java.awt.Insets;
import org.jackhuang.hmcl.laf.widget.N9ComponentFactory;

/**
 * 一个用9格图实现的边框阴影效果，目前用于内部窗口的边框（阴影效果是半透明的）.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @see
 * org.jb2011.lnf.beautyeye.BeautyEyeLNFHelper.FrameBorderStyle#translucencySmallShadow
 */
public class BEShadowBorder extends NinePatchBorder {

    private final static int TOP = 5, LEFT = 5, RIGHT = 5, BOTTOM = 6;

    public BEShadowBorder() {
        super(new Insets(TOP, LEFT, BOTTOM, RIGHT),
                N9ComponentFactory.ICON_9.get("border_shadow1"));
    }

    //* 2012-09-19 在BeautyEye v3.2中的BERootPaneUI，Jack Jiang启用了相比
    //* 原MetalRootPaneUI中更精确更好的边框拖放算法，以下方法暂时弃用，以后可以删除了！
//	//当用本border作边框时，窗口可拖动敏感触点区大小值
//	public static int BORDER_DRAG_THICKNESS()
//	{
//		return Math.min(Math.min(Math.min(TOP, LEFT),RIGHT),BOTTOM);
//	}
//	
//	//当用本border作边框时，窗口边角可拖动敏感触点区大小值
//	public static int CORNER_DRAG_WIDTH()
//	{
//		return Math.max(Math.max(Math.max(TOP, LEFT),RIGHT),BOTTOM);
//	}
}
