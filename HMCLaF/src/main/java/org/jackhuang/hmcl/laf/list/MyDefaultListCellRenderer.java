/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * MyDefaultListCellRenderer.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.list;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;

/**
 * 列表单元的默认renderer实现类.
 *
 * @author Jack Jiang(jb2011@163.com), 2012-08-30
 * @version 1.0
 */
public class MyDefaultListCellRenderer extends DefaultListCellRenderer {

    private static final Icon9Factory ICON_9 = new Icon9Factory("list");

    /**
     * The is selected.
     */
    protected boolean isSelected = false;

    /**
     * The is focuesed.
     */
    protected boolean isFocuesed = false;

    /**
     * Instantiates a new my default list cell renderer.
     */
    public MyDefaultListCellRenderer() {
        //设置成透明背景则意味着不需要背景填充，但本类中的应用场景有点特殊
        //——默认的render的UI里不需要绘制背景的情况下本类才可进行NinePatch图作为背景进行填充
        setOpaque(false);

        //** 要像Combox的render一样设置border没用，因为列表单元不获得焦点时的border由UIManager属性List.cellNoFocusBorder决定
//      //注：此内衬是决定列表单元间的上下左右空白的关键哦！
//      setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
    }

    @Override
    public void paintComponent(Graphics g) {
        if (isSelected)
            //注意：2012-09-03日用的该图片右边有一个像素的空白哦，其实它是可以通过List.border的UI属性来调整的
            //之所以没有用它调整就是为了日后可以使用N9图来自由调整而无需调整代码（List.border的代码没置）
            ICON_9.get("selected_icon_bg")
                    .draw((Graphics2D) g, 0, 0, this.getWidth(), this.getHeight());
        else {
//			g.setColor(this.getBackground());
//    		//本Insets取this.getInsets()是符合Sun的设计初衷的，但是不合理（本身isPopupVisible==false
//    		//时的背景就是特殊情况，不能与下拉项同等视之）,所以此处用自定义的Insets来处理则在UI展现上更为合理 。
//    		//TODO 这个Instes可以作为UI属性“ComboBox.popupNoVisibleBgInstes”方便以后设置，暂时先硬编码吧，以后再说
//    		Insets is = new Insets(2,3,2,3);//this.getInsets();
////    		g.fillRect(is.left, is.top
////    				 , this.getWidth()-is.left-is.right
////    				 , this.getHeight()-is.top-is.bottom);
//    		BEUtils.fillTextureRoundRec((Graphics2D)g, this.getBackground(), is.left, is.top
//    				, this.getWidth()-is.left-is.right
//    				, this.getHeight()-is.top-is.bottom,20,0);//20,20
        }
//		else
        //注意：本组件已经被设置成opaque=false即透明，否则本方法还会填充默认背景，那么选中时的
        //N9图背景因先绘将会被覆盖哦。所以要使用N9图作为选中时的背景则前提是本组件必须是透明
        super.paintComponent(g);
    }

    @Override
    public Component getListCellRendererComponent(
            JList list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
        Component c = super.getListCellRendererComponent(list,
                 value, index, isSelected, cellHasFocus);

        this.isSelected = isSelected;
        this.isFocuesed = cellHasFocus;
        return c;
    }

    /**
     * A subclass of DefaultListCellRenderer that implements UIResource.
     * DefaultListCellRenderer doesn't implement UIResource directly so that
     * applications can safely override the cellRenderer property with
     * DefaultListCellRenderer subclasses.
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
    public static class UIResource extends MyDefaultListCellRenderer
            implements javax.swing.plaf.UIResource {
    }
}
