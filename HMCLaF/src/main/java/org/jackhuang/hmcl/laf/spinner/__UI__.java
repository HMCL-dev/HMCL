/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * __UI__.java at 2015-2-1 20:25:37, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.spinner;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {

    public static void uiImpl() {
        put("Spinner.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("Spinner.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("SpinnerUI", BESpinnerUI.class);

        //Spinner组件的边框
        putBorder("Spinner.border", 5, 5, 10, 5); // 3, 3, 3, 3
        //Spinner组件的2个箭头按钮的内衬距
        putInsets("Spinner.arrowButtonInsets", 1, 0, 2, 2); // 1, 1, 1, 1
        //Spinner组件的2个箭头按钮的默认大小
        putDim("Spinner.arrowButtonSize", 17, 9);
    }
}
