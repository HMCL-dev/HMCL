/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEInternalFrameTitlePane.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.internalframe;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Icon;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import javax.swing.plaf.metal.MetalLookAndFeel;

import org.jackhuang.hmcl.laf.titlepane.BETitlePane;
import org.jackhuang.hmcl.laf.utils.MySwingUtilities2;
import org.jackhuang.hmcl.laf.utils.WinUtils;

/**
 * 内部窗体的标题栏UI实现.
 * 
 * BeautyEye外观实现中取消了isPalette的所有特殊处理，isPalette及相关属性在
 *该外观中将失去意义，请注意！
 *虽然beautyEye是参考自MetalLookAndFeel，但因beautyEye使用了Insets很大的立体边框，
 * 则如果还要像MetalLookAndFeel实现Palette类型的JInternalFrame则效果会很难看，干脆就就像
 * WindowsLookAndFeel一样，不去理会什么Palette，在当前的L&F下没有任何减分。
 * 
 * @see com.sun.java.swing.plaf.windows.WindowsInternalFrameTitlePane
 * @author Jack Jiang
 */
public class BEInternalFrameTitlePane extends BasicInternalFrameTitlePane {
//	protected boolean isPalette = false;
//	protected Icon paletteCloseIcon;
//	protected int paletteTitleHeight;

    /**
     * The Constant handyEmptyBorder.
     */
    private static final Border handyEmptyBorder = new EmptyBorder(0, 0, 0, 0);

    /**
     * Key used to lookup Color from UIManager. If this is null,
     * <code>getWindowTitleBackground</code> is used.
     */
    private String selectedBackgroundKey;
    /**
     * Key used to lookup Color from UIManager. If this is null,
     * <code>getWindowTitleForeground</code> is used.
     */
    private String selectedForegroundKey;
    /**
     * Key used to lookup shadow color from UIManager. If this is null,
     * <code>getPrimaryControlDarkShadow</code> is used.
     */
    private String selectedShadowKey;
    /**
     * Boolean indicating the state of the <code>JInternalFrame</code>s closable
     * property at <code>updateUI</code> time.
     */
    private boolean wasClosable;

    /**
     * The buttons width.
     */
    int buttonsWidth = 0;

    /**
     * Instantiates a new bE internal frame title pane.
     *
     * @param f the f
     */
    public BEInternalFrameTitlePane(JInternalFrame f) {
        super(f);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // This is done here instead of in installDefaults as I was worried
        // that the BasicInternalFrameUI might not be fully initialized, and
        // that if this resets the closable state the BasicInternalFrameUI
        // Listeners that get notified might be in an odd/uninitialized state.
        updateOptionPaneState();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        setFont(UIManager.getFont("InternalFrame.titleFont"));
//		paletteTitleHeight = UIManager
//				.getInt("InternalFrame.paletteTitleHeight");
//		paletteCloseIcon = UIManager.getIcon("InternalFrame.paletteCloseIcon");
        wasClosable = frame.isClosable();
        selectedForegroundKey = selectedBackgroundKey = null;
        if (true)
            setOpaque(false);
    }

    @Override
    protected void uninstallDefaults() {
        super.uninstallDefaults();
        if (wasClosable != frame.isClosable())
            frame.setClosable(wasClosable);
    }

    @Override
    protected void createButtons() {
        super.createButtons();

        Boolean paintActive = frame.isSelected() ? Boolean.TRUE : Boolean.FALSE;
        iconButton.putClientProperty("paintActive", paintActive);
        iconButton.setBorder(handyEmptyBorder);

        maxButton.putClientProperty("paintActive", paintActive);
        maxButton.setBorder(handyEmptyBorder);

        closeButton.putClientProperty("paintActive", paintActive);
        closeButton.setBorder(handyEmptyBorder);

        // The palette close icon isn't opaque while the regular close icon is.
        // This makes sure palette close buttons have the right background.
        closeButton.setBackground(MetalLookAndFeel.getPrimaryControlShadow());

        if (true) {
            iconButton.setContentAreaFilled(false);
            maxButton.setContentAreaFilled(false);
            closeButton.setContentAreaFilled(false);
        }
    }

    /**
     * Override the parent's method to do nothing. Metal frames do not have
     * system menus.
     */
    @Override
    protected void assembleSystemMenu() {
    }

