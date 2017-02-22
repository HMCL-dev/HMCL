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
package org.jackhuang.hmcl.laf.table;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.UIManager;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.IconFactory;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__  extends UI {

    private static final IconFactory ICON = new IconFactory("table");

    public static void uiImpl() {
        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> JTable的相关ui属性设定
        //JTable所属的滚动面板的border实现（不像JList，JTable的UI自动为它配备的一个JScrollPane）
        put("Table.scrollPaneBorder", new TableScrollBorder());//defaut is com.sun.java.swing.plaf.windows.XPStyle.XPFillBorder
        put("Table.focusCellHighlightBorder", new FocusCellHighlightBorder());//new BEDashedBorder(new Color(0,160,0),1,0,true,false,true,false)));
        /* ~~注：这个属性是jb2011为了更好的ui效果自已加的属性 */
        put("Table.focusCellHighlightBorderColor", Color.white);//new ColorUIResource(Color.red));
        /* ~~注：这个属性是jb2011为了更好的ui效果自已加的属性，目的是使Table.focusCellHighlightBorder有点立体效果哦 */
        putColor("Table.focusCellHighlightBorderHighlightColor", 255, 255, 255, 70);//注意：这个颜色是半透明的哦
        put("Table.background", Color.white);
        //** 2011-03-16 add by jb2011 为了使JDK1.6及以上表格头在排序时能显示排序箭头（1.6里的排序图标是在UI里设定的）
        UIManager.put("Table.descendingSortIcon", ICON.get("descending_sort"));
        UIManager.put("Table.ascendingSortIcon", ICON.get("ascending_sort"));
        put("Table.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        putColor("Table.gridColor", 220, 220, 220);
        put("Table.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("Table.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("TableUI", BETableUI.class);

        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> JTable表头相关ui属性设定
//		UIManager.put("TableHeader.font",new Font("宋体",Font.PLAIN,12));//此属性将决定表头的字体
        put("TableHeader.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("TableHeader.foreground", BeautyEyeLNFHelper.commonForegroundColor);

        //不建议完全定制表头ui，因为BasicTableHeaderUI里的关键方法都是private，无法继承重写，要实现自已
        //的绘制逻辑需要重写大段代码，而因JTable在不同jdk版本里的变动较大：比如1.6里才有的排序（及图标）及相关方法
        //在不同的版本里都不尽相同，而且调用了若干sun的非公开包里的api。虽存在兼容性问题，为了UI美观，还是自定义实现吧
        put("TableHeaderUI", BETableHeaderUI.class);
        //** BE LNF的本属性只在Java版本高于1.5时起效
        //* 由jb2011自已加的属性，因原BasicTableHeaderUI里用TableHeader.cellBorder来设置
        //border，但WindowsTableHeaderUI的border则是自已实现的IconBorder，而BE LNF中则是仿
        //照Windows LNF实现，所以只能实现一个自已的属性来供以后的使用者灵活设定（否则只能像Windows LNF一样硬编码在代码里）
        putInsets("TableHeader.cellMargin", 7, 0, 7, 0);
    }

    /**
     * Border for a Table Header.
     *
     * @see javax.swing.plaf.metal.TableHeaderBorder
     */
    public static class TableHeaderBorder extends javax.swing.border.AbstractBorder {

        protected Insets editorBorderInsets = new Insets(7, 0, 7, 0);//默认是2, 2, 2, 0 );

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            g.translate(x, y);

//			g.setColor(MetalLookAndFeel.getControlDarkShadow() );
//			g.drawLine( w-1, 0, w-1, h-1 );
//			g.drawLine( 1, h-1, w-1, h-1 );
//			g.setColor( MetalLookAndFeel.getControlHighlight() );
//			g.drawLine( 0, 0, w-2, 0 );
//			g.drawLine( 0, 0, 0, h-2 );
            //* add by Jack Jiang START
            //绘制表头单元的底部水平线（跟网格线颜色一样就可以了）
            g.setColor(UIManager.getColor("Table.gridColor"));
            g.drawLine(0, h - 1, w, h - 1);

            //绘制表头单元的右分隔竖线
            BETableHeaderUI.ICON_9.get("header_cell_separator")
                    .draw((Graphics2D) g, w - 4, 0, 4, h);
            //* add by Jack Jiang END

            g.translate(-x, -y);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return editorBorderInsets;
        }
    }
}
