/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BETextPaneUI.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.textcoms;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTextPaneUI;
import javax.swing.text.JTextComponent;

import org.jackhuang.hmcl.laf.textcoms.__UI__.BgSwitchable;

/**
 * 文本组件JTextPane的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com), 2012-08-25
 * @see com.sun.java.swing.plaf.windows.WindowsTextPaneUI
 */
public class BETextPaneUI extends BasicTextPaneUI implements BgSwitchable,
        org.jackhuang.hmcl.laf.BeautyEyeLNFHelper.__UseParentPaintSurported {
    //默认是纯白色背景，因为JTextPane肯定是要放在JScrollPane中的，而ScrollPane也是有边框的
    //如果JTextPane再有边框就很难看了，所以JTextPane在没有获得焦点时就已无边框效果出现会好看很多

    public static ComponentUI createUI(JComponent c) {
        BETextFieldUI.addOtherListener(c);
//    	c.addMouseListener(new NLLookAndFeel.EditMenu());
        return new BETextPaneUI();
    }

    /**
     * 是否使用父类的绘制实现方法，true表示是.
     * <p>
     * 因为在BE LNF中，边框和背景等都是使用N9图，没法通过设置背景色和前景
     * 色来控制JTextPane的颜色和边框，本方法的目的就是当用户设置了进度条的border或背景色 时告之本实现类不使用BE
     * LNF中默认的N9图填充绘制而改用父类中的方法（父类中的方法 就可以支持颜色的设置罗，只是丑点，但总归是能适应用户的需求场景要求，其实用户完全可以
     * 通过JTextPane.setUI(..)方式来自定义UI哦）.
     *
     * @return true, if is use parent paint
     */
    @Override
    public boolean isUseParentPaint() {
        return getComponent() != null
                && (!(getComponent().getBorder() instanceof UIResource)
                || !(getComponent().getBackground() instanceof UIResource));
    }

    @Override
    public void switchBgToNormal() {
    }

    @Override
    public void switchBgToFocused() {
    }

    @Override
    public void switchBgToOver() {
    }

}