    /**
     * Override the parent's method to do nothing. Metal frames do not have
     * system menus.
     *
     * @param systemMenu the system menu
     */
    @Override
    protected void addSystemMenuItems(JMenu systemMenu) {
    }

    /**
     * Override the parent's method to do nothing. Metal frames do not have
     * system menus.
     */
    @Override
    protected void showSystemMenu() {
    }

    /**
     * Override the parent's method avoid creating a menu bar. Metal frames do
     * not have system menus.
     */
    @Override
    protected void addSubComponents() {
        add(iconButton);
        add(maxButton);
        add(closeButton);
    }

    @Override
    protected PropertyChangeListener createPropertyChangeListener() {
        return new MetalPropertyChangeHandler();
    }

    @Override
    protected LayoutManager createLayout() {
        return new XMetalTitlePaneLayout();
    }

    class MetalPropertyChangeHandler extends
            BasicInternalFrameTitlePane.PropertyChangeHandler {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String prop = (String) evt.getPropertyName();
            if (prop.equals(JInternalFrame.IS_SELECTED_PROPERTY)) {
                Boolean b = (Boolean) evt.getNewValue();
                iconButton.putClientProperty("paintActive", b);
                closeButton.putClientProperty("paintActive", b);
                maxButton.putClientProperty("paintActive", b);
            } else if ("JInternalFrame.messageType".equals(prop)) {
                updateOptionPaneState();
                frame.repaint();
            }
            super.propertyChange(evt);
        }
    }

    class XMetalTitlePaneLayout extends TitlePaneLayout {

        @Override
        public void addLayoutComponent(String name, Component c) {
        }

        @Override
        public void removeLayoutComponent(Component c) {
        }

        @Override
        public Dimension preferredLayoutSize(Container c) {
            return minimumLayoutSize(c);
        }

        @Override
        public Dimension minimumLayoutSize(Container c) {
            // Compute width.
            int width = 30;
            if (frame.isClosable())
                width += 21;
            if (frame.isMaximizable())
                width += 16 + (frame.isClosable() ? 10 : 4);
            if (frame.isIconifiable())
                width += 16 + (frame.isMaximizable() ? 2
                        : (frame.isClosable() ? 10 : 4));
            FontMetrics fm = frame.getFontMetrics(getFont());
            String frameTitle = frame.getTitle();
            int title_w = frameTitle != null ? MySwingUtilities2.stringWidth(
                    frame, fm, frameTitle) : 0;
            int title_length = frameTitle != null ? frameTitle.length() : 0;

            if (title_length > 2) {
                int subtitle_w = MySwingUtilities2.stringWidth(frame, fm, frame
                        .getTitle().substring(0, 2)
                        + "...");
                width += (title_w < subtitle_w) ? title_w : subtitle_w;
            } else
                width += title_w;

            // Compute height.
            int height = 0;

//			if (isPalette)
//			{
//				height = paletteTitleHeight;
//			}
//			else
            {
                int fontHeight = fm.getHeight();
                fontHeight += 4;//默认是 +=7 
                Icon icon = frame.getFrameIcon();
                int iconHeight = 0;
                if (icon != null)
                    // SystemMenuBar forces the icon to be 16x16 or less.
                    iconHeight = Math.min(icon.getIconHeight(), 16);
                iconHeight += 5;
                height = Math.max(fontHeight, iconHeight) + 5;//默认是 +0，modified by jb2011 2012-06-18
            }

            return new Dimension(width, height);
        }

        @Override
        public void layoutContainer(Container c) {
            boolean leftToRight = WinUtils.isLeftToRight(frame);

            int w = getWidth();
            int x = leftToRight ? w : 0;
            int y = 5;//默认是0，modified by jb2011
            int spacing;

            // assumes all buttons have the same dimensions
            // these dimensions include the borders
            int buttonHeight = closeButton.getIcon().getIconHeight();
            int buttonWidth = closeButton.getIcon().getIconWidth();

            if (frame.isClosable()) //				if (isPalette)
            //				{
            //					spacing = 3;
            //					x += leftToRight ? -spacing - (buttonWidth + 2) : spacing;
            //					closeButton.setBounds(x, y, buttonWidth + 2,
            //							getHeight() - 4);
            //					if (!leftToRight)
            //						x += (buttonWidth + 2);
            //				}
            //				else
            {
                spacing = 4;
                x += leftToRight ? -spacing - buttonWidth : spacing;
                closeButton.setBounds(x, y, buttonWidth, buttonHeight);
                if (!leftToRight)
                    x += buttonWidth;
            }

            if (frame.isMaximizable())// && !isPalette)
            {
                spacing = frame.isClosable() ? 2 : 4; //10 : 40
                x += leftToRight ? -spacing - buttonWidth : spacing;
                maxButton.setBounds(x, y, buttonWidth, buttonHeight);
                if (!leftToRight)
                    x += buttonWidth;
            }

            if (frame.isIconifiable())// && !isPalette)
            {
                spacing = frame.isMaximizable() ? 2 : (frame.isClosable() ? 10
                        : 4);
                x += leftToRight ? -spacing - buttonWidth : spacing;
                iconButton.setBounds(x, y, buttonWidth, buttonHeight);
                if (!leftToRight)
                    x += buttonWidth;
            }

            buttonsWidth = leftToRight ? w - x : x;
        }
    }

