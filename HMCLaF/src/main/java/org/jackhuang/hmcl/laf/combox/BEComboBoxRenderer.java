/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEComboBoxRenderer.java at 2015-2-1 20:25:38, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.combox;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JList;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import org.jackhuang.hmcl.laf.menu.BEMenuUI;
import org.jackhuang.hmcl.laf.BEUtils;

/**
 * JComboBox组件下拉选项的render默认实现类.
 *
 * @author Jack Jiang(jb2011@163.com), 2012-07-05
 * @see BEComboBoxUI
 */
public class BEComboBoxRenderer extends BasicComboBoxRenderer {

    /**
     * 当前下拉框里的item是否被选中：用于是否要将背景填充选中样式时使用.
     */
    private boolean selected = false;

    /**
     * 记下本render对应的JComboBox的UI.
     */
    private BEComboBoxUI comboBoxUI = null;

    public BEComboBoxRenderer(BEComboBoxUI ui) {
        super();
        this.comboBoxUI = ui;
        //设置成透明背景则意味着不需要背景填充，但本类中的应用场景有点特殊
        //——默认的render的UI里不需要绘制背景的情况下本类才可进行NinePatch图作为背景进行填充
        setOpaque(false);
        //TODO 此border（render的内衬）可以作为一个UIManager的属性哦，方便以后设置
        //注：此内衬是决定列表单元间的上下左右空白的关键哦！
        setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 8));//此设置是与Combox.border UI属性配合哦
    }

    /**
     * {@inheritDoc}
     *
     * @see
     * javax.swing.plaf.basic.BasicComboBoxRenderer#getListCellRendererComponent
     * (javax.swing.JList, java.lang.Object, int, boolean, boolean)
     */
    @Override
    public Component getListCellRendererComponent(JList list,
            Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component c = super.getListCellRendererComponent(list,
                 value, index, isSelected, cellHasFocus);
        //add by jb2011：保存选中状态
        this.selected = isSelected;
        return c;
    }

    /**
     * {@inheritDoc}
     * 
     * 按理说本方法中的是否选中背景填充实现逻辑在Render的UI中实现最为合理，但由于
     * 本类就是处理于UI实现中，所以容易产生冲突，故而不适宜自定义Ui实现，干脆硬编码则更直接
     */
    @Override
    public void paintComponent(Graphics g) {
        //当下拉选项弹出时 且 该item是被选中时才需要把背景填充成选中样式
        if (comboBoxUI.isPopupVisible(null) && selected)
            BEMenuUI.drawSelectedBackground(g, 0, 0, this.getWidth(), this.getHeight());
        //下拉选项未弹出时JComboBox的背景样式
        //按理说，本类已设置成透明而无须填充背景，但本应用场景中，只能在默认Ui不填充背景的情况下才
        //能完成自定义NinePatch背景的填充。见 ComponentUI.update(Graphics g, JComponent c)方法

        //注意：本组件已经被设置成opaque=false即透明，否则本方法还会填充默认背景，那么选中时的
        //N9图背景因先绘将会被覆盖哦。所以要使用N9图作为选中时的背景则前提是本组件必须是透明
        super.paintComponent(g);
    }

    //copy from BasicComboBoxRenderer and modified by jb2011
    /**
     * A subclass of BasicComboBoxRenderer that implements UIResource.
     * BasicComboBoxRenderer doesn't implement UIResource directly so that
     * applications can safely override the cellRenderer property with
     * BasicListCellRenderer subclasses.
     * <p>
     * <strong>Warning:</strong>
     * Serialized objects of this class will not be compatible with future Swing
     * releases. The current serialization support is appropriate for short term
     * storage or RMI between applications running the same version of Swing. As
     * of 1.4, support for long term storage of all
     * JavaBeans<sup><font size="-2">TM</font></sup>
     * has been added to the <code>java.beans</code> package. Please see
     * {@link java.beans.XMLEncoder}.
     */
    public static class UIResource extends BEComboBoxRenderer implements javax.swing.plaf.UIResource {

        /**
         * Instantiates a new uI resource.
         *
         * @param ui the ui
         */
        public UIResource(BEComboBoxUI ui) {
            super(ui);
        }
    }
}
