/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEMenuUI.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.menu;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;
import javax.swing.UIManager;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicMenuUI;

import org.jackhuang.hmcl.laf.BEUtils;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;
import org.jackhuang.hmcl.laf.utils.WinUtils;

/**
 * JMenuU的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @see com.sun.java.swing.plaf.windows.WindowsMenuUI
 */
public class BEMenuUI extends BasicMenuUI {
    
    //JMenuBar的顶层菜单项的装饰底线高度
    public final static int DECORATED_UNDERLINE_HEIGHT = 2;// TODO 可以提炼成Ui属性哦
    
    //顶层菜单项被选中的颜色
    public final static Color MENU_SELECTED_UNDERLINE_COLOR = new Color(37, 147, 217);// TODO 可以提炼成Ui属性哦
    
    //顶层菜单项未被选中的颜色
    public final static Color MENU_UNSELECTED_UNDERLINE_COLOR = new Color(226, 230, 232);// TODO 可以提炼成Ui属性哦

    protected Integer menuBarHeight;

    protected boolean hotTrackingOn;

    public static ComponentUI createUI(JComponent x) {
        return new BEMenuUI();
    }

    /**
     * {@inheritDoc}
     *
     * @see com.sun.java.swing.plaf.windows.WindowsMenuUI#installDefaults()
     */
    @Override
    protected void installDefaults() {
        super.installDefaults();
//    	if (!WindowsLookAndFeel.isClassicWindows()) 
        {
            menuItem.setRolloverEnabled(true);
        }

        menuBarHeight = (Integer) UIManager.getInt("MenuBar.height");
        Object obj = UIManager.get("MenuBar.rolloverEnabled");
        hotTrackingOn = (obj instanceof Boolean) ? (Boolean) obj : true;
    }

    /**
     * Draws the background of the menu.
     *
     * @param g the g
     * @param menuItem the menu item
     * @param bgColor the bg color
     * @since 1.4
     * 
     * @see com.sun.java.swing.plaf.windows.WindowsMenuUI#paintBackground(java.awt.Graphics, javax.swing.JMenuItem, java.awt.Color)
     */
    @Override
    protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
        JMenu menu = (JMenu) menuItem;
        ButtonModel model = menu.getModel();

        Color oldColor = g.getColor();
        int menuWidth = menu.getWidth();
        int menuHeight = menu.getHeight();

//    	UIDefaults table = UIManager.getLookAndFeelDefaults();
//    	Color highlight = table.getColor("controlLtHighlight");
//    	Color shadow = table.getColor("controlShadow");
        g.setColor(menu.getBackground());
        g.fillRect(0, 0, menuWidth, menuHeight);

        //给JMEnuBar的顶层菜单项中画一个灰色底线（看起来美观）
        //位于菜单项里的MenuItem则不需要画这个底线（要不然不好看哦）
        if (menu.isTopLevelMenu()) {
            //未选中的装饰底线
            //只要放在MenuBar最顶层的JMenu才需要画哦（从而与底色融为一体）
            g.setColor(MENU_UNSELECTED_UNDERLINE_COLOR);
            g.fillRect(0, menuHeight - DECORATED_UNDERLINE_HEIGHT, menuWidth, menuHeight);
        }

