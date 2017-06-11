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
package org.jackhuang.hmcl.util.ui;

import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

/**
 *
 * @author huang
 */
public class JFontComboBox extends JComboBox<String> implements ItemListener {
    private static DefaultComboBoxModel<String> defaultModel = null;
    private int fontSize;
    
    protected static DefaultComboBoxModel<String> getDefaultModel() {
        if (defaultModel == null)
            defaultModel = new DefaultComboBoxModel<>(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        return defaultModel;
    }

    public JFontComboBox() {
        super();
        setRenderer(new JFontComboBoxCellRenderer());
        setModel(getDefaultModel());
        addItemListener(this);
        fontSize = getFont().getSize();
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        setFont(new Font(e.getItem().toString(), Font.PLAIN, fontSize));
    }
    
    public static class JFontComboBoxCellRenderer extends JLabel implements ListCellRenderer<String> {

        @Override
        public Component getListCellRendererComponent(JList list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            Font font = new Font(value, Font.PLAIN, 14);
            setEnabled(list.isEnabled());
            setText(font.getFontName());
            setFont(font);
            return this;
        }
        
    }
    
}
