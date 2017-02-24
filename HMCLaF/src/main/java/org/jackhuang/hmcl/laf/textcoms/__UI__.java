/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * __UI__.java at 2015-2-1 20:25:38, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.textcoms;

import java.awt.Color;

import javax.swing.UIManager;
import javax.swing.plaf.InsetsUIResource;
import org.jackhuang.hmcl.util.ui.GraphicsUtils;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.Icon9Factory;
import org.jackhuang.hmcl.laf.utils.UI;
import org.jackhuang.hmcl.laf.widget.border.BERoundBorder;

public class __UI__ extends UI {
    static final Color BORDER_NORMAL = GraphicsUtils.getWebColor("#999999");
    static final Color BORDER_DISABLED = GraphicsUtils.getWebColor("#E3E3E3");
    static final Color BORDER_OVER = GraphicsUtils.getWebColor("#666666");
    
    static Color border_focused() {
        return (Color) UIManager.get("TextField.focused");
    }

    public static final Icon9Factory ICON_9 = new Icon9Factory("text");

    public static void uiImpl() {
        final InsetsUIResource iuir = new InsetsUIResource(4, 7, 4, 7);
        final BERoundBorder berb = new BERoundBorder().setLineColor(new Color(0, 0, 0, 0));

        //使用全透明色绘边框（用什么边框无所谓，关键是读取它的margin并透明），目的就是要让它的背景显现出来（NinePatch图实现）
        UIManager.put("TextField.margin", iuir);
        put("TextField.border", berb);
        put("TextField.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        put("TextField.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("TextField.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("TextFieldUI", BETextFieldUI.class);

        UIManager.put("FormattedTextField.margin", iuir);
        put("FormattedTextField.border", new BERoundBorder(1).setArcWidth(10).setLineColor(new Color(0, 0, 0, 0)));
        put("FormattedTextField.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("FormattedTextField.inactiveBackground", BeautyEyeLNFHelper.commonBackgroundColor);
        put("FormattedTextField.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        put("FormattedTextField.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("FormattedTextFieldUI", BEFormattedTextFieldUI.class);

        UIManager.put("PasswordField.margin", iuir);
        put("PasswordField.border", berb);
        put("PasswordField.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        put("PasswordField.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("PasswordField.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("PasswordFieldUI", BEPasswordFieldUI.class);

        UIManager.put("TextArea.margin", iuir);
        put("TextArea.border", berb);
        put("TextArea.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        put("TextArea.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("TextArea.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("TextAreaUI", BETextAreaUI.class);

        put("TextPane.border", berb);
        put("TextPane.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        put("TextPane.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("TextPane.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("TextPaneUI", BETextPaneUI.class);

        put("EditorPane.border", berb);
        put("EditorPane.selectionForeground", BeautyEyeLNFHelper.commonSelectionForegroundColor);
        put("EditorPane.selectionBackground", BeautyEyeLNFHelper.commonSelectionBackgroundColor);
        put("EditorPane.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("EditorPaneUI", BEEditorPaneUI.class);
    }

    public interface BgSwitchable {

        void switchBgToNormal();

        void switchBgToFocused();
        
        void switchBgToOver();
    }
}
