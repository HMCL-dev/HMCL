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
package org.jackhuang.hmcl.laf.radio$cb_btn;

import java.awt.Component;
import java.awt.Graphics;
import java.io.Serializable;

import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.IconFactory;
import org.jackhuang.hmcl.laf.utils.UI;

public class __UI__ extends UI {
    private static final IconFactory RADIO = new IconFactory("radio");
    private static final IconFactory CHECK = new IconFactory("check");

    public static void uiImpl() {

        putInsets("CheckBox.margin", 4, 3, 4, 3);
        put("CheckBox.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("CheckBox.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("CheckBoxUI", BECheckBoxUI.class);

        putInsets("RadioButton.margin", 4, 3, 4, 3); // 2, 2, 2, 2
        put("RadioButton.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("RadioButton.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("RadioButtonUI", BERadioButtonUI.class);

        UIManager.put("RadioButton.icon", new IconFactoryIcon(RADIO));
        UIManager.put("CheckBox.icon", new IconFactoryIcon(CHECK));

        // 衬距设定
        putInsets("RadioButton.margin", 1, 1, 1, 1); // 2, 2, 2, 2
        putInsets("CheckBox.margin", 1, 1, 1, 1); // 2, 2, 2, 2
    }

    /**
     * @see com.sun.java.swing.plaf.windows.WindowsIconFactory
     * @see com.sun.java.swing.plaf.windows.WindowsIconFactory.CheckBoxIcon
     * @see com.sun.java.swing.plaf.windows.WindowsIconFactory.RadioBoxIcon
     */
    private static class IconFactoryIcon implements Icon, Serializable {
        IconFactory icon;

        public IconFactoryIcon(IconFactory icon) {
            this.icon = icon;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            JToggleButton cb = (JToggleButton) c;
            ButtonModel model = cb.getModel();

            g.drawImage(icon.get(model.isSelected() ? "" : "unchecked",
                    model.isEnabled() ? (model.isPressed() ? "pressed" : (model.isRollover() ? "over" : "normal")) : "disabled").getImage(),
                    x, y, null);
        }

        @Override
        public int getIconWidth() {
            return 24;
        }

        @Override
        public int getIconHeight() {
            return 24;
        }
    }
}
