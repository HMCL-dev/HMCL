/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * FocusListenerImpl.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.textcoms;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;

import org.jackhuang.hmcl.laf.textcoms.__UI__.BgSwitchable;
import org.jackhuang.hmcl.laf.BEUtils;
import org.jackhuang.hmcl.laf.widget.border.BERoundBorder;

/**
 * 焦点改变时的监听器实现类.
 * <p>
 * 目前主要用于各文本组件.
 *
 * @author Jack Jiang(jb2011@163.com)
 */
public class FocusListenerImpl implements FocusListener, MouseListener {

    /**
     * 文本组件等获得焦点后的边框线条宽度.
     */
    public static int defaultFocusedThikness = 2;

    /**
     * Gets the single instance of FocusListenerImpl.
     *
     * @return single instance of FocusListenerImpl
     */
    public static FocusListenerImpl getInstance() {
//			return INSTANCE;
        return new FocusListenerImpl();
    }

    /**
     * The focused thikness.
     */
    protected int focusedThikness = defaultFocusedThikness;

    /**
     * Gets the focused thikness.
     *
     * @return the focused thikness
     */
    public int getFocusedThikness() {
        return focusedThikness;
    }

    /**
     * Sets the focused thikness.
     *
     * @param focusedThikness the focused thikness
     * @return the focus listener impl
     */
    public FocusListenerImpl setFocusedThikness(int focusedThikness) {
        this.focusedThikness = focusedThikness;
        return this;
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (e.getSource() instanceof JComponent) {
            JComponent com = (JComponent) e.getSource();
            Border orignalBorder = com.getBorder();

            if (orignalBorder != null) {
                //决定获得焦点时的连框色调
                Color focusedColor = getTextFieldFocusedColor();

                //JTextField获得焦点时特殊处理：自动切换它的背景图即可（用NinePatch图实现）
                if (com instanceof JTextComponent) {
                    JTextComponent text = (JTextComponent) com;
                    ComponentUI ui = text.getUI();
                    text.putClientProperty("BEFocused", true);
                    if (ui instanceof BgSwitchable) {
                        ((BgSwitchable) ui).switchBgToFocused();
                        com.repaint();
                        return;
                    }
                } else if (com instanceof JComboBox)
                    focusedColor = getComboBoxFocusedColor();

                //获得焦点后的新边框
                BERoundBorder cc;
                if (orignalBorder instanceof BERoundBorder)
                    cc = (BERoundBorder) (((BERoundBorder) orignalBorder).clone());
                else
                    cc = new BERoundBorder(1).setArcWidth(0);
                cc.setLineColor(focusedColor);
                cc.setThickness(focusedThikness);

                //* ！当组件是JPasswordField,它的反应会有bug,也就是在setBorder之后它的
                //* preferredSize会变的很小，这里针对其作的特殊处理就是为了使其size与setBorder前保持一致
                Dimension oldDm = null;
                if (com instanceof JTextField)
                    oldDm = com.getSize();
                com.setBorder(cc);
                if (com instanceof JTextField)
                    com.setPreferredSize(oldDm);
            }
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
        if (e.getSource() instanceof JComponent) {
            JComponent com = (JComponent) e.getSource();

            //JTextField获得焦点时特殊处理：自动切换它的背景图即可（用NinePatch图实现）
            if (com instanceof JTextComponent) {
                JTextComponent text = (JTextComponent) com;
                ComponentUI ui = text.getUI();
                text.putClientProperty("BEFocused", false);
                if (ui instanceof BgSwitchable) {
                    ((BgSwitchable) ui).switchBgToNormal();
                    com.repaint();
                    return;
                }
            }

            //失去焦点时还原边框样式
            Border orignalBorder = com.getBorder();
            if (orignalBorder != null)
                if (orignalBorder instanceof BERoundBorder) {
                    BERoundBorder cc = (BERoundBorder) (((BERoundBorder) orignalBorder).clone());
                    cc.setLineColor(BERoundBorder.defaultLineColor);
                    cc.setThickness(1);
                    com.setBorder(cc);
                }
        }
    }

    /**
     * Gets the text field focused color.
     *
     * @return the text field focused color
     */
    public static Color getTextFieldFocusedColor() {
        return BEUtils.getColor(UIManager.getColor("TextField.selectionBackground"), 30, 30, 30);
    }

    /**
     * Gets the combo box focused color.
     *
     * @return the combo box focused color
     */
    public static Color getComboBoxFocusedColor() {
        return BEUtils.getColor(UIManager.getColor("ComboBox.selectionBackground"), 30, 30, 30);
    }
//	public static Color getTextPaneFocusedColor()
//	{
//		return LNFUtils.getColor(UIManager.getColor("TextPane.selectionBackground"),30,30,30);
//	}

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (e.getSource() instanceof JComponent) {
            JComponent com = (JComponent) e.getSource();

            //JTextField获得焦点时特殊处理：自动切换它的背景图即可（用NinePatch图实现）
            if (com instanceof JTextComponent) {
                JTextComponent text = (JTextComponent) com;
                ComponentUI ui = text.getUI();
                Object o = text.getClientProperty("BEFocused");
                if (o == null || o == Boolean.FALSE)
                    if (ui instanceof BgSwitchable) {
                        ((BgSwitchable) ui).switchBgToOver();
                        com.repaint();
                        return;
                    }
            }

            //失去焦点时还原边框样式
            Border orignalBorder = com.getBorder();
            if (orignalBorder != null)
                if (orignalBorder instanceof BERoundBorder) {
                    BERoundBorder cc = (BERoundBorder) (((BERoundBorder) orignalBorder).clone());
                    cc.setLineColor(BERoundBorder.defaultLineColor);
                    cc.setThickness(1);
                    com.setBorder(cc);
                }
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (e.getSource() instanceof JComponent) {
            JComponent com = (JComponent) e.getSource();

            //JTextField获得焦点时特殊处理：自动切换它的背景图即可（用NinePatch图实现）
            if (com instanceof JTextComponent) {
                JTextComponent text = (JTextComponent) com;
                ComponentUI ui = text.getUI();
                Object o = text.getClientProperty("BEFocused");
                if (o == null || o == Boolean.FALSE)
                    if (ui instanceof BgSwitchable) {
                        ((BgSwitchable) ui).switchBgToNormal();
                        com.repaint();
                        return;
                    }
            }

            //失去焦点时还原边框样式
            Border orignalBorder = com.getBorder();
            if (orignalBorder != null)
                if (orignalBorder instanceof BERoundBorder) {
                    BERoundBorder cc = (BERoundBorder) (((BERoundBorder) orignalBorder).clone());
                    cc.setLineColor(BERoundBorder.defaultLineColor);
                    cc.setThickness(1);
                    com.setBorder(cc);
                }
        }
    }

}