        //* >注意：官方是判断menu.isOpaque()==false时才进入此if-else分支，
        //* >其实以下代码的意义是：当是顶级Menu（即直接放在MenuBar上的那层）时
        //* >才需要调用以下分支，而在win7下，以下分支是不会被调用的（WindowsLookAndfeel.initVistaComponentDefaults中
        //* >处理了如果是vista则"MenuItem.opaque", "Menu.opaque", "CheckBoxMenuItem.opaque", "RadioButtonMenuItem.opaque"
        //* >默认都设置成了false，即默认透明（官方逻辑可能是透明体就意味着是顶级menu了）！）
        //* >，那么win7下将会出现错误的表现：无法区分是不是顶级menu也就无法区别paint出不同的样子了.
        //* >那么官方为何没收到bug报告呢？我分析可能还没有人面对到BeautyEye这样需要对Menu进行较复杂样式需求的场景
        //* >，即把顶级menu和内层menu分的很清。
        //* >目前BeautyEye的实现即menu.isTopLevelMenu()==true时即进入本分支
        //* >才是比较合理的，当然，有待实践检验，或许官方有其它考虑。 -- commet by Jack Jiang 2012-09-14
        if (menu.isTopLevelMenu())//menu.isOpaque())
        {
            //JMebuBar的顶层菜单项被选中或鼠标停留时
            if (model.isArmed() || model.isSelected()) {
                Color c = MENU_SELECTED_UNDERLINE_COLOR;
                g.setColor(c);

                //先填充一个装饰3角形（用于在UI上提示用户它被选中了）
                //       x2,y2
                //
                //x1,y1          x3,y3
                int tW = 7, tH = 3;
                int x1 = menuWidth / 2 - tW / 2;
                int y1 = menuHeight - DECORATED_UNDERLINE_HEIGHT;
                int x2 = menuWidth / 2;
                int y2 = menuHeight - DECORATED_UNDERLINE_HEIGHT - tH;
                int x3 = menuWidth / 2 + tW / 2;
                int y3 = menuHeight - DECORATED_UNDERLINE_HEIGHT;
                //反走样
                BEUtils.setAntiAliasing((Graphics2D) g, true);
                BEUtils.fillTriangle(g, x1, y1, x2, y2, x3, y3, c);
                BEUtils.setAntiAliasing((Graphics2D) g, false);

                //再填充一个底线（用于装饰）
                g.fillRect(0, menuHeight - DECORATED_UNDERLINE_HEIGHT, menuWidth, DECORATED_UNDERLINE_HEIGHT);//menuHeight);
            } else if (model.isRollover() && model.isEnabled()) {
                // Only paint rollover if no other menu on menubar is selected
                boolean otherMenuSelected = false;
                MenuElement[] menus = ((JMenuBar) menu.getParent())
                        .getSubElements();
                for (MenuElement menu1 : menus)
                    if (((JMenuItem) menu1).isSelected()) {
                        otherMenuSelected = true;
                        break;
                    }

                if (!otherMenuSelected) {
                    g.setColor(MENU_SELECTED_UNDERLINE_COLOR);//selectionBackground);// Uses protected
//					g.fillRect(0, 0, menuWidth, menuHeight);
                    //再填充一个底线（用于装饰）
                    g.fillRect(0, menuHeight - DECORATED_UNDERLINE_HEIGHT, menuWidth, DECORATED_UNDERLINE_HEIGHT);//menuHeight);
                }
            }
        } // add by Jack Jiang，JMebuBar的内层父级菜单项的样式绘制
        else if (model.isArmed() || (menuItem instanceof JMenu
                && model.isSelected())) {
            //用NinePatch图来填充
            drawSelectedBackground(g, 0, 0, menuWidth, menuHeight);
        }
        g.setColor(oldColor);
    }

    public static void drawSelectedBackground(Graphics g, int x, int y, int w, int h) {
            g.setColor(UIManager.getColor("MenuItem.selectionBackground"));
            g.fillRect(0, 0, w, h);
    }
    
    /**
     * Method which renders the text of the current menu item.
     * <p>
     * @param g Graphics context
     * @param menuItem Current menu item to render
     * @param textRect Bounding rectangle to render the text.
     * @param text String to render
     * @since 1.4
     * 
     * @see com.sun.java.swing.plaf.windows.WindowsMenuUI#paintText(java.awt.Graphics, javax.swing.JMenuItem, java.awt.Rectangle, java.lang.String) 
     */
    @Override
    protected void paintText(Graphics g, JMenuItem menuItem,
            Rectangle textRect, String text) {
        //================= commet by Jack Jiang START
//    	if (WindowsMenuItemUI.isVistaPainting()) {
//    		WindowsMenuItemUI.paintText(accessor, g, menuItem, textRect, text);
//    		return;
//    	}
        //================= commet by Jack Jiang END
        JMenu menu = (JMenu) menuItem;
        ButtonModel model = menuItem.getModel();
        Color oldColor = g.getColor();

        // Only paint rollover if no other menu on menubar is selected
        boolean paintRollover = model.isRollover();
        if (paintRollover && menu.isTopLevelMenu()) {
            MenuElement[] menus = ((JMenuBar) menu.getParent()).getSubElements();
            for (MenuElement menu1 : menus)
                if (((JMenuItem) menu1).isSelected()) {
                    paintRollover = false;
                    break;
                }
        }

        if ((model.isSelected()
                && ( //    							WindowsLookAndFeel.isClassicWindows() ||
                !menu.isTopLevelMenu()))
                || ( //    					BEXPStyle.getXP() != null && 
                (paintRollover || model.isArmed() || model.isSelected())))
            g.setColor(selectionForeground); // Uses protected field.

        //================= add by Jack Jiang START
        //特殊处理顶级菜单项（就是直接放在JMenuBar上的那一层），使之在被选中或rover等状态时保持黑色（或其它颜色）
        //，目的是为了配合整个菜单项的L&F效果，并没有过多的用途，此颜色可提取作为UIManager的自定义属性哦
        if (menu.isTopLevelMenu())
            g.setColor(new Color(35, 35, 35));//用MaxOS X的经典黑
        //================= add by Jack Jiang END

//    	WindowsGraphicsUtils.paintText(g, menuItem, textRect, text, 0);
        WinUtils.paintText(g, menuItem, textRect, text, 0);

        g.setColor(oldColor);
    }

    /**
     * {@inheritDoc}
     *
     * @see
     * com.sun.java.swing.plaf.windows.WindowsMenuUI#createMouseInputListener(javax.swing.JComponent)
     */
    @Override
    protected MouseInputListener createMouseInputListener(JComponent c) {
        return new BEMouseInputHandler();
    }

    /**
     * This class implements a mouse handler that sets the rollover flag to true
     * when the mouse enters the menu and false when it exits.
     *
     * @since 1.4
     * @see com.sun.java.swing.plaf.windows.WindowsMenuUI.MouseInputHandler
     */
    protected class BEMouseInputHandler extends BasicMenuUI.MouseInputHandler {

        @Override
        public void mouseEntered(MouseEvent evt) {
            super.mouseEntered(evt);

            JMenu menu = (JMenu) evt.getSource();
            if (hotTrackingOn && menu.isTopLevelMenu() && menu.isRolloverEnabled()) {
                menu.getModel().setRollover(true);
                menuItem.repaint();
            }
        }

        @Override
        public void mouseExited(MouseEvent evt) {
            super.mouseExited(evt);

            JMenu menu = (JMenu) evt.getSource();
            ButtonModel model = menu.getModel();
            if (menu.isRolloverEnabled()) {
                model.setRollover(false);
                menuItem.repaint();
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see
     * com.sun.java.swing.plaf.windows.WindowsMenuUI#getPreferredMenuItemSize(javax.swing.JComponent,
     * javax.swing.Icon, javax.swing.Icon, int)
     */
    @Override
    protected Dimension getPreferredMenuItemSize(JComponent c,
            Icon checkIcon,
            Icon arrowIcon,
            int defaultTextIconGap) {

        Dimension d = super.getPreferredMenuItemSize(c, checkIcon, arrowIcon,
                defaultTextIconGap);

        // Note: When toolbar containers (rebars) are implemented, only do
        // this if the JMenuBar is not in a rebar (i.e. ignore the desktop
        // property win.menu.height if in a rebar.)
        if (c instanceof JMenu && ((JMenu) c).isTopLevelMenu()
                && menuBarHeight != null && d.height < menuBarHeight)

            d.height = menuBarHeight;

        return d;
    }
}
