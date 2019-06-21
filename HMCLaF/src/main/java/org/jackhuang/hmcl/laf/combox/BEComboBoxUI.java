/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEComboBoxUI.java at 2015-2-1 20:25:38, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.combox;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.ComboBoxEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicComboBoxEditor;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import org.jackhuang.hmcl.laf.utils.AnimationController;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;
import org.jackhuang.hmcl.laf.utils.Skin;
import org.jackhuang.hmcl.laf.utils.TMSchema;
import org.jackhuang.hmcl.laf.utils.TMSchema.State;

/**
 * JComboBox的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com), 2012-06-30
 * @version 1.0
 * @see com.sun.java.swing.plaf.windows.WindowsComboBoxUI
 */
public class BEComboBoxUI extends BasicComboBoxUI
        implements org.jackhuang.hmcl.laf.BeautyEyeLNFHelper.__UseParentPaintSurported, MouseListener, Skin {

    private static final Icon9Factory ICON_9 = new Icon9Factory("combo");
    private static final Dimension BTN_SIZE = new Dimension(17, 20);
    public static ComponentUI createUI(JComponent c) {
        return new BEComboBoxUI();
    }

    //* 本方法由Jack Jiang于2012-09-07日加入
    /**
     * 是否使用父类的绘制实现方法，true表示是.
     * <p>
     * 因为在BE LNF中，边框和背景等都是使用N9图，没法通过设置背景色和前景
     * 色来控制JComboBox的颜色和边框，本方法的目的就是当用户设置了进度条的border或背景色 时告之本实现类不使用BE
     * LNF中默认的N9图填充绘制而改用父类中的方法（父类中的方法 就可以支持颜色的设置罗，只是丑点，但总归是能适应用户的需求场景要求，其实用户完全可以
     * 通过JComboBox.setUI(..)方式来自定义UI哦）.
     *
     * @return true, if is use parent paint
     */
    @Override
    public boolean isUseParentPaint() {
        return comboBox != null
                && (!(comboBox.getBorder() instanceof UIResource)
                || !(comboBox.getBackground() instanceof UIResource));
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);

        //2012-08-30*******************************************************【重要说明】 START 对应BEListUI中的【重要说明】
        //* 【重要说明】因BEListUI中为了使列表行单元高变的更高（在MyDefaultListCellRenderer.java中
        //* 像COmboxRender一样通过增到border不起效果，它可能是BasicListUI的设计缺陷，它要么取FixedCellHeight
        //* 固定值，要么取getPreferSize()即自动计算高度——它似乎是不计入border的，所以render设置border不起效）
        //* 所以只能为列表单元设置因定值：list.setFixedCellHeight(30)，但它将影响Combox里的行高（也会变成30高）
        //* 所以此处要把列表UI中强制设定的30高针对Combox还原成自动计算（API中规定FixedCellHeight==-1即表示自动计算）
        popup.getList().setFixedCellHeight(-1);
        //**************************************************************** 【重要说明】 END

        //* 以下代码由jb2011加入
//    	comboBox.setMaximumRowCount(8);//这个最大行可以起效，但似乎它的行高指的是一个固定值而不是计算值，像本LNF里因cell本身行高就很高
        //即使设置了最大显示行，但是显示的并不是指定值，有待进一步研究
        //为下拉框的弹出弹加border，这样上下空白外一点好看一些
        // install the scrollpane border
        Container parent = popup.getList().getParent();  // should be viewport
        if (parent != null) {
            parent = parent.getParent();  // should be the scrollpane
            if (parent != null && parent instanceof JScrollPane)
                LookAndFeel.installBorder((JScrollPane) parent, "ComboBox.scrollPaneBorder");//*~ 注：这个属性是Jack Jiang仿照JTabel里的实现自已加的属性
        }
    }

    @Override
    protected void installListeners() {
        super.installListeners();
        comboBox.addMouseListener(this);
    }

    @Override
    protected void uninstallListeners() {
        super.uninstallListeners();
        comboBox.removeMouseListener(this);
    }

    /**
     * Gets the combox.
     *
     * @return the combox
     */
    public JComboBox getCombox() {
        return this.comboBox;
    }

    /**
     * {@inheritDoc}
     *
     * 自定义下接框箭头按钮实现类
     */
    @Override
    protected JButton createArrowButton() {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                if (comboBox.isEditable())
                    ICON_9.getWithComboState("", comboBox.isEnabled(), mouseDown, mouseInside)
                            .draw((Graphics2D) g, 0, 0, getWidth(), getHeight());
            }
        };
        button.addMouseListener(this);
        button.setMinimumSize(BTN_SIZE);
        button.setPreferredSize(BTN_SIZE);
        button.setMargin(new Insets(0, 0, 0, 0));
        return button;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        hasFocus = comboBox.hasFocus();
        ListCellRenderer renderer = comboBox.getRenderer();
        Rectangle r = new Rectangle(0, 0, comboBox.getWidth(), comboBox.getHeight());
        paintCurrentValueBackground(g, r, hasFocus);
        if (!comboBox.isEditable())
            paintCurrentValue(g, rectangleForCurrentValue(), false);
    }

    /**
     * Paints the background of the currently selected item.
     *
     * @param g the g
     * @param bounds the bounds
     * @param hasFocus the has focus
     * @see javax.swing.plaf.basic.BasicComboBoxUI#paintCurrentValueBackground(java.awt.Graphics, java.awt.Rectangle, boolean)
     */
    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
        AnimationController.paintSkin(comboBox, this, g, bounds.x, bounds.y, bounds.width, bounds.height, getXPComboBoxState(comboBox));
    }

    @Override
    public TMSchema.Part getPart(JComponent c) {
        return TMSchema.Part.CP_COMBOBOX;
    }
    
    State getXPComboBoxState(JComponent c) {
        State state = State.NORMAL;
        if (!c.isEnabled()) {
            state = State.DISABLED;
        } else if (isPopupVisible(comboBox)) {
            state = State.PRESSED;
        } else if (mouseInside) {
            state = State.ROLLOVER;
        }
        return state;
    }
    
    @Override
    public void paintSkinRaw(Graphics g, int x, int y, int w, int h, TMSchema.State state) {
        String key;
        switch (state) {
            case PRESSED:
                key = "pressed";
                break;
            case DISABLED:
                key = "disabled";
                break;
            case ROLLOVER:
                key = "rollover";
                break;
            case NORMAL:
                key = "normal";
                break;
            default:
                return;
        }
        ICON_9.get(key).draw((Graphics2D) g, x, y, w, h);
    }
    
    

    //* copy from BasicComboBoxUI and modified by jb2011
    /**
     * Creates the default renderer that will be used in a non-editiable combo
     * box. A default renderer will used only if a renderer has not been
     * explicitly set with <code>setRenderer</code>.
     *
     * @return a <code>ListCellRender</code> used for the combo box
     * @see javax.swing.JComboBox#setRenderer
     */
    @Override
    protected ListCellRenderer createRenderer() {
        return new BEComboBoxRenderer.UIResource(this);
    }

    /**
     * {@inheritDoc}
     *
     * 改变方法可见性
     */
    @Override
    public Insets getInsets() {
        return super.getInsets();
    }

    /**
     * Creates the popup portion of the combo box.
     *
     * 目的是修正弹出popup窗口的x、y坐标，不像菜单UI里有
     * Menu.menuPopupOffsetX等4个属性可设置以备对坐标进行调整，BeautyEye LNF中由Jack Jiang
     * 依照Menu中的实现自定义了2个属性，以便以后配置。参考自jdk1.6.0_u18源码.
     *
     * @return an instance of <code>ComboPopup</code>
     * @see javax.swing.plaf.basic.BasicComboBoxUI#createPopup()
     */
    @Override
    protected ComboPopup createPopup() {
        return new BasicComboPopup(comboBox) {
            /**
             * popupOffsetX是jb2011自定的属性，用于修正下拉框的弹出窗的X坐标
             */
            private final int popupOffsetX = UIManager.getInt("ComboBox.popupOffsetX");
            /**
             * popupOffsetY是jb2011自定的属性，用于修正下拉框的弹出窗的Y坐标
             */
            private final int popupOffsetY = UIManager.getInt("ComboBox.popupOffsetY");

            @Override
            public void show() {
                setListSelection(comboBox.getSelectedIndex());
                Point location = getPopupLocation();
                show(comboBox, //以下x、y坐标修正代码由Jack Jiang增加
                        location.x + popupOffsetX, //*~ popupOffsetX是自定属性，用于修改弹出窗的X坐标
                        location.y + popupOffsetY //*~ popupOffsetY是自定属性，用于修改弹出窗的Y坐标
                );
            }

            /**
             * Sets the list selection index to the selectedIndex. This method
             * is used to synchronize the list selection with the combo box
             * selection.
             *
             * @param selectedIndex the index to set the list
             * @see javax.swing.plaf.basic.BasicComboPopup#setListSelection(int)
             */
            private void setListSelection(int selectedIndex) {
                if (selectedIndex == -1)
                    list.clearSelection();
                else {
                    list.setSelectedIndex(selectedIndex);
                    list.ensureIndexIsVisible(selectedIndex);
                }
            }

            /**
             * Calculates the upper left location of the Popup.
             *
             * @see javax.swing.plaf.basic.BasicComboPopup#getPopupLocation()
             */
            private Point getPopupLocation() {
                Dimension popupSize = comboBox.getSize();
                Insets insets = getInsets();

                // reduce the width of the scrollpane by the insets so that the popup
                // is the same width as the combo box.
                popupSize.setSize(popupSize.width - (insets.right + insets.left),
                        getPopupHeightForRowCount(comboBox.getMaximumRowCount()));
                Rectangle popupBounds = computePopupBounds(0, comboBox.getBounds().height,
                        popupSize.width, popupSize.height);
                Dimension scrollSize = popupBounds.getSize();
                Point popupLocation = popupBounds.getLocation();

                scroller.setMaximumSize(scrollSize);
                scroller.setPreferredSize(scrollSize);
                scroller.setMinimumSize(scrollSize);

                list.revalidate();

                return popupLocation;
            }
        };
    }

    /**
     * Creates the default editor that will be used in editable combo boxes. A
     * default editor will be used only if an editor has not been explicitly set
     * with <code>setEditor</code>.
     *
     * 重写父类方法的目的是使得默认的
     * Editor透明（即不填充默认背景），因为BE LNF中JTextField的LNF是用NP图
     * 实现的，此处不透明的话就会遮住NP背景图，从而使得外观难看。
     *
     * Fixed Issue 49(https://code.google.com/p/beautyeye/issues/detail?id=49)
     *
     * @return a <code>ComboBoxEditor</code> used for the combo box
     * @see javax.swing.JComboBox#setEditor
     * @see com.sun.java.swing.plaf.windows.WindowsComboBoxUI.WindowsComboBoxEditor
     */
    @Override
    protected ComboBoxEditor createEditor() {
        BasicComboBoxEditor.UIResource bcbe = new BasicComboBoxEditor.UIResource();
        Component c = bcbe.getEditorComponent();
        if (c != null) {
            //把默认的Editor设置成透明(editor不透明的话就会遮住NP背景图，从而使得外观难看)
            ((JComponent) c).setOpaque(false);

            //* 以下这段是为了给默认Editor加上border而加（没有它个border将使
            //* 得与不可编辑comboBox的内容组件看起来有差异哦），
            //* 在WindowsComboBoxUI中，这段代码是放在WindowsComboBoxEditor
            //* 中的方法createEditorComponent中实现，由于该 方法是1.6里才有的，
            //* BE LNF因要兼容java1.5，所以不作类似实现就在本方法中实现也没有问题。
            //* 类似实现请参考WindowsComboBoxUI.WindowsComboBoxEditor类
//    			JTextField editor = (JTextField)c;
            Border border = (Border) UIManager.get("ComboBox.editorBorder");
            if (border != null)
                ((JComponent) c).setBorder(border);
        }
        return bcbe;
    }

    // =================================================================================================================
    // MouseListener Methods
    private boolean mouseInside = false;
    private boolean mouseDown = false;

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (comboBox.isEditable()) {
            if (e.getComponent() == arrowButton)
                mouseInside = true;
        } else {
            mouseInside = true;
            comboBox.repaint();
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (comboBox.isEditable()) {
            if (e.getComponent() == arrowButton)
                mouseInside = false;
        } else {
            mouseInside = false;
            comboBox.repaint();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (comboBox.isEditable()) {
            if (e.getComponent() == arrowButton)
                mouseDown = true;
        } else {
            mouseDown = true;
            comboBox.repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (comboBox.isEditable()) {
            if (e.getComponent() == arrowButton)
                mouseDown = false;
        } else {
            mouseDown = false;
            comboBox.repaint();
        }
    }
}
