/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEToggleButtonUI.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.button;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;

import org.jackhuang.hmcl.laf.BEUtils;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;
import org.jackhuang.hmcl.laf.utils.MySwingUtilities2;

/**
 * JToggleButton的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @see com.sun.java.swing.plaf.windows.WindowsToggleButtonUI
 */
public class BEToggleButtonUI extends BasicToggleButtonUI {

    protected static final Icon9Factory ICON_9 = new Icon9Factory("toggle_button");

    private static final BEToggleButtonUI INSTANCE = new BEToggleButtonUI();

    public static ComponentUI createUI(JComponent b) {
        return INSTANCE;
    }

    //* 由Jack Jiang于2012-10-12日加入：重写本方法的目的是使得JToggleButton不填充
    //* BasicToggleButtonUI里的白色距形区，否则在JToolBar上时会因该白色距形区的存
    //* 在而使得与BEToolBarUI的渐变背景不协调（丑陋的创可贴效果），实际上
    //* WindowsToolButtonUI里有同名方法里也是它样处理的，详情参见WindowsToolButtonUI
    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        LookAndFeel.installProperty(b, "opaque", Boolean.FALSE);
    }

    // ********************************
    //         Paint Methods
    // ********************************
    @Override
    public void paint(Graphics g, JComponent c) {
        BEButtonUI.paintXPButtonBackground(g, c);
        super.paint(g, c);
    }

    //修改的目的是让它在获得焦点（或说点中时）改变前景色，可惜父类中没有实现它，只能自已来解决了
    /**
     * As of Java 2 platform v 1.4 this method should not be used or overriden.
     * Use the paintText method which takes the AbstractButton argument.
     *
     * @param g the g
     * @param c the c
     * @param textRect the text rect
     * @param text the text
     * @see
     * javax.swing.plaf.basic.BasicToggleButtonUI#paintText(java.awt.Graphics,
     * javax.swing.JComponent, java.awt.Rectangle, java.lang.String)
     */
    @Override
    protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
        AbstractButton b = (AbstractButton) c;
        ButtonModel model = b.getModel();
        FontMetrics fm = MySwingUtilities2.getFontMetrics(c, g);
        int mnemonicIndex = b.getDisplayedMnemonicIndex();

        if (model.isEnabled()) {
            if (model.isSelected())
                g.setColor(UIManager.getColor(getPropertyPrefix() + "focus"));
            else
                g.setColor(b.getForeground());

            MySwingUtilities2.drawStringUnderlineCharAt(c, g, text, mnemonicIndex,
                    textRect.x + getTextShiftOffset(),
                    textRect.y + fm.getAscent() + getTextShiftOffset());
        } else {
            g.setColor(b.getBackground().brighter());
            MySwingUtilities2.drawStringUnderlineCharAt(c, g, text, mnemonicIndex,
                    textRect.x, textRect.y + fm.getAscent());
            g.setColor(b.getBackground().darker());
            MySwingUtilities2.drawStringUnderlineCharAt(c, g, text, mnemonicIndex,
                    textRect.x - 1, textRect.y + fm.getAscent() - 1);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Method signature defined here overriden in subclasses. Perhaps this class
     * should be abstract?
     */
    @Override
    protected void paintFocus(Graphics g, AbstractButton b,
            Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
        Rectangle bound = b.getVisibleRect();
        //决定焦点要制的位置（当前实现是往内缩3个像素，与当前按钮背景配合）
        final int delta = 3;
        int x = bound.x + delta, y = bound.y + delta, w = bound.width - delta * 2, h = bound.height - delta * 2;

        //绘制焦点虚线框
        g.setColor(UIManager.getColor("ToggleButton.focusLine"));//*~ 这是Jack Jiang自定义的属性哦
        BEUtils.drawDashedRect(g, x, y, w, h, 17, 17, 2, 2);
        //再绘制焦点虚线框的立体高亮阴影，以便形成立体感
        g.setColor(UIManager.getColor("ToggleButton.focusLineHilight"));//*~ 这是Jack Jiang自定义的属性哦
        BEUtils.drawDashedRect(g, x + 1, y + 1, w, h, 17, 17, 2, 2);
    }
}
