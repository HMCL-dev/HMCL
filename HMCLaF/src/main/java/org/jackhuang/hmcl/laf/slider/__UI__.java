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
package org.jackhuang.hmcl.laf.slider;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {

    public static void uiImpl() {
        put("Slider.background", BeautyEyeLNFHelper.commonBackgroundColor);
        //JSlider的刻度线绘制颜色
        putColor("Slider.tickColor", 154, 154, 154);
        put("Slider.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        //获得焦点时的insets
//	putInsets("Slider.focusInsets", 2, 2, 7, 7); //父类中默认是 2, 2, 2, 2
        //获得焦点时的焦点边框颜色
        put("Slider.focus", BeautyEyeLNFHelper.commonFocusedBorderColor); //[r=113,g=111,b=100]
        put("SliderUI", BESliderUI.class);
    }
}
