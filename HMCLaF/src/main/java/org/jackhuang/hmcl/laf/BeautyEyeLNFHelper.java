/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BeautyEyeLNFHelper.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.border.Border;

import org.jackhuang.hmcl.laf.widget.border.BEShadowBorder;
import org.jackhuang.hmcl.laf.widget.border.BEShadowBorder3;
import org.jackhuang.hmcl.laf.widget.border.PlainGrayBorder;

/**
 * <p>
 * BeautyEye Swing外观实现方案 - L&F核心辅助类.<br>
 * <p>
 * 项目托管地址：https://github.com/JackJiang2011/beautyeye
 *
 * @author Jack Jiang(jb2011@163.com), 2012-05
 * @version 1.0
 */
public class BeautyEyeLNFHelper {

    /**
     * 开关量：用于开启/关闭BeautyEye LNF的调试信息输出.
     * <p>
     * 默认false，即不开启调试信息输出.
     *
     * @since 3.2
     */
    public static boolean debug = false;
    /**
     * 开关量：用于开启/关闭当窗口（包括JFrame、JDialog）处于非活动 状态（inactivity）时的半透明视觉效果.
     * <p>
     * 默认true，即表示默认开启半透明效果.
     *
     * @since 3.2
     */
    public static boolean translucencyAtFrameInactive = true;

    /**
     * BeautyEye LNF 的窗口边框样式.
     * <p>
     * 默认值：运行在java1.6.0_u10及以上版本时使用
     * {@link FrameBorderStyle#translucencyAppleLike}，
     * 运行在java1.5版本时使用{@link FrameBorderStyle#generalNoTranslucencyShadow}.
     *
     * <p>
     * <b>注意：</b>如需设置本参数，请确保它在UIManager.setLookAndFeel前被设置，否则将不会起效哦.
     *
     * @see FrameBorderStyle
     */
    public static FrameBorderStyle frameBorderStyle = FrameBorderStyle.translucencyAppleLike;

    /**
     * 颜色全局变量：正常情况下的窗口文本颜色.
     * <p>
     * 你可设置本变量，也可直接通过{@code UIManager.put("activeCaptionText",new ColorUIResource(c))}和
     * {@code UIManager.put("inactiveCaptionText",new ColorUIResource(c))}来实现窗口文本颜色的改变.
     * <p>
     * 窗体不活动(inactivite)时的颜色将据此自动计算出来，无需额外设置. 默认是黑色（new Color(0,0,0)）.
     */
    public static Color activeCaptionTextColor = new Color(0, 0, 0);//黑色

    /**
     * 颜色全局变量：多数组件的背景色.
     * <p>
     * 你可设置本变量，也可直接通过各自的UIManager属性来改变它们.
     * <p>
     * 默认是浅灰色（new Color(250,250,250)）.
     *
     * @since 3.2
     */
    public static Color commonBackgroundColor = new Color(250, 250, 250);//240,240,240); //248,248,248);//255,255,255);//
    /**
     * 颜色全局变量：多数组件的前景色（文本颜色）.
     * <p>
     * 你可设置本变量，也可直接通过各自的UIManager属性来改变它们.
     * <p>
     * 默认是深灰色（new Color(60,60,60)）.
     *
     * @since 3.2
     */
    public static Color commonForegroundColor = new Color(60, 60, 60);//102,102,102);
    /**
     * 颜色全局变量：某些组件的焦点边框颜色. 当前主要用于按钮等焦点边框的绘制颜色.
     * <p>
     * 你可设置本变量，也可直接通过各自的UIManager属性来改变它们.
     * <p>
     * 默认是浅灰色（new Color(250,250,250)）.
     *
     * @since 3.2
     */
    public static Color commonFocusedBorderColor = new Color(162, 162, 162);
    /**
     * 颜色全局变量：某些组件被禁用时的文本颜色. 当前主要用于菜单项中.
     * <p>
     * 你可设置本变量，也可直接通过各自的UIManager属性来改变它们.
     * <p>
     * 默认是浅灰色（new Color(172,168,153)）.
     *
     * @since 3.2
     */
    public static Color commonDisabledForegroundColor = new Color(172, 168, 153);
    /**
     * 颜色全局变量：多数组件中文本被选中时的背景色.当前主要用于各文本组件等.
     * <p>
     * 你可设置本变量，也可直接通过各自的UIManager属性来改变它们.
     * <p>
     * 默认是深灰色（new Color(2,129,216)）.
     *
     * @since 3.2
     */
    public static Color commonSelectionBackgroundColor = new Color(2, 129, 216);//78,155,193));//58,135,173));//235,217,147));//new Color(255,237,167));
    /**
     * 颜色全局变量：多数组件中文本被选中时的前景色（文本颜色）.当前主要用于各文本组件、菜单项等.
     * <p>
     * 你可设置本变量，也可直接通过各自的UIManager属性来改变它们.
     * <p>
     * 默认是深灰色（new Color(255,255,255)）.
     *
     * @since 3.2
     */
    public static Color commonSelectionForegroundColor = new Color(255, 255, 255);

