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
package org.jackhuang.hmcl.laf.button;

import java.awt.Insets;

import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicBorders.MarginBorder;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {

    public static void uiImpl() {
        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> JButton相关ui属性设定
        put("Button.background", BeautyEyeLNFHelper.commonBackgroundColor);
        //Button.foreground的设定不起效，这可能是LNF里的bug，因NLLookAndFeel
        //是继承自它们所以暂时无能为力，就这么的吧，以后再说
        put("Button.foreground", BeautyEyeLNFHelper.commonForegroundColor);

        //以下属性将决定按钮获得焦点时的焦点虚线框的绘制偏移量哦
        put("Button.dashedRectGapX", 3);
        put("Button.dashedRectGapY", 3);
        put("Button.dashedRectGapWidth", 6);
        put("Button.dashedRectGapHeight", 6);

        put("ButtonUI", BEButtonUI.class);
        putInsets("Button.margin", 2, 5, 2, 5);
        //此border可以与Button.margin连合使用，而者之和即查整个Button的内衬哦
        UIManager.put("Button.border", new BEButtonUI.BEEmptyBorder(new Insets(3, 3, 3, 3)));
        //获得焦点时的虚线框颜色
        putColor("Button.focus", 130, 130, 130);

        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> JToggleButton相关ui属性设定
        //注意：本属性不要与ToggleButton.border混用，因为没有它的优先级高，
        //另本参数如用InsetsUIResource则不会有效果，具体原因待查（本属性也将决定toolbar的整体高度和宽度哦）
        putInsets("ToggleButton.margin", 3, 11, 3, 11);//4, 8, 4, 8));////4, 12, 4, 12));
        put("ToggleButton.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("ToggleButton.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        //用于ToggleButon被选中时的前景色
        //注：在原WindowsLookAndFeel中，本属性存在（值是Color(0,0,0,)）但在UI里没有用到
        //，此处被jb2011定义为“选中时的前景色”，当然也可以自已定名称，参见 NLWindowsToggleButtonUI2.paintText(..)
        put("ToggleButton.focus", BeautyEyeLNFHelper.commonForegroundColor);// Color.white
        put("ToggleButtonUI", BEToggleButtonUI.class);
        //以下设置对ToggleButton在不加入到JToolBar时是有效果的哦！！！！！！！！！！！
//		UIManager.put("ToggleButton.margin",new InsetsUIResource(2, 30, 2, 30));
        put("ToggleButton.border", new MarginBorder());
        /* ~~注：这个属性是Jack Jiang为了更好的ui效果自已加的属性：焦点虚线的颜色 */
        put("ToggleButton.focusLine", BeautyEyeLNFHelper.commonFocusedBorderColor.darker());
        /* ~~注：这个属性是Jack Jiang为了更好的ui效果自已加的属性：焦点虚线的高亮立体阴影颜色 */
        putColor("ToggleButton.focusLineHilight", 240, 240, 240);
    }
}