//	public void paintPalette(Graphics g)
//	{
//		boolean leftToRight = WinUtils.isLeftToRight(frame);
//
//		int width = getWidth();
//		int height = getHeight();
//
//		Color background = MetalLookAndFeel.getPrimaryControlShadow();
//		Color darkShadow = MetalLookAndFeel.getPrimaryControlDarkShadow();
//
//		NLTitlePane.paintTitlePane(g, 0, 0, width , height, false
//				, background, darkShadow);
//	}
    
    @Override
    public void paintComponent(Graphics g) {
//		if (isPalette)
//		{
//			paintPalette(g);
//			return;
//		}

        boolean leftToRight = WinUtils.isLeftToRight(frame);
        boolean isSelected = frame.isSelected();//! 选中与否的判断方式，参见border部分的注释

        int width = getWidth();
        int height = getHeight();

        Color background = null;
        Color foreground = null;
        Color shadow = null;

        if (isSelected) {
//			if (!true)
//			{
//				closeButton.setContentAreaFilled(true);
//				maxButton.setContentAreaFilled(true);
//				iconButton.setContentAreaFilled(true);
//			}
            if (selectedBackgroundKey != null)
                background = UIManager.getColor(selectedBackgroundKey);
            if (background == null)
                background = UIManager.getColor("activeCaption");
            if (selectedForegroundKey != null)
                foreground = UIManager.getColor(selectedForegroundKey);
            if (selectedShadowKey != null)
                shadow = UIManager.getColor(selectedShadowKey);
            if (shadow == null)
                shadow = UIManager.getColor("activeCaptionBorder");
            if (foreground == null)
                foreground = UIManager.getColor("activeCaptionText");
        } else {
            if (!true) {
                closeButton.setContentAreaFilled(false);
                maxButton.setContentAreaFilled(false);
                iconButton.setContentAreaFilled(false);
            }
            background = UIManager.getColor("inactiveCaption");
            foreground = UIManager.getColor("inactiveCaptionText");
            shadow = UIManager.getColor("inactiveCaptionBorder");
        }

        //---------------------------------------------------绘制标题栏背景
//		Color topDarkShadow2 = LNFUtils.getColor(
//				background, -40, -40, -40)
//				,topHightLight2 = LNFUtils.getColor(background,
//						60, 60, 60)
//				,topDarkHightLight2 = LNFUtils.getColor(background,
//						20, 20, 20);
        /*>>>>>>>>>>>>>>>>>>>>>>>>>>>>> 绘制标题栏背景START */
 /*
		 * ** bug修正 by js,2009-09-30（这因是JDK1.5的Swing底层BUG）
		 * 
		 * BUG描述：当操作系统的主题切换时，JInternalFrame有时相同的方法this.getBounds()获得的结果是不一样的
		 * 			，这种差异主要在于有时this.getBounds()获得的结果并未包括它的border，主要表现就是结果
		 * 			的(x,y)的坐标是未包含border的坐标，因而这种情况下它的(x=0,y=0),而正确应该
		 * 			(x=border.insets.left,y=border.insets.top)，如果无视此bug，则titlePane
		 * 			在填充时会变的丑陋。
		 * 
		 * 解决方法：当确知这个bug发生后，只能以人工方式弥补这种异相，如 强制修正其(x,y)的坐标，并相应地调整
		 * 			titlePane的填充区域。
         */
//		Border b=frame.getBorder();
//		Insets is=b.getBorderInsets(frame);
//		Rectangle bounds = this.getBounds();
//		int paintTitlePaneX = bounds.x,paintTitlePaneY = bounds.y;
////		boolean isBUG = false;
//////		System.out.println("JInternalFrame的UI是否处于正常isBUG="+isBUG);
////		if(isBUG=(is.left!=bounds.x))//当is.left!=bounds.x即可认定已经产生了BUG
////			paintTitlePaneX = is.left;
////		if(isBUG=(is.top!=bounds.y))//当is.left!=bounds.x同样可认定已经产生了BUG
////			paintTitlePaneY = is.top;
////		if(isBUG)//有BUG时的填充
////		{
////			//----------------------------- 以下代码是为了弥补因BUG而产生的填充异常
////			//*以下(1),(2),(3)部分代码是用于弥补BUG下的border外观被titlePane覆盖的错误
////			//*请参见JInternalFrameDialogBorder的边框填充代码（尽量在此处模拟出边框的TOP部分效果）>
////			//水平第一条线(1)
////			g.setColor(topDarkShadow2);
////			g.drawLine(0, 0, width, 0);
////			g.drawLine(0, 1, 0, height);//左坚线
////			g.drawLine(width, 1, width, height);//右坚线
////			//水平第二条线(2)
////			g.setColor(topHightLight2);
////			g.drawLine(1, 1, width-NLMetalBorders.InternalFrameDialogBorder.insets.left, 1);
////			//水平第三条线(3)
////			g.setColor(topDarkHightLight2);
////			g.drawLine(2, 2, width-NLMetalBorders.InternalFrameDialogBorder.insets.left, 2);
////			
////			NLTitlePane.paintTitlePane(g//左右空起的部分由Border绘制
////					, paintTitlePaneX//ZCMetalBorders.InternalFrameDialogBorder.insets.left
////					, paintTitlePaneY//ZCMetalBorders.InternalFrameDialogBorder.insets.left
////					, width-is.left*2	//注意此处也因BUG而作了填充区域的调整
////					, height, isSelected
////					, background, shadow);
////			//----------------------------- END
////		}
////		//正常无BUG时的填充
////		else
        {
            Insets frameInsets = frame.getInsets();
            //** Swing BUG：按理说，此处是不需要传入frameInstes的，因父类BasicInternalFrameTitlePane的布局
            //是存在BUG（布计算时没有把BorderInstes算入到x、y的偏移中，导致UI中paint时只能自行
            //进行增益，否则填充的图璩形肯定就因没有算上borderInstes而错位，详见父类中的
            //BasicInternalFrameTitlePane中内部类Handler的layoutContainer方法里
            //getNorthPane().setBounds(..)这一段
//			NLTitlePane.paintTitlePane(g
//					, frameInsets.left//0
//					, frameInsets.top//0
//					, width-frameInsets.left-frameInsets.right//-0
//					, height, isSelected
////					, background, shadow
//					);
            paintTitlePaneImpl(frameInsets, g, width, height, isSelected);
        }
        /*>>>>>>>>>>>>>>>>>>>>>>>>>>>>> 绘制标题栏背景END */

        //----------------------------------------------------绘制标题及图标
        int titleLength = 0;
        int xOffset = leftToRight ? 5 : width - 5;
        String frameTitle = frame.getTitle();

        Icon icon = frame.getFrameIcon();
        if (icon != null) {
            if (!leftToRight)
                xOffset -= icon.getIconWidth();
            int iconY = ((height / 2) - (icon.getIconHeight() / 2));
            icon.paintIcon(frame, g, xOffset + 2, iconY + 1);//默认是xOffset +0, iconY+0
            xOffset += leftToRight ? icon.getIconWidth() + 5 : -5;
        }

        if (frameTitle != null) {
            Font f = getFont();
            g.setFont(f);
            FontMetrics fm = MySwingUtilities2.getFontMetrics(frame, g, f);
            int fHeight = fm.getHeight();

            int yOffset = ((height - fm.getHeight()) / 2) + fm.getAscent();

            Rectangle rect = new Rectangle(0, 0, 0, 0);
            if (frame.isIconifiable())
                rect = iconButton.getBounds();
            else if (frame.isMaximizable())
                rect = maxButton.getBounds();
            else if (frame.isClosable())
                rect = closeButton.getBounds();
            int titleW;

            if (leftToRight) {
                if (rect.x == 0)
                    rect.x = frame.getWidth() - frame.getInsets().right - 2;
                titleW = rect.x - xOffset - 4;
                frameTitle = getTitle(frameTitle, fm, titleW);
            } else {
                titleW = xOffset - rect.x - rect.width - 4;
                frameTitle = getTitle(frameTitle, fm, titleW);
                xOffset -= MySwingUtilities2.stringWidth(frame, fm, frameTitle);
            }

            titleLength = MySwingUtilities2.stringWidth(frame, fm, frameTitle);

//			g.setColor(Color.DARK_GRAY);//
//			NLUtils.drawString(frame, g, frameTitle, xOffset+1, yOffset+1);
            g.setColor(foreground);
//			if(NLLookAndFeel.windowTitleAntialising)
//				NLLookAndFeel.setAntiAliasing((Graphics2D) g,true);
            MySwingUtilities2.drawString(frame, g, frameTitle, xOffset, yOffset);
//			if(NLLookAndFeel.windowTitleAntialising)
//				NLLookAndFeel.setAntiAliasing((Graphics2D) g,false);
            xOffset += leftToRight ? titleLength + 5 : -5;
        }
    }

    /**
     * Paint title pane impl.
     *
     * @param frameInsets the frame insets
     * @param g the g
     * @param width the width
     * @param height the height
     * @param isSelected the is selected
     */
    protected void paintTitlePaneImpl(Insets frameInsets, Graphics g,
            int width, int height, boolean isSelected) {
        BETitlePane.paintTitlePane(g,
                frameInsets.left//0
                ,
                 frameInsets.top//0
                ,
                 width - frameInsets.left - frameInsets.right//-0
                ,
                 height, isSelected
        //				, background, shadow
        );
    }

