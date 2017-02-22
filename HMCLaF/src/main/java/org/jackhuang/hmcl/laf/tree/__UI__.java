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
package org.jackhuang.hmcl.laf.tree;

import java.awt.Color;

import javax.swing.UIManager;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.IconFactory;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {

    private static final IconFactory ICON = new IconFactory("tree");

    public static void uiImpl() {
        put("Tree.background", Color.white);
        put("Tree.textBackground", Color.white);
//      put("Tree.drawsFocusBorderAroundIcon", false);
        put("Tree.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        put("Tree.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("Tree.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("Tree.selectionBorderColor", BeautyEyeLNFHelper.commonFocusedBorderColor);//windows父类中默认是0,0,0

        UIManager.put("Tree.openIcon", ICON.get("open"));
        UIManager.put("Tree.closedIcon", ICON.get("closed"));
        UIManager.put("Tree.leafIcon", ICON.get("leaf"));
        UIManager.put("Tree.expandedIcon", ICON.get("expanded"));
        UIManager.put("Tree.collapsedIcon", ICON.get("collapsed"));

        //不绘制层次线
        put("Tree.paintLines", false);//default is true
        //行高
        put("Tree.rowHeight", 18);//default is 16
        //未选中时单元前景色（备选MacOSX黑 (35,35,35)）
        putColor("Tree.textForeground", 70, 70, 70);
        //处于编辑状态时的文本框边框，因BE LNF中文本框无边框（事实上它是用N9图实现的背景
        //边框视觉效果），所以此处要去掉，但加多点空白，与背景配合起来好看点
        putBorder("Tree.editorBorder", 1, 5, 1, 5);//Windows LNF中默认是LineBorderUIResource

        put("TreeUI", BETreeUI.class);
    }
}
