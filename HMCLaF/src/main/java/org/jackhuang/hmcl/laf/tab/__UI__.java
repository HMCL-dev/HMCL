/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * __UI__.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.tab;

import java.awt.Font;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {

    public static void uiImpl() {
        FontUIResource f = (FontUIResource) UIManager.get("TabbedPane.font");
        UIManager.put("TabbedPane.font", new FontUIResource(new Font(f.getName(), f.getStyle(), 14)));
        put("TabbedPane.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("TabbedPane.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("TabbedPane.opaque", false);
//	put("TabbedPane.tabRunOverlay", 2);//本属性无效果
        //false表示tab在边框虑线之上而不是重叠效果
        put("TabbedPane.tabsOverlapBorder", true);
        put("TabbedPaneUI", BETabbedPaneUI.class);
        //此属性决定了整个JTabbedPane区域的内衬
        putInsets("TabbedPane.tabAreaInsets", 3, 20, 2, 20);// 3, 2, 2, 2
        //此属性决定了tab与内容面板间的空白
        putInsets("TabbedPane.contentBorderInsets", 2, 0, 3, 0);// 2, 2, 3, 3
        //此参数将决定选中时的tab与左右相邻tab的重合度，正值表示重合、负值表示间隔（空白）
        //* 注意与NP图的边缘留白配合使用能达到更灵活的效果
        putInsets("TabbedPane.selectedTabPadInsets", 0, 1, 0, 2);// 2, 2, 2, 1
        //此属性决定了JTabbedPane的tab标签的内衬
        putInsets("TabbedPane.tabInsets", 7, 7, 7, 7);
        //获得焦点时的虚线框颜色
        putColor("TabbedPane.focus", 130, 130, 130);
        //在BE LNF中，此颜色将决定TabPlacement=TOP和LEFT二种类型TabbedPane的内容面板那条虚线的颜色
        putColor("TabbedPane.highlight", 228, 228, 231);//new Color(200,200,200)));
        //在BE LNF中，此颜色将决定TabPlacement=RIGHT和BOTTOM二种类型TabbedPane的内容面板那条虚线的颜色
        put("TabbedPane.shadow", BeautyEyeLNFHelper.commonFocusedBorderColor);//192,192,192);
        //在BE LNF中，因TabPlacement=RIGHT和BOTTOM二种类型时，父类方法会默认再多画一条深色立体线而使得在BE LNF中
        //不好看，此颜色设置的目的就是让此立体阴影线与背景色一致从而看不出它的效果，进而不影响内容面板那条虚线在BE LNF中的视觉效果
        put("TabbedPane.darkShadow", BeautyEyeLNFHelper.commonBackgroundColor);
    }
}
