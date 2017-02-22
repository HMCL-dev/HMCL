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
package org.jackhuang.hmcl.laf.separator;

import java.awt.Color;

import javax.swing.UIManager;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.utils.IconFactory;
import org.jackhuang.hmcl.laf.utils.UI;
import org.jackhuang.hmcl.laf.widget.border.BEDashedRoundRecBorder;

/**
 * 各种未归类的UI属性设置实现类. 本类中的各种属性日后可能会移入相应的各独立组件的UI包.
 *
 * @author Jack Jiang
 * @version 1.1
 */
public class __UI__ extends UI {

    private static final IconFactory ICON = new IconFactory("option_pane");

    public static void uiImpl() {
        put("control", BeautyEyeLNFHelper.commonBackgroundColor);
        put("Separator.foreground", new Color(180, 180, 180));
        put("ToolTip.foreground", BeautyEyeLNFHelper.commonForegroundColor);

        put("Separator.background", Color.white);
        put("Panel.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("Panel.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("PanelUI", BEPanelUI.class);

        put("Label.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        //put("Label.background", BeautyEyeLNFHelper.commonBackgroundColor);

        put("ColorChooser.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("ColorChooser.background", BeautyEyeLNFHelper.commonBackgroundColor);
        put("ColorChooser.swatchesDefaultRecentColor", BeautyEyeLNFHelper.commonBackgroundColor);

        putColor("TitledBorder.titleColor", 58, 135, 173); // TitleBorder的标题颜色
        // TitledBorder的默认border实现（windows LNF中默认是圆色灰色实线距形）
        put("TitledBorder.border", new BEDashedRoundRecBorder(BeautyEyeLNFHelper.commonFocusedBorderColor));

//		UIManager.put("OptionPaneUI",org.jb2011.lnf.windows2.ch3.NLOptionPaneUI.class.getName());
        //** Ui里的实现逻辑：此属性为true时将导致JOptionPane里的各按钮按BasicOptionPaneUI里设定的Insets进行
        //** UI展现：当按钮数<=2时使用的Insets=new Instes(2,8,2,8)，否则使用new Instes(2,4,2,4)，
        //** 这样的逻辑下，BeautyEye L&F实现里会使得按钮高度缩小而不好看，所以要关闭此属性
        put("OptionPane.setButtonMargin", false);
        put("OptionPane.foreground", BeautyEyeLNFHelper.commonForegroundColor);
        put("OptionPane.background", BeautyEyeLNFHelper.commonBackgroundColor);
        UIManager.put("OptionPane.questionIcon", ICON.get("question"));
        UIManager.put("OptionPane.warningIcon", ICON.get("warn"));
        UIManager.put("OptionPane.informationIcon", ICON.get("info"));
        UIManager.put("OptionPane.errorIcon", ICON.get("error"));

        put("SeparatorUI", BESeparatorUI.class);
    }
}