//	public void setPalette(boolean b)
//	{
//		isPalette = b;
//
//		if (isPalette)
//		{
//			closeButton.setIcon(paletteCloseIcon);
//			if (frame.isMaximizable())
//				remove(maxButton);
//			if (frame.isIconifiable())
//				remove(iconButton);
//		}
//		else
//		{
//			closeButton.setIcon(closeIcon);
//			if (frame.isMaximizable())
//				add(maxButton);
//			if (frame.isIconifiable())
//				add(iconButton);
//		}
//		revalidate();
//		repaint();
//	}
    /**
     * Updates any state dependant upon the JInternalFrame being shown in a
     * <code>JOptionPane</code>.
     */
    private void updateOptionPaneState() {
        int type = -2;
        boolean closable = wasClosable;
        Object obj = frame.getClientProperty("JInternalFrame.messageType");

        if (obj == null)
            // Don't change the closable state unless in an JOptionPane.
            return;
        if (obj instanceof Integer)
            type = ((Integer) obj).intValue();
        switch (type) {
            case JOptionPane.ERROR_MESSAGE:
                selectedBackgroundKey = "OptionPane.errorDialog.titlePane.background";
                selectedForegroundKey = "OptionPane.errorDialog.titlePane.foreground";
                selectedShadowKey = "OptionPane.errorDialog.titlePane.shadow";
                closable = false;
                break;
            case JOptionPane.QUESTION_MESSAGE:
                selectedBackgroundKey = "OptionPane.questionDialog.titlePane.background";
                selectedForegroundKey = "OptionPane.questionDialog.titlePane.foreground";
                selectedShadowKey = "OptionPane.questionDialog.titlePane.shadow";
                closable = false;
                break;
            case JOptionPane.WARNING_MESSAGE:
                selectedBackgroundKey = "OptionPane.warningDialog.titlePane.background";
                selectedForegroundKey = "OptionPane.warningDialog.titlePane.foreground";
                selectedShadowKey = "OptionPane.warningDialog.titlePane.shadow";
                closable = false;
                break;
            case JOptionPane.INFORMATION_MESSAGE:
            case JOptionPane.PLAIN_MESSAGE:
                selectedBackgroundKey = selectedForegroundKey = selectedShadowKey = null;
                closable = false;
                break;
            default:
                selectedBackgroundKey = selectedForegroundKey = selectedShadowKey = null;
                break;
        }
        if (closable != frame.isClosable())
            frame.setClosable(closable);
    }

    /**
     * {@inheritDoc}
     *
     * 改变父类的方法的可见性为public，方便外界调用，仅此而已.
     */
    @Override
    public void uninstallListeners() {
        super.uninstallListeners();
    }
}
