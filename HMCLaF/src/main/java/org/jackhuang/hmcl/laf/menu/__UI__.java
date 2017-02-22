/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * __UI__.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.menu;

import java.awt.Color;

import javax.swing.UIManager;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {

    public static void uiImpl() {
        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> margin和border设置 START
//		UIManager.put("MenuBar.border",new org.jb2011.lnf.windows2.ch10.EWindowsMenuBarUI.MenuBarBorder(Color.red,Color.BLUE));
        //去掉菜单条下方的border（默认是一个2个像素高的横线，参见WindowsMenuBarUI.MenuBarBorder）
        putBorder("MenuBar.border");

        //提示：margin与border并存设置情况容易产生混知，其实官方实现是：当设置margin时，则Border就使用
        //marginBorder，该Border就是用的这个margin来作它的Insets的
        //BueatyEye LNF中推荐实践是：抛弃margin的概念（省的混淆），直接使用border（在其上直接给于insets）即可
        putInsets("CheckBoxMenuItem.margin", 0, 0, 0, 0);
        putInsets("RadioButtonMenuItem.margin", 0, 0, 0, 0);//iuir);
        putInsets("Menu.margin", 0, 0, 0, 0);//windows lnf中默认是2，2，2，2
        putInsets("MenuItem.margin", 0, 0, 0, 0);//windows lnf中默认是2，2，2，2
//	putInsets("MenuItem.margin", 10, 2, 10, 2);//top=2,left=2,bottom=2,right=2

        //请注意：上面的margin已经全设为0了哦
        putBorder("Menu.border", 4, 3, 5, 3);//javax.swing.plaf.basic.BasicBorders.MarginBorder;
        putBorder("MenuItem.border", 4, 3, 5, 3);//
        putBorder("CheckBoxMenuItem.border", 4, 3, 5, 3);//javax.swing.plaf.basic.BasicBorders.MarginBorder;
        putBorder("RadioButtonMenuItem.border", 4, 3, 5, 3);//		
//	putBorder("PopupMenu.border", 20, 10, 20, 10); //	
        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> margin和border设置 END

        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> 颜色设置 START
        put("MenuBar.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("RadioButtonMenuItem.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("Menu.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("PopupMenu.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("CheckBoxMenuItem.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("MenuItem.foreground", BeautyEyeLNFHelper.commonForegroundColor);

        put("MenuBar.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("Menu.background", new Color(255, 255, 255, 0));
        put("PopupMenu.background", new Color(255, 255, 255, 0));

        put("RadioButtonMenuItem.disabledForeground", BeautyEyeLNFHelper.commonDisabledForegroundColor);
        put("MenuItem.disabledForeground", BeautyEyeLNFHelper.commonDisabledForegroundColor);

        put("Menu.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        put("MenuItem.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        put("CheckBoxMenuItem.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        put("RadioButtonMenuItem.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);

        put("Menu.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("MenuItem.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("CheckBoxMenuItem.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("RadioButtonMenuItem.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> 颜色设置 END

        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> 杂项设置 START
        //本值意味着弹出菜单X轴方向上的偏移量，因BE LNF中加了边框，所以要负偏移以便得弹出菜单主体能与菜单项对齐好看一些
        put("Menu.menuPopupOffsetX", -3);//win lnf中默认值是0
        //本值意味着弹出菜单Y轴方向上的偏移量，因BE LNF中加了边框，所以要负偏移以便得弹出菜单主体能与菜单项对齐好看一些
        put("Menu.menuPopupOffsetY", 2);///win lnf默认值是0
        //本值意味着弹出子菜单X轴方向上的偏移量，因BE LNF中加了边框，所以要负偏移以便得弹出菜单主体能与菜单项对齐好看一些
        put("Menu.submenuPopupOffsetX", -2);///win lnf默认值是-4
        //本值意味着弹出子菜单Y轴方向上的偏移量，因BE LNF中加了边框，所以要负偏移以便得弹出菜单主体能与菜单项对齐好看一些
        put("Menu.submenuPopupOffsetY", -5);///win lnf默认值是-3

        //多选按钮式的菜单项选中与否的图标实现设定
        UIManager.put("CheckBoxMenuItem.checkIcon", new BECheckBoxMenuItemUI.CheckBoxMenuItemIcon());
        //单选按钮式的菜单项选中与否的图标实现设定
        UIManager.put("RadioButtonMenuItem.checkIcon", new BERadioButtonMenuItemUI.RadioButtonMenuItemIcon());

        //加高菜单条，提升视觉体验
        put("MenuBar.height", 30);//default value is 19

        //此属性true时表明禁用的菜单项将不能被rover，否则有rover效果（BE LNF中
        //因禁用状态rover时的字体色影响视觉效果，所以干脆禁用之，逻辑上也很合理）
        put("MenuItem.disabledAreNavigable", false);// windows lnf中默认是true
        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> 杂项设置 END

        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI实现类设置 START
        put("MenuBarUI", BEMenuBarUI.class);
        put("MenuUI", BEMenuUI.class);
        put("MenuItemUI", BEMenuItemUI.class);
        put("RadioButtonMenuItemUI", BERadioButtonMenuItemUI.class);
        put("CheckBoxMenuItemUI", BECheckBoxMenuItemUI.class);
        put("PopupMenuSeparatorUI", BEPopupMenuSeparatorUI.class);
        put("PopupMenuUI", BEPopupMenuUI.class);
        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI实现类设置 END
    }
}
