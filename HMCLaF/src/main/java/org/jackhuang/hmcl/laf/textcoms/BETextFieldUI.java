/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BETextFieldUI.java at 2015-2-1 20:25:37, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.textcoms;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JComponent;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.JTextComponent;

import org.jackhuang.hmcl.laf.textcoms.__UI__.BgSwitchable;
import org.jb2011.ninepatch4j.NinePatch;

/**
 * 文本组件JTextField的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 */
public class BETextFieldUI extends BasicTextFieldUI implements BgSwitchable,
        org.jackhuang.hmcl.laf.BeautyEyeLNFHelper.__UseParentPaintSurported {

    private NinePatch bg = __UI__.ICON_9.get("normal");

    public static BETextFieldUI createUI(JComponent c) {
        addOtherListener(c);
        return new BETextFieldUI();
    }

    //* 本方法由Jack Jiang于2012-09-07日加入
    /**
     * 是否使用父类的绘制实现方法，true表示是.
     * <p>
     * 因为在BE LNF中，边框和背景等都是使用N9图，没法通过设置背景色和前景
     * 色来控制JTextField的颜色和边框，本方法的目的就是当用户设置了进度条的border或背景色 时告之本实现类不使用BE
     * LNF中默认的N9图填充绘制而改用父类中的方法（父类中的方法 就可以支持颜色的设置罗，只是丑点，但总归是能适应用户的需求场景要求，其实用户完全可以
     * 通过JTextField.setUI(..)方式来自定义UI哦）.
     *
     * @return true, if is use parent paint
     */
    @Override
    public boolean isUseParentPaint() {
        return getComponent() != null
                && (!(getComponent().getBorder() instanceof UIResource)
                || !(getComponent().getBackground() instanceof UIResource));
    }

    /**
     * Paints a background for the view. This will only be called if isOpaque()
     * on the associated component is true. The default is to paint the
     * background color of the component.
     *
     * @param g the graphics context
     */
    @Override
    protected void paintBackground(Graphics g) {
//    	Color bgc = editor.getBackground();
//        g.setColor(bgc);
//        //先填 充背景
//        g.fillRect(0, 0, editor.getWidth(), editor.getHeight());
//        
//        //(1) ---- 仿Numbus文本框效果
//        //** top立体效果实现
//        //第(0,0)开始的第一条线会被后来的border覆盖掉的，所以此处绘制没有意义，不搞了
////        g.setColor(new Color(0,0,0));
////        g.drawLine(0, 0, editor.getWidth(), 0);
//        //第2条线颜色淡一点
//        g.setColor(new Color(208,208,208));
//        g.drawLine(0, 1, editor.getWidth(), 1);
//        //第3条线颜色更淡一点
//        g.setColor(new Color(231,231,225));
//        g.drawLine(0, 2, editor.getWidth(), 2);

        //先调用父类方法把背景刷新下（比如本UI里使用的大圆角NP图如不先刷新背景则会因上下拉动滚动条
        //而致4个圆角位置得不到刷新，从而影响视觉效果（边角有前面的遗留），置于透明边角不被透明像素填
        //充的问题，它有可能是Android的NinePatch技术为了性能做作出的优化——一切全透明像素即意味着不需绘制）
        super.paintBackground(g);// TODO 出于节约计算资源考生虑，本行代码换成父类中默认填充背景的代码即可

        //* 如果用户作了自定义颜色设置则使用父类方法来实现绘制，否则BE LNF中没法支持这些设置哦
        if (!isUseParentPaint()) {
            //用新的NP图实现真正的背景填充
            JTextComponent editor = this.getComponent();
            BETextFieldUI.paintBg(g, 0, 0, editor.getWidth(), editor.getHeight(),
                    editor.isEnabled(), border);
        }

//    	if(this.getComponent().isEnabled())
//	    	//*** 重要说明：因使用的NinePatch图片作填充背景，所以后绪任何对JTextField设置
//	    	//*** 背景色将不会起效，因为背景是用图片填充而非传统方法绘制出来的
//	    	bg.draw((Graphics2D)g, 0, 0, editor.getWidth(), editor.getHeight());
//    	else
//    		__Icon9Factory__.getInstance().getTextFieldBgDisabled()
//    			.draw((Graphics2D)g, 0, 0, editor.getWidth(), editor.getHeight());
////      //(2) ---- 仿360软件管家文本框效果（不太好看）
//        //** top立体效果实现
//        //第(0,0)开始的第一条线会被后来的border覆盖掉的，所以此处绘制没有意义，不搞了
////        g.setColor(new Color(0,0,0));
////        g.drawLine(0, 0, editor.getWidth(), 0);
//        //第2条线颜色淡一点
//        g.setColor(new Color(232,232,232));
//        g.drawLine(1, 1, editor.getWidth()-1, 1);
//        //第3条线颜色更淡一点
//        g.setColor(new Color(241,241,241));
//        g.drawLine(1, 2, editor.getWidth()-1, 2);
//        //第4条线颜色更淡一点
//        g.setColor(new Color(248,248,248));
//        g.drawLine(1, 3, editor.getWidth()-1, 3);
//        //第5条线颜色更淡一点
//        g.setColor(new Color(252,252,252));
//        g.drawLine(1, 4, editor.getWidth()-1, 4);
//        
//        //** left
//        //第2条线颜色淡一点
//        g.setColor(new Color(241,241,241));
//        g.drawLine(1, 1, 1, editor.getHeight()-1);
//        //第3条线颜色淡一点
//        g.setColor(new Color(248,248,248));
//        g.drawLine(2, 1, 2, editor.getHeight()-1);
//        //第4条线颜色淡一点
//        g.setColor(new Color(253,253,253));
//        g.drawLine(3, 1, 3, editor.getHeight()-1);
//        
//        //** right
//        //第2条线颜色淡一点
//        g.setColor(new Color(241,241,241));
//        g.drawLine(editor.getWidth()-1, 1, editor.getWidth()-1, editor.getHeight()-1);
//        //第3条线颜色淡一点
//        g.setColor(new Color(248,248,248));
//        g.drawLine(editor.getWidth()-2, 1, editor.getWidth()-2, editor.getHeight()-1);
//        //第4条线颜色淡一点
//        g.setColor(new Color(253,253,253));
//        g.drawLine(editor.getWidth()-3, 1, editor.getWidth()-3, editor.getHeight()-1);
//        
//        //** bottom
//        //第2条线颜色淡一点
//        g.setColor(new Color(248,248,248));
//        g.drawLine(1, editor.getHeight()-1, editor.getWidth()-1, editor.getHeight()-1);
//        //第3条线颜色淡一点
//        g.setColor(new Color(252,252,252));
//        g.drawLine(1, editor.getHeight()-2, editor.getWidth()-1, editor.getHeight()-2);
    }

    @Override
    public void switchBgToNormal() {
        border = __UI__.BORDER_NORMAL;
    }

    @Override
    public void switchBgToFocused() {
        border = __UI__.border_focused();
    }

    @Override
    public void switchBgToOver() {
        border = __UI__.BORDER_OVER;
    }
    
    Color border = __UI__.BORDER_NORMAL;

    /**
     * Paint bg.
     *
     * @param g the g
     * @param x the x
     * @param y the y
     * @param w the w
     * @param h the h
     * @param enabled the enabled
     * @param bg the bg
     */
    public static void paintBg(Graphics g, int x, int y, int w, int h,
            boolean enabled, Color border) {
        if (enabled) {
            g.setColor(border);
            g.fillRect(x, y, w, h);
            g.setColor(Color.white);
            g.fillRect(x + 2, y + 2, w - 4, h - 4);
        }
        else
            __UI__.ICON_9.get("disabled")
                    .draw((Graphics2D) g, x, y, w, h);
    }

    /**
     * 为组件添加焦点监听器（获得/取消焦点时可以自动设置/取消一个彩色的边框效果，以体高UI体验） 、右键菜单监听器（有复制/粘贴等功能）.
     *
     * @param c the c
     */
    public static void addOtherListener(JComponent c) {
        c.addFocusListener(FocusListenerImpl.getInstance());
    	c.addMouseListener(FocusListenerImpl.getInstance());
    }

}
