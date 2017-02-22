/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * PlainGrayBorder.java at 2015-2-1 20:25:38, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.widget.border;

import java.awt.Insets;
import org.jackhuang.hmcl.laf.widget.N9ComponentFactory;

/**
 * 一个NinePatch图实现的不透明边框border.
 * <p>
 * 目前主要用于jdk1.5及以下版本的窗口边框（因为该版本下java不支持窗口透明）.
 *
 * @author Jack Jiang(jb2011@163.com), 2012-09-04
 * @version 1.0
 * @see
 * org.jb2011.lnf.beautyeye.BeautyEyeLNFHelper.FrameBorderStyle#generalNoTranslucencyShadow
 */
public class PlainGrayBorder extends NinePatchBorder {

    private final static int IS = 5;

    public PlainGrayBorder() {
        super(new Insets(IS, IS, IS, IS),
                N9ComponentFactory.ICON_9.get("border_plain_gray"));
    }

    //* 2012-09-19 在BeautyEye v3.2中的BERootPaneUI，Jack Jiang启用了相比
    //* 原MetalRootPaneUI中更精确更好的边框拖放算法，以下方法暂时弃用，以后可以删除了！
//	//当用本border作边框时，窗口可拖动敏感触点区大小值
//	public static int BORDER_DRAG_THICKNESS()
//	{
//		return IS;
//	}
//	//当用本border作边框时，窗口边角可拖动敏感触点区大小值
//	public static int CORNER_DRAG_WIDTH()
//	{
//		return 16;//使用MetalLookAndFeel的默认值比较合适哦
//	}
}
