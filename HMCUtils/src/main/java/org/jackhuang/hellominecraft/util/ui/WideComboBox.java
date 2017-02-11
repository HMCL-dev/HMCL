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
package org.jackhuang.hellominecraft.util.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import javax.swing.JComboBox;

/**
 * Make the popup menu of combo boxes wider.
 *
 * @author huangyuhui
 */
public class WideComboBox extends JComboBox<String> {

    public WideComboBox() {
    }

    private boolean layingOut = false;
    public int customzedMinimumWidth = 300;
    private FontMetrics fontMetrics = null;

    @Override
    public void doLayout() {
        try {
            layingOut = true;
            super.doLayout();
        } finally {
            layingOut = false;
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        fontMetrics = SwingUtils.getFontMetrics(this, g);
    }

    @Override
    public Dimension getSize() {
        Dimension dim = super.getSize();
        if (!layingOut && fontMetrics != null)
            for (int i = 0; i < getItemCount(); ++i)
                dim.width = Math.max(dim.width, SwingUtils.stringWidth(this, fontMetrics, getItemAt(i)) + 5);

        return dim;
    }
}
