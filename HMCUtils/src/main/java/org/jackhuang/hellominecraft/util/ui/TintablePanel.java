/*
 * Hello Minecraft!.
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

/**
 *
 * @author huangyuhui
 */
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class TintablePanel extends JPanel {

    private Color tintColor;
    private boolean tintActive;
    private JLabel overIcon = null;

    public TintablePanel() {
        this.tintColor = new Color(0, 0, 0, 0);
    }

    public Color getTintColor() {
        return this.tintColor;
    }

    public void setTintColor(Color color) {
        this.tintColor = color;
    }

    public void setOverIcon(ImageIcon image) {
        if (this.overIcon != null)
            remove(this.overIcon);

        this.overIcon = new JLabel(image);
        this.overIcon.setVisible(false);
        add(this.overIcon);
        revalidate();
    }

    public boolean isTintActive() {
        return this.tintActive;
    }

    public void setTintActive(boolean tintActive) {
        this.tintActive = tintActive;

        if (this.overIcon != null) {
            this.overIcon.setVisible(tintActive);
            EventQueue.invokeLater(TintablePanel.this::revalidate);
        }
        EventQueue.invokeLater(TintablePanel.this::repaint);
    }

    @Override
    public void doLayout() {
        super.doLayout();

        if (this.overIcon != null) {
            int width = this.overIcon.getIcon().getIconWidth();
            int height = this.overIcon.getIcon().getIconHeight();
            this.overIcon.setBounds(getWidth() / 2 - width / 2, getHeight() / 2 - height / 2, width, height);
        }
    }

    @Override
    public void paint(Graphics graphics) {
        super.paint(graphics);

        if (this.tintActive) {
            graphics.setColor(getTintColor());
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
