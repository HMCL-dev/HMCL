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
package org.jackhuang.hellominecraft.lookandfeel;

import java.util.HashMap;
import java.util.Map;
import org.jackhuang.hellominecraft.C;

/**
 *
 * @author huangyuhui
 */
public enum Theme {
    BLUE(C.i18n("color.blue"), new HashMap<String, String>() {
         {
             put("Customized.TabbedPaneTab.selected_foreground", "#106CA3");
             put("Customized.ComboBox.selected_background", "#A0D8F0");
             put("Customized.MainFrame.background", "#106CA3");
             put("Customized.MainFrame.selected_background", "#0C5E91");
         }
     }),
    GREEN(C.i18n("color.green"), new HashMap<String, String>() {
          {
              put("Customized.TabbedPaneTab.selected_foreground", "#1ABC9C");
              put("Customized.ComboBox.selected_background", "#1ABC9C");
              put("Customized.MainFrame.background", "#1ABC9C");
              put("Customized.MainFrame.selected_background", "#16A085");
          }
      }),
    PURPLE(C.i18n("color.purple"), new HashMap<String, String>() {
           {
               put("Customized.TabbedPaneTab.selected_foreground", "#9B59B6");
               put("Customized.ComboBox.selected_background", "#9B59B6");
               put("Customized.MainFrame.background", "#9B59B6");
               put("Customized.MainFrame.selected_background", "#8E44AD");
           }
       }),
    DARKER_BLUE(C.i18n("color.dark_blue"), new HashMap<String, String>() {
                {
                    put("Customized.TabbedPaneTab.selected_foreground", "#34495E");
                    put("Customized.ComboBox.selected_background", "#34495E");
                    put("Customized.MainFrame.background", "#34495E");
                    put("Customized.MainFrame.selected_background", "#2C3E50");
                }
            }),
    ORANGE(C.i18n("color.orange"), new HashMap<String, String>() {
           {
               put("Customized.TabbedPaneTab.selected_foreground", "#E67E22");
               put("Customized.ComboBox.selected_background", "#F39C12");
               put("Customized.MainFrame.background", "#E67E22");
               put("Customized.MainFrame.selected_background", "#D35400");
           }
       }),
    RED(C.i18n("color.red"), new HashMap<String, String>() {
        {
            put("Customized.TabbedPaneTab.selected_foreground", "#E74C3C");
            put("Customized.ComboBox.selected_background", "#E74C3C");
            put("Customized.MainFrame.background", "#E74C3C");
            put("Customized.MainFrame.selected_background", "#C0392B");
        }
    });

    public final String localizedName;
    public final Map<String, String> settings;

    private Theme(String localizedName, Map<String, String> settings) {
        if (settings == null)
            throw new NullPointerException("Theme settings map should not be null.");
        this.localizedName = localizedName;
        this.settings = settings;
    }
}
