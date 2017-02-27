/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * WindowTranslucencyHelper.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.utils;

import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

/**
 * 关于java支持窗口透明的详细信息请见：http://docs.oracle.com/javase/tutorial/uiswing/misc/trans_shaped_windows.html#uniform
 */
public class WindowTranslucencyHelper {

    /**
     * Checks if is translucency supported.
     *
     * @return true, if is translucency supported
     */
    public static boolean isTranslucencySupported() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
    }

    /**
     * Sets the window opaque.
     *
     * @param w the w
     * @param opaque the opaque
     */
    public static void setWindowOpaque(Window w, boolean opaque) {
        Color bgc = w.getBackground();
        /*
         * 在群友机器上（win7+java1.7.0.1）的生产系统下
         * 下使用BeautyEye有时w.getBackground()返回值是null，但为什么返回是null，Jack 没
         * 有测出来（Jack测试都是正常的），暂且认为是其系统代码有问题吧，在此容错一下
         */
        if (bgc == null)
            bgc = Color.black;//暂不知道用此黑色作为容错值合不合适
        Color newBgn = new Color(bgc.getRed(), bgc.getGreen(), bgc.getBlue(), opaque ? 255 : 0);
        w.setBackground(newBgn);
    }

}
