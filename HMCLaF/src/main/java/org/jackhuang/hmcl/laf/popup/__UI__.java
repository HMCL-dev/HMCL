/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * __UI__.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.popup;

import javax.swing.PopupFactory;

public class __UI__ {

    public static PopupFactory popupFactoryDIY = new TranslucentPopupFactory();

    public static void uiImpl() {
        PopupFactory.setSharedInstance(popupFactoryDIY);
    }
}