    /**
     * 开关量：用于默认设置或不设置窗口（Frame及其子类）的设置此窗体的最大化边界.
     * <p>
     * 此开关量是它是为了解决这样一个问题 ：<br>
     * 当不使用操作系统的窗口装饰（即使用完全自定义的窗口标题、边框）时，在windows上最 大化窗口时将会全屏显示从而覆盖了下方的任务栏（task
     * bar），这个问题 据说自2002年 就已存在，SUN一直未解决或者根本不认为是bug。目前的解决方案是当本变量是true时则
     * 默认为每一个窗体设置最大化边界，否则保持系统默认。不过这样设置并非完美方案：一旦 设置了最大边界，则此后无论Task Bar再怎么调
     * 整大小，比如被hide了，则窗体永远是设 置时的最大边界，不过目前也只能这么折中解决了，因为窗体最大化事件处理并非L&F中实现
     * ，暂未找到其它更好的方法。
     * <p>
     * 默认true，即表示默认开启此设置.
     *
     * @since 3.2
     * @see javax.swing.JFrame#setMaximizedBounds(java.awt.Rectangle)
     */
    public static boolean setMaximizedBoundForFrame = true;

    /**
     * BeautyEye LNF的外观实现核心方法.
     * <p>
     * 本方法可以直接从外部调用，这意味着BeautyEye LNF的外观核心实现无需特定于LookAndFeel的实例.
     * <p>
     * 也就是说任意外观都可应用本方法所作的外观实现（并保证跨平台），以使之可灵活应用.
     *
     * @see org.jb2011.lnf.beautyeye.titlepane.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.tab.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.button.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.separator.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.scroll.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.table.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.textcoms.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.popup.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.toolbar.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.menu.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.internalframe.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.progress.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.radio$cb_btn.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.combox.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.slider.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.tree.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.split.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.spinner.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.list.__UI__#uiImpl()
     * @see org.jb2011.lnf.beautyeye.filechooser.__UI__#uiImpl_win()
     */
    protected static void implLNF() {
        //自定义窗口的L&F实现
        org.jackhuang.hmcl.laf.titlepane.__UI__.uiImpl();
        //自定义JTabbedPane的L&F实现
        org.jackhuang.hmcl.laf.tab.__UI__.uiImpl();
        //自定义按钮的L&F实现
        org.jackhuang.hmcl.laf.button.__UI__.uiImpl();
        //各种杂七杂八的设置
        org.jackhuang.hmcl.laf.separator.__UI__.uiImpl();
        //自定义滚动条的L&F实现
        org.jackhuang.hmcl.laf.scroll.__UI__.uiImpl();
        //自定义表格头的L&F实现
        org.jackhuang.hmcl.laf.table.__UI__.uiImpl();
        //自定义文本组件的L&F实现
        org.jackhuang.hmcl.laf.textcoms.__UI__.uiImpl();
        //自定义弹出组件（包括toolTip组件和弹出菜单等）的L&F实现
        org.jackhuang.hmcl.laf.popup.__UI__.uiImpl();
        //自定义ToggleButton的L&F实现
        org.jackhuang.hmcl.laf.toolbar.__UI__.uiImpl();
        //自定义菜单项的L&F实现
        org.jackhuang.hmcl.laf.menu.__UI__.uiImpl();
        //自定义DesktopPane及内部窗体的L&F实现
        org.jackhuang.hmcl.laf.internalframe.__UI__.uiImpl();
        //自定义进度条的L&F实现
        org.jackhuang.hmcl.laf.progress.__UI__.uiImpl();
        //自定义单选按钮的L&F实现
        org.jackhuang.hmcl.laf.radio$cb_btn.__UI__.uiImpl();
        //自定义下拉框的L&F实现
        org.jackhuang.hmcl.laf.combox.__UI__.uiImpl();
        //自定义JSlider的L&F实现
        org.jackhuang.hmcl.laf.slider.__UI__.uiImpl();
        //自定义Jtree的L&F实现
        org.jackhuang.hmcl.laf.tree.__UI__.uiImpl();
        //自定义JSplitPane的L&F实现
        org.jackhuang.hmcl.laf.split.__UI__.uiImpl();
        //自定义JSpinner的L&F实现
        org.jackhuang.hmcl.laf.spinner.__UI__.uiImpl();
        //自定义JList的L&F实现
        org.jackhuang.hmcl.laf.list.__UI__.uiImpl();
        //自定义JFileChooser的L&F实现
        org.jackhuang.hmcl.laf.filechooser.__UI__.uiImpl();
    }

