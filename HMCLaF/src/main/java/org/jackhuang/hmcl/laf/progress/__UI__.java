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
package org.jackhuang.hmcl.laf.progress;

import javax.swing.BorderFactory;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {

    public static void uiImpl() {
        put("ProgressBar.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("ProgressBar.selectionForeground", BeautyEyeLNFHelper.commonBackgroundColor);

        // 此属性决定水平进度条的默认最小大小：15是相关于.9.png图片的最小填充
        // 高度或长度的(小于此高度则NinePatch算法无法解决而很难看)
        putDim("ProgressBar.horizontalSize", 146, 15); // 默认是146,12
        putDim("ProgressBar.verticalSize", 15, 146); // 默认是12,146
        put("ProgressBar.border", BorderFactory.createEmptyBorder(0, 0, 0, 0));
        put("ProgressBarUI", BEProgressBarUI.class);
    }
}
