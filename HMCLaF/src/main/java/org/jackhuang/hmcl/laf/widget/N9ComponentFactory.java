/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * N9ComponentFactory.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;

import org.jb2011.ninepatch4j.NinePatch;

/**
 * 使用NinePatch作为背景的一些可重用组件工厂类.
 * <p>
 * 目前这些组件主要用于SwingSets2的演示代码，以便改造官方的SwingSets2使 得它们的美感与BeautyEye形成较好的搭配。
 *
 * @author Jack Jiang(jb2011@163.com)
 */
public class N9ComponentFactory extends JLabel {

    public static final Icon9Factory ICON_9 = new Icon9Factory("widget");

    /**
     * Creates a new N9Component object.
     *
     * @param text the text
     * @param n9 the n9
     * @param is the is
     * @param foregroundColor the foreground color
     * @param f the f
     * @return the j label
     */
    public static JLabel createLabel_root(String text,
             final NinePatch n9, Insets is,
             Color foregroundColor, Font f) {
        JLabel l = new JLabel(text) {
            @Override
            public void paintComponent(Graphics g) {
                n9.draw((Graphics2D) g, 0, 0, this.getWidth(), this.getHeight());
                super.paintComponent(g);
            }
        };
        if (is != null)
            l.setBorder(BorderFactory.createEmptyBorder(is.top, is.left, is.bottom, is.right));
        if (foregroundColor != null)
            l.setForeground(foregroundColor);
        if (f != null)
            l.setFont(f);

        return l;
    }

    /**
     * Creates a new N9Component object.
     *
     * @param txt the txt
     * @return the j label
     */
    public static JLabel createLabel_style1(String txt) {
        return createLabel_root(txt, ICON_9.get("hint_light_blue"),
                 new Insets(1, 6, 1, 6), Color.white, new Font("宋体", Font.BOLD, 12));
    }

    /**
     * Creates a new N9Component object.
     *
     * @param txt the txt
     * @return the j label
     */
    public static JLabel createLabel_style2(String txt) {
        return createLabel_root(txt, ICON_9.get("tips"),
                 new Insets(15, 3, 28, 3), new Color(139, 119, 75),
                 null);
    }

    /**
     * Creates a new N9Component object.
     *
     * @param txt the txt
     * @return the j label
     */
    public static JLabel createLabel_style3(String txt) {
        return createLabel_root(txt, ICON_9.get("orange_balloon"),
                 new Insets(4, 9, 9, 9)//3, 9, 8, 9)
                ,
                 new Color(255, 255, 255),
                 null);
    }

    /**
     * Creates a new N9Component object.
     *
     * @param txt the txt
     * @return the j label
     */
    public static JLabel createLabel_style4(String txt) {
        return createLabel_root(txt, ICON_9.get("hint_light_gray"),
                 new Insets(2, 8, 2, 8), Color.white, new Font("宋体", Font.PLAIN, 12));
    }

    /**
     * Creates a new N9Component object.
     *
     * @return the image bg panel
     */
    public static ImageBgPanel createPanel_style1() {
        return createPanel_style1(new Insets(8, 0, 26, 10));
    }

    /**
     * Creates a new N9Component object.
     *
     * @param is the is
     * @return the image bg panel
     */
    public static ImageBgPanel createPanel_style1(Insets is) {
        ImageBgPanel p = new ImageBgPanel().setN9(ICON_9.get("panel"));
        if (is != null)
            p.setBorder(BorderFactory.createEmptyBorder(is.top, is.left, is.bottom, is.right));
        return p;
    }

}