    /**
     * Gets the beauty eye lnf platform.
     *
     * @return {@code new BeautyEyeLookAndFeel()}
     */
    public static LookAndFeel getBeautyEyeLNFPlatform() {
        return new BeautyEyeLookAndFeel();
    }

    /**
     * 实施BeautyEye外观.<b>开发者使用BeautyEye L&F时应首选本方法.</b>
     * <p>
     * 本方法会据操作系统类型不同，来决定主类是使用BeautyEyeLookAndFeelWin还是BeautyEyeLookAndFeelWin.
     * 使用BeautyEye外观时推荐使用本方法来设置外观.之所以有平台不同主类不同的区分，是为了
     * 在Windows上平台上能更好的使用与操作系统相同的字体等设置.
     *
     * @throws Exception {@link UIManager#setLookAndFeel(String)}过程中出现的任何异常
     * @see #getBeautyEyeLNFStrWindowsPlatform()
     * @see #getBeautyEyeLNFPlatform()
     * @see org.jb2011.lnf.beautyeye.utils.Platform
     */
    public static void launchBeautyEyeLNF() throws Exception {
        System.setProperty("sun.java2d.noddraw", "true");
        UIManager.setLookAndFeel(getBeautyEyeLNFPlatform());
    }

    /**
     * <b>开发者无需关注本方法.</b>
     * <p>
     * true表示当前正在使用的窗口边框是不透明的，否则表示透明. 本方法目前作为BERootPaneUI中设置窗口是否透明的开关使用.
     * <p>
     * #### 官方API中存的Bug： ####<br>
     * 在jdk1.6.0_u33下+win7平台下（其它版本是否也有这情况尚未完全验证），JFrame窗口
     * 被设置成透明后，该窗口内所有文本都会被反走样（不管你有没有要求反走样），真悲具。
     * 这应该是官方AWTUtilities.setWindowOpaque(..)bug导致的,1.7.0_u6同样存在该问题，
     * 使用BeautyEye时，遇到这样的问题只能自行使用本方法中指定的不透明边框才行（这样
     * BERootPaneUI类的设置窗口透明的代码就不用执行，也就不用触发该bug了），但JDialog 不受此bug影响，诡异！
     *
     * @return true, if successful
     * @since 3.2
     */
    public static boolean isFrameBorderOpaque() {
        return frameBorderStyle == FrameBorderStyle.osLookAndFeelDecorated
                || frameBorderStyle == FrameBorderStyle.generalNoTranslucencyShadow;
    }

