/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * __UI__.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.scroll;

import java.awt.Color;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {

    public static void uiImpl() {
        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> 视口的相关ui值设定
        put("Viewport.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("Viewport.foreground", BeautyEyeLNFHelper.commonForegroundColor);

        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> JScrollPane的相关ui值设定
        put("ScrollPane.border", new ScrollPaneBorder()); // 0, 0, 0, 0
        // 不能设置alpha通道小于255的透明颜色，否则会出现无法重paint的问题
        put("ScrollPane.background", Color.white);//cc));
        put("ScrollPane.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("ScrollPaneUI", BEScrollPaneUI.class);

        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> JScrollPane的滚动条相关ui值设定
        put("ScrollBar.thumb", BeautyEyeLNFHelper.commonBackgroundColor);
        put("ScrollBar.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        putColor("ScrollBar.background", 250, 250, 250);
        putColor("ScrollBar.trackForeground", 250, 250, 250);
        putColor("scrollbar", 250, 250, 250);
        put("ScrollBarUI", BEScrollBarUI.class);

//	/* ~~注：这个属性是jb2011自已加的，目的是控制滚动面板及其Viewport的透明性 */
//	//设置成透明是为了让BE LNF中它的N9图实现的border能展现出图片背景来，好看一点
//	put("ScrollPane.opaque", false);
    }
}
