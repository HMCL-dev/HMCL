/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BETreeUI.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.tree;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

/**
 * JTree的UI实现类.
 *
 * 目前，本类中暂未对UI方面代码进行修改，对Tree的UI修改主要是基于UIManager对应Tree
 * 属性的配置，目前已经足够达到预期效果，如有必要可开放本类中的代码进行深入修改。
 *
 * @author Jack Jiang(jb2011@163.com)
 * @version 1.0
 * @see com.sun.java.swing.plaf.windows.WindowsTreeUI
 */
public class BETreeUI extends BasicTreeUI {

    public static ComponentUI createUI(JComponent c) {
        return new BETreeUI();
    }

//	//copy from BasicTreeUI and modified by jb2011
//	// This method is slow -- revisit when Java2D is ready.
//	// assumes x1 <= x2
//	/**
//	 * 绘制水平层次虚线.
//   * Jack Jiang重写此方法的目的是加调整虚线的步进值.
//	 */
//	protected void drawDashedHorizontalLine(Graphics g, int y, int x1, int x2)
//	{
//		// Drawing only even coordinates helps join line segments so they
//		// appear as one line.  This can be defeated by translating the
//		// Graphics by an odd amount.
////		x1 += (x1 % 2);
//		x1 += (x1 % 6);
//
////		for (int x = x1; x <= x2; x+=2) 
////		{
////			g.drawLine(x, y, x, y);
////		}
//		for (int x = x1; x <= x2; x+=6) 
//		{
//			g.drawLine(x, y, x, y);
//		}
//	}
//	
//	//copy from BasicTreeUI and modified by jb2011
//	// This method is slow -- revisit when Java2D is ready.
//	// assumes y1 <= y2
//	/**
//	 * 绘制垂直层次虚线.
//  * Jack Jiang重写此方法的目的是加调整虚线的步进值.
//	 */
//	protected void drawDashedVerticalLine(Graphics g, int x, int y1, int y2) 
//	{
//		// Drawing only even coordinates helps join line segments so they
//		// appear as one line.  This can be defeated by translating the
//		// Graphics by an odd amount.
////		y1 += (y1 % 2);
//		y1 += (y1 % 6);
//
////		for (int y = y1; y <= y2; y+=2) {
////			g.drawLine(x, y, x, y);
////		}
//		for (int y = y1; y <= y2; y+=6) {
//			g.drawLine(x, y, x, y);
//		}
//	}
//	static protected final int HALF_SIZE = 4;
//	static protected final int SIZE = 9;
    /**
     * Returns the default cell renderer that is used to do the stamping of each
     * node.
     *
     * @return the tree cell renderer
     * @see
     * com.sun.java.swing.plaf.windows.WindowsTreeUI#createDefaultCellRenderer()
     */
    @Override
    protected TreeCellRenderer createDefaultCellRenderer() {
        return new WindowsTreeCellRenderer();
    }

//	/**
//	 * The minus sign button icon
//	 * <p>
//	 * <strong>Warning:</strong>
//	 * Serialized objects of this class will not be compatible with
//	 * future Swing releases.  The current serialization support is appropriate
//	 * for short term storage or RMI between applications running the same
//	 * version of Swing.  A future release of Swing will provide support for
//	 * long term persistence.
//	 */
//	public static class ExpandedIcon implements Icon, Serializable 
//	{
//
//		static public Icon createExpandedIcon()
//		{
//			return new ExpandedIcon();
//		}
//
////		Skin getSkin(Component c) 
////		{
////			XPStyle xp = XPStyle.getXP();
////			return (xp != null) ? xp.getSkin(c, Part.TVP_GLYPH) : null;
////		}
//
//		public void paintIcon(Component c, Graphics g, int x, int y) 
//		{
////			Skin skin = getSkin(c);
////			if (skin != null) {
////				skin.paintSkin(g, x, y, State.OPENED);
////				return;
////			}
//
//			Color     backgroundColor = c.getBackground();
//
//			if(backgroundColor != null)
//				g.setColor(backgroundColor);
//			else
//				g.setColor(Color.white);
//			
//			g.fillRect(x, y, SIZE-1, SIZE-1);
//			g.setColor(Color.gray);
//			g.drawRect(x, y, SIZE-1, SIZE-1);
//			g.setColor(Color.black);
//			g.drawLine(x + 2, y + HALF_SIZE, x + (SIZE - 3), y + HALF_SIZE);
//		}
//
//		public int getIconWidth() {
////			Skin skin = getSkin(null);
//			return //(skin != null) ? skin.getWidth() : 
//				SIZE;
//		}
//
//		public int getIconHeight()
//		{
////			Skin skin = getSkin(null);
//			return //(skin != null) ? skin.getHeight() : 
//				SIZE;
//		}
//	}
//	/**
//	 * The plus sign button icon
//	 * <p>
//	 * <strong>Warning:</strong>
//	 * Serialized objects of this class will not be compatible with
//	 * future Swing releases.  The current serialization support is appropriate
//	 * for short term storage or RMI between applications running the same
//	 * version of Swing.  A future release of Swing will provide support for
//	 * long term persistence.
//	 */
//	public static class CollapsedIcon extends ExpandedIcon {
//		static public Icon createCollapsedIcon() {
//			return new CollapsedIcon();
//		}
//
//		public void paintIcon(Component c, Graphics g, int x, int y) 
//		{
////			Skin skin = getSkin(c);
////			if (skin != null) 
////			{
////				skin.paintSkin(g, x, y, State.CLOSED);
////			} 
////			else 
//			{
//				super.paintIcon(c, g, x, y);
//				g.drawLine(x + HALF_SIZE, y + 2, x + HALF_SIZE, y + (SIZE - 3));
//			}
//		}
//	}
    /**
     * The Class WindowsTreeCellRenderer.
     *
     * @see
     * com.sun.java.swing.plaf.windows.WindowsTreeUI.WindowsTreeCellRenderer
     */
    public class WindowsTreeCellRenderer extends DefaultTreeCellRenderer {//目前没有定制内容，本来想让render绘制成圆角，但尝试后发现DefaultTreeCellRenderer类里
        //的代码设计欠佳，很难继承，要改的代码非常多，干脆作罢
//		/**
//		 * Configures the renderer based on the passed in components.
//		 * The value is set from messaging the tree with
//		 * <code>convertValueToText</code>, which ultimately invokes
//		 * <code>toString</code> on <code>value</code>.
//		 * The foreground color is set based on the selection and the icon
//		 * is set based on on leaf and expanded.
//		 */
//		public Component getTreeCellRendererComponent(JTree tree, Object value,
//				boolean sel,
//				boolean expanded,
//				boolean leaf, int row,
//				boolean hasFocus) {
//			super.getTreeCellRendererComponent(tree, value, sel,
//					expanded, leaf, row,
//					hasFocus);
//			// Windows displays the open icon when the tree item selected.
//			if (!tree.isEnabled()) {
//				setEnabled(false);
//				if (leaf) {
//					setDisabledIcon(getLeafIcon());
//				} else if (sel) {
//					setDisabledIcon(getOpenIcon());
//				} else {
//					setDisabledIcon(getClosedIcon());
//				}
//			}
//			else {
//				setEnabled(true);
//				if (leaf) {
//					setIcon(getLeafIcon());
//				} else if (sel) {
//					setIcon(getOpenIcon());
//				} else {
//					setIcon(getClosedIcon());
//				}
//			}
//			return this;
//		}
    }
}
