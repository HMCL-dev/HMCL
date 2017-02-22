/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEFileChooserUI.java at 2015-2-1 20:25:39, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.filechooser;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalFileChooserUI;

/**
 * BeautyEye L&F implementation of a FileChooser.
 * <p>
 * 目前属通用跨平台专用UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com), 2012-09-17
 * @version 1.0
 */
public class BEFileChooserUI extends MetalFileChooserUI {

    public BEFileChooserUI(JFileChooser filechooser) {
        super(filechooser);
    }

    //
    // ComponentUI Interface Implementation methods
    //
    
    public static ComponentUI createUI(JComponent c) {
        return new BEFileChooserUI((JFileChooser) c);
    }

    /**
     * {@inheritDoc}
     *
     * 注：目前仅发现Windows平台的WindowsFileChooserUI存在它个问题 ！ 本方法由Jack
     * Jiang实现，没有以下默认背景绘制则在BE LNF中因透明窗口而使得
     * 文件选择框内容面板的空白处出现全透明现的丑陋现象，注释掉本方法即可见之前的问题
     */
    @Override
    public void paint(Graphics g, JComponent c) {
        g.setColor(c.getBackground());
        g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }

    /**
     * 重写父类方法，以实现对文件查看列表的额外设置.
     * <p>
     * 为什么要重写此方法，没有更好的方法吗？<br>
     * 答：因父类的封装结构不佳，filePane是private私有，子类中无法直接引用，
     * 要想对filePane中的文列表额外设置，目前重写本方法是个没有办法的方法.
     * <p>
     * sun.swing.FilePane源码可查看地址：<a href="http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b14/sun/swing/FilePane.java">Click
     * here.</a>
     *
     * @param fc the fc
     * @return the j panel
     */
    @Override
    protected JPanel createList(JFileChooser fc) {
        JPanel p = super.createList(fc);

        //* 以下代码的作用就是将文件列表JList对象引用给找回来（通过从它的父面板中层层向下搜索）
        //* ，因无法从父类中直接获得列表对象的直接引用，只能用此笨办法了
        if (p.getComponentCount() > 0) {
            Component scollPane = p.getComponent(0);
            if (scollPane != null && scollPane instanceof JScrollPane) {
                JViewport vp = ((JScrollPane) scollPane).getViewport();
                if (vp != null) {
                    Component fileListView = vp.getView();
                    //终于找到了文件列表的实例引用
                    if (fileListView != null && fileListView instanceof JList)
                        //把列表的行高改成-1（即自动计算列表每个单元的行高而不指定固定值）
                        //* 说明：在BeautyEye LNF中，为了便JList的UI更好看，在没有其它方法有前
                        //* 提下就在JList的BEListUI中给它设置了默写行高32，而JFildChooser中的
                        //* 文件列表将会因此而使得单元行高很大——从而导致文件列表很难看，此处就是恢复
                        //* 文件列表单元行高的自动计算，而非指定固定行高。
                        //*
                        //* 说明2：为什么不能利用list.getClientProperty("List.isFileList")从而在JList
                        //* 的ui中进行判断并区别对待是否是文件列表呢？
                        //* 答：因为"List.isFileList"是在BasicFileChooserUI中设置的，也就是说当为个属性被
                        //* 设置的时候JFileChooser中的文件列表已经实例化完成（包括它的ui初始化），所以此时
                        //* 如果在JList的ui中想区分是不可能的，因它还没有被调置，这个设置主要是供BasicListUI
                        //* 在被实例化完成后，来异步处理这个属性的（通过监听属性改变事件来实现的）
                        ((JList) fileListView).setFixedCellHeight(-1);
                }
            }
        }

        return p;
    }

//    //
//    // Renderer for Types ComboBox
//    //
//    protected FilterComboBoxRenderer createFilterComboBoxRenderer() 
//    {
//    	return new BEFilterComboBoxRenderer();
//    }
//    /**
//     * Render different type sizes and styles.
//     */
//    protected class BEFilterComboBoxRenderer extends FilterComboBoxRenderer 
//    {
//    	public BEFilterComboBoxRenderer()
//    	{
//    		super();
//    		
////    		//设置成透明背景则意味着不需要背景填充，但本类中的应用场景有点特殊
////            //——默认的render的UI里不需要绘制背景的情况下本类才可进行NinePatch图作为背景进行填充
////            setOpaque(false);
//            //TODO 此border（render的内衬）可以作为一个UIManager的属性哦，方便以后设置
//            //注：此内衬是决定列表单元间的上下左右空白的关键哦！
////            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
//    	}
//    	
//    	//按理说本方法中的是否选中背景填充实现逻辑在Render的UI中实现最为合理，但由于
//        //本类就是处理于UI实现中，所以容易产生冲突，故而不适宜自定义Ui实现，干脆硬编码则更直接
//        public void paintComponent(Graphics g) 
//        {
//        	//** 本HACK是为了解决 Issue 30：因JFileChooser中的文件类型选择下拉框的render
//        	//** （即本render）是自定义的（不是使用的ComboxUI里由BE LNF定义好的render而
//        	//** 导致的视觉效果不佳的问题
//        	//##### HACK：强行把绘制坐标右移、下移——以便使得与左边的空折多一点，要不然就太难看了
//        	g.translate(5, 1);
//        	
//        	//照常绘制（只是之前的坐标被移了一下而已）
//        	super.paintComponent(g);
//        	
//        	//##### HACK：恢复坐标
//        	g.translate(-5, -1);
//    	 }
//    }
}
