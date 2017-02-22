/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * __UI__.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.list;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {

    public static void uiImpl() {
        //通过此border可以调整整个列表的上下左右的空白
        putBorder("List.border", 0, 0, 0, 0);
        //本属性将决定已被选中的列表单元显示文本的上下左右空白哦
        putBorder("List.focusCellHighlightBorder", 1, 8, 5, 3);
        /* ~~注：这个属性是jb2011为了更好的ui效果自已加的属性 */
        putColor("List.focusSelectedCellHighlightBorderColor", 252, 87, 84); //Color.red
        /* ~~注：这个属性是jb2011为了更好的ui效果自已加的属性，目的是使List.focusSelectedCellHighlightBorderColor有点立体效果哦 */
        putColor("List.focusSelectedCellHighlightBorderHighlightColor", 255, 255, 255, 70);//注意：这个颜色是半透明的哦
        //列表单元获得焦点时的边框（windows主题下是一个距形虚线框）——之前老也想不通的与左边隔一个像素的白边问题就是没设置它造成的
        put("List.focusSelectedCellHighlightBorder", new FocusSelectedCellHighlightBorder());
        //将决定正状态下的列表单元显示文本的上下左右空白哦
        putBorder("List.cellNoFocusBorder", 1, 8, 5, 3);

        put("List.background", Color.white);
        put("List.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("List.selectionForeground", Color.white);//fgColor);
        put("List.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);

        UIManager.put("List.cellRenderer", new MyDefaultListCellRenderer.UIResource());
        put("ListUI", BEListUI.class);
    }
    
    /**
     * 本border由Jack Jiang实现，它是列表单元获得焦点时的边框（windows外观
     * 下是一个距形虚线框）,本border当前的样式是在在最左边画的一条红色竖线
     */
    static class FocusSelectedCellHighlightBorder extends AbstractBorder {

        @Override
        public Insets getBorderInsets(Component c) {
            //Insets跟List.focusCellHighlightBorder 一样即可，这样就能在视觉上保持一致罗（不一样则会有移位的难看效果）！
            return UIManager.getBorder("List.focusCellHighlightBorder").getBorderInsets(c);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            return getBorderInsets(c);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            //在左边划一条2像素宽的竖线
            g.setColor(UIManager.getColor("List.focusSelectedCellHighlightBorderColor"));
            g.fillRect(x, y + 0, 2, height - 1);//-2);//上下各空白一个像素，目的是为了与render的N9图片背景配合形成更好的视觉效果

            //再在上面的竖线右边划一条1像素宽的亮色竖线，以便为上面的2像素竖线营造立体效果
            /* ~~注：这个属性是jb2011为了更好的ui效果自已加的属性，目的是使List.focusSelectedCellHighlightBorder有点立体效果哦 */
            g.setColor(UIManager.getColor("List.focusSelectedCellHighlightBorderHighlightColor"));
            g.fillRect(x + 2, y + 0, 1, height - 1);//-2);
        }
    }
}
