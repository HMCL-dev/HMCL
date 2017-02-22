/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.laf;

import java.util.HashMap;
import org.jackhuang.hmcl.api.ui.Theme;
import org.jackhuang.hmcl.util.C;

/**
 *
 * @author huangyuhui
 */
public final class LAFTheme {
    public static final Theme BLUE = new Theme("blue", C.i18n("color.blue"), new HashMap<String, String>() {
        {
            put("Customized.TabbedPaneTab.selected_foreground", "#106CA3");
            put("Customized.ComboBox.selected_background", "#A0D8F0");
            put("Customized.MainFrame.background", "#106CA3");
            put("TabbedPane.selectedForeground", "#106CA3");
            put("TextField.focused", "#106CA3");
            put("MenuItem.selectionBackground", "#106CA3");
            put("Customized.MainFrame.selected_background", "#0C5E91");
            put("Customized.MainFrame.background_image", "background.jpg");
        }
    });
    public static final Theme GREEN = new Theme("green", C.i18n("color.green"), new HashMap<String, String>() {
        {
            put("Customized.TabbedPaneTab.selected_foreground", "#1ABC9C");
            put("Customized.ComboBox.selected_background", "#1ABC9C");
            put("Customized.MainFrame.background", "#1ABC9C");
            put("TabbedPane.selectedForeground", "#1ABC9C");
            put("TextField.focused", "#1ABC9C");
            put("MenuItem.selectionBackground", "#1ABC9C");
            put("Customized.MainFrame.selected_background", "#16A085");
            put("Customized.MainFrame.background_image", "background.jpg");
        }
    });
    public static final Theme PURPLE = new Theme("purple", C.i18n("color.purple"), new HashMap<String, String>() {
        {
            put("Customized.TabbedPaneTab.selected_foreground", "#9B59B6");
            put("Customized.ComboBox.selected_background", "#9B59B6");
            put("Customized.MainFrame.background", "#9B59B6");
            put("TabbedPane.selectedForeground", "#9B59B6");
            put("TextField.focused", "#9B59B6");
            put("MenuItem.selectionBackground", "#9B59B6");
            put("Customized.MainFrame.selected_background", "#8E44AD");
            put("Customized.MainFrame.background_image", "background.jpg");
        }
    });
    public static final Theme DARKER_BLUE = new Theme("darker_blue", C.i18n("color.dark_blue"), new HashMap<String, String>() {
        {
            put("Customized.TabbedPaneTab.selected_foreground", "#34495E");
            put("Customized.ComboBox.selected_background", "#34495E");
            put("Customized.MainFrame.background", "#34495E");
            put("TabbedPane.selectedForeground", "#34495E");
            put("TextField.focused", "#34495E");
            put("MenuItem.selectionBackground", "#34495E");
            put("Customized.MainFrame.selected_background", "#2C3E50");
            put("Customized.MainFrame.background_image", "background.jpg");
        }
    });
    public static final Theme ORANGE = new Theme("orange", C.i18n("color.orange"), new HashMap<String, String>() {
        {
            put("Customized.TabbedPaneTab.selected_foreground", "#E67E22");
            put("Customized.ComboBox.selected_background", "#F39C12");
            put("Customized.MainFrame.background", "#E67E22");
            put("TabbedPane.selectedForeground", "#E67E22");
            put("TextField.focused", "#E67E22");
            put("MenuItem.selectionBackground", "#E67E22");
            put("Customized.MainFrame.selected_background", "#D35400");
            put("Customized.MainFrame.background_image", "background.jpg");
        }
    });
    public static final Theme RED = new Theme("red", C.i18n("color.red"), new HashMap<String, String>() {
        {
            put("Customized.TabbedPaneTab.selected_foreground", "#E74C3C");
            put("Customized.ComboBox.selected_background", "#E74C3C");
            put("Customized.MainFrame.background", "#E74C3C");
            put("TabbedPane.selectedForeground", "#E74C3C");
            put("TextField.focused", "#E74C3C");
            put("MenuItem.selectionBackground", "#E74C3C");
            put("Customized.MainFrame.selected_background", "#C0392B");
            put("Customized.MainFrame.background_image", "background.jpg");
        }
    });
    public static final Theme[] THEMES = new Theme[] {BLUE, GREEN, PURPLE, DARKER_BLUE, ORANGE, RED};
}
