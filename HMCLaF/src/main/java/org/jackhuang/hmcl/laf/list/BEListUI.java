/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEListUI.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.list;

import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicListUI;

/**
 * JList的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @version 1.0
 */
public class BEListUI extends BasicListUI {

    public static ComponentUI createUI(JComponent c) {
        return new BEListUI();
    }

    /**
     * Initialize JList properties, e.g. font, foreground, and background, and
     * add the CellRendererPane. The font, foreground, and background properties
     * are only set if their current value is either null or a UIResource, other
     * properties are set if the current value is null.
     *
     * @see #uninstallDefaults
     * @see #installUI
     * @see CellRendererPane
     */
    @Override
    protected void installDefaults() {
        super.installDefaults();

        //2012-08-30*******************************************************【重要说明】 START 对应BEComboBoxUI中的【重要说明】
        //* 【重要说明】因BEListUI中为了使列表行单元高变的更高（在MyDefaultListCellRenderer.java中
        //* 像COmboxRender一样通过增到border不起效果，它可能是BasicListUI的设计缺陷，它要么取FixedCellHeight
        //* 固定值，要么取getPreferSize()即自动计算高度——它似乎是不计入border的，所以render设置border不起效）
        //* 所以只能为列表单元设置因定值：list.setFixedCellHeight(30)，但它将影响Combox里的行高（也会变成30高）
        //* 所以BEComboBoxUI中要把本列表UI中强制设定的30高针对Combox还原成自动计算（API中规定FixedCellHeight==-1即表示自动计算）
        //*************************************************************** 【重要说明】 END
        //* 则jb2011加入，表示列表单元固定高度，默认值=-1（即意味着行高自动计算）
        //* 使用BE LNF的项目，如要恢复行高算动计算则显示设置列表的固定行高为-1即可！
        list.setFixedCellHeight(27);//32//30);// TODO 此设置值作一个UIManager属性就可以方便以后设置了，-1是原系统默认值哦（即自动计算单元高）

        //* 不需要了，它样强制要求它为透明可能会影响以后用户的定制需求，干脆
        //* 设置它的背景为认白色，也能达到想到的N9白色背景
//		//此设置将取消列表背景的绘制（以便BE LNF中绘制N9背景图哦）
//		list.setOpaque(false);
    }
}
