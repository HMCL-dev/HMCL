/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BESpinnerUI.java at 2015-2-1 20:25:39, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.spinner;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSpinnerUI;

import org.jackhuang.hmcl.laf.spinner.BESpinnerUI.GlyphButton.Type;
import org.jackhuang.hmcl.laf.BEUtils;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;

/**
 * JSPinner的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @version 1.0
 * @see com.sun.java.swing.plaf.windows.WindowsSpinnerUI
 */
public class BESpinnerUI extends BasicSpinnerUI {

    private static final Icon9Factory ICON_9 = new Icon9Factory("spinner");

    public static ComponentUI createUI(JComponent c) {
        return new BESpinnerUI();
    }

    @Override
    protected JComponent createEditor() {
        JComponent e = super.createEditor();
        e.setOpaque(false);

        //参见JSpinner.NumberEditor，super.createEditor()返回值就是它的父类
        //（是一个JPanel实例），它是由一个FormatttedTextField及其父JPanel组成
        //的，所以设置完 e.setOpaque(false)，还要把它的子FormatttedTextField
        //设置成透明，其实它的子只有1个，它里为了适用未来的扩展假设它有很多子，
        Component[] childs = e.getComponents();
        BEUtils.componentsOpaque(childs, false);

        return e;
    }

    /**
     * Paint.
     *
     * @param g the g
     * @param c the c {@inheritDoc}
     */
    @Override
    public void paint(Graphics g, JComponent c) {
        if (spinner != null)
            ICON_9.getWithEnabled("", spinner.isEnabled()).
                    draw((Graphics2D) g, 0, 0, c.getWidth(), c.getHeight());
        super.paint(g, c);
    }

    @Override
    protected Component createPreviousButton() {
        JButton xpButton = new GlyphButton(spinner, Type.down);
        Dimension size = UIManager.getDimension("Spinner.arrowButtonSize");
        xpButton.setPreferredSize(size);
        xpButton.setRequestFocusEnabled(false);
        installPreviousButtonListeners(xpButton);
        return xpButton;
    }

    @Override
    protected Component createNextButton() {
        JButton xpButton = new GlyphButton(spinner, Type.up);
        Dimension size = UIManager.getDimension("Spinner.arrowButtonSize");
        xpButton.setPreferredSize(size);
        xpButton.setRequestFocusEnabled(false);
        installNextButtonListeners(xpButton);
        return xpButton;
    }

    /**
     * @see com.sun.java.swing.plaf.windows.XPStyle.GlyphButton
     */
    static class GlyphButton extends JButton {

        private Type type = null;

        /**
         * The Enum Type.
         */
        public enum Type {
            down,
            up
        }

        /**
         * Instantiates a new glyph button.
         *
         * @param parent the parent
         * @param type the type
         */
        public GlyphButton(Component parent, Type type) {
//			XPStyle xp = getXP();
//			skin = xp.getSkin(parent, part);
            this.type = type;
            setBorder(null);
            setContentAreaFilled(false);
            setMinimumSize(new Dimension(5, 5));
            setPreferredSize(new Dimension(16, 16));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        }

        @Override
        public boolean isFocusTraversable() {
            return false;
        }

        @Override
        public void paintComponent(Graphics g) {
            ICON_9.get("button_" + type.name(), !isEnabled() || getModel().isPressed() ? "pressed" : "").
                    draw((Graphics2D) g, 0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintBorder(Graphics g) {
        }
    }
}