    /**
     * <b>开发者无需关注本方法.</b>
     * <p>
     * 根据设置的frameBorderStyle来返回正确的窗口边框.
     *
     * @return
     * 当frameBorderStyle=={@link FrameBorderStyle#defaultLookAndFeelDecorated}
     * 时返回无意义的BorderFactory.createEmptyBorder()，否则返回指定边框对象
     */
    public static Border getFrameBorder() {
        switch (frameBorderStyle) {
            case osLookAndFeelDecorated:
                return BorderFactory.createEmptyBorder();
            case translucencyAppleLike:
                return new BEShadowBorder3();
            case translucencySmallShadow:
                return new BEShadowBorder();
            case generalNoTranslucencyShadow:
            default:
                return new PlainGrayBorder();
        }
    }
//	/**
//	 * <b>开发者无需关注本方法.</b>
//	 * <p>
//	 * 根据设置的frameBorderStyle来返回正确的窗口边框边角的拖动区大小.
//	 * <p>
//	 * <b>重要说明：</b>本方法中的边框类型及其对应的边框类必须与方法 {@link #__getFrameBorder()}
//	 * 完全一致！
//	 * 
//	 * @return 当frameBorderStyle=={@link FrameBorderStyle#defaultLookAndFeelDecorated}
//	 * 时返回null，否则返回指定边框对象
//	 */
//	public static int __getFrameBorder_CORNER_DRAG_WIDTH()
//	{
//		switch(frameBorderStyle)
//		{
//			case osLookAndFeelDecorated:
//				return 16;
//			case translucencyAppleLike:
//				return BEShadowBorder3.CORNER_DRAG_WIDTH();
//			case translucencySmallShadow:
//				return new BEShadowBorder().CORNER_DRAG_WIDTH();
//			case generalNoTranslucencyShadow:
//			default:
//				return new PlainGrayBorder().CORNER_DRAG_WIDTH();
//		}
//	}
//	/**
//	 * <b>开发者无需关注本方法.</b>
//	 * <p>
//	 * 根据设置的frameBorderStyle来返回正确的窗口边框拖动区大小.
//	 * <p>
//	 * <b>重要说明：</b>本方法中的边框类型及其对应的边框类必须与方法 {@link #__getFrameBorder()}
//	 * 完全一致！
//	 * 
//	 * @return 当frameBorderStyle=={@link FrameBorderStyle#defaultLookAndFeelDecorated}
//	 * 时返回null，否则返回指定边框对象
//	 */
//	public static int __getFrameBorder_BORDER_DRAG_THICKNESS()
//	{
//		switch(frameBorderStyle)
//		{
//			case osLookAndFeelDecorated:
//				return 5;
//			case translucencyAppleLike:
//				return BEShadowBorder3.BORDER_DRAG_THICKNESS();
//			case translucencySmallShadow:
//				return new BEShadowBorder().BORDER_DRAG_THICKNESS();
//			case generalNoTranslucencyShadow:
//			default:
//				return new PlainGrayBorder().BORDER_DRAG_THICKNESS();
//		}
//	}

    /**
     * BeautyEye LNF 的窗口边框样式.
     */
    public enum FrameBorderStyle {

        /**
         * 使用本地系统的窗口装饰样式（本样式将能带来最佳性能，使用操作系统默认窗口样式）.
         */
        osLookAndFeelDecorated,
        /**
         * 使用类似于MacOSX的强烈立体感半透明阴影边框（本样式性能尚可，视觉效果最佳）.
         */
        translucencyAppleLike,
        /**
         * 使用不太强烈立体感半透明阴影边框（本样式性能尚可，视觉效果较soft）.
         */
        translucencySmallShadow,
        /**
         * 使用不透明的普通边框（这是本LNF在Java1.5版默认使用的样式，因为java1.5不支持窗口透明）
         */
        generalNoTranslucencyShadow
    }

    /**
     * <b>开发者暂时无需关注此接口.</b>
     * <p>
     * 实现了此接口的UI类意味着用户可以通过自行设置诸如border等，来 取消默认的NainePatch图实现的边框填充、背景填充等，具体设置哪
     * 些东西可以取消默认的NinePatch图填充的方式详见各自的类注释。
     */
    public interface __UseParentPaintSurported {

        /**
         * 是否使用父类的绘制实现方法，true表示是.
         * <p>
         * 因为在BE LNF中，进度条和背景都是使用N9图，比如没法通过设置JProgressBar的背景色和前景
         * 色来控制进度条的颜色，本方法的目的就是当用户设置了进度条的Background或Foreground 时告之本实现类不使用BE
         * LNF中默认的N9图填充绘制而改用父类中的方法（父类中的方法
         * 就可以支持颜色的设置罗，只是丑点，但总归是能适应用户的需求场景要求，其实用户完全可以 通过JProgressBar.setUI(new
         * MetalProgressBar())方式来自定义进度的UI哦）.
         *
         * @return true, if is use parent paint
         */
        boolean isUseParentPaint();
    }
}
