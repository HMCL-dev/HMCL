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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import javax.swing.border.AbstractBorder;
import org.jackhuang.hellominecraft.util.Pair;

public class DropShadowBorder extends AbstractBorder {

    private Color color;
    private int thickness = 1;
    private Insets insets = null;
    RenderingHints hints;

    public DropShadowBorder(Color color, int thickness) {
        this.thickness = thickness;
        this.color = color;
        this.hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        this.insets = new Insets(thickness * 4, thickness * 4, thickness * 4, thickness * 4);
    }

    public void setColor(Color c) {
        color = c;
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return this.insets;
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        return getBorderInsets(c);
    }

    private static final HashMap<Pair<Integer, Integer>, BufferedImage> CACHE = new HashMap<>();

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Pair<Integer, Integer> pair = new Pair<>(width, height);
        BufferedImage list;
        int border = this.thickness * 4;
        if (CACHE.containsKey(pair)) list = CACHE.get(pair);
        else {
            BufferedImage shadow = new BufferedImage(width, height, 2);

            Graphics2D g2 = shadow.createGraphics();
            g2.setRenderingHints(this.hints);
            Composite oldComposite = g2.getComposite();
            AlphaComposite composite = AlphaComposite.getInstance(1, 0.0F);
            g2.setComposite(composite);
            g2.setColor(new Color(0, 0, 0, 0));
            g2.fillRect(0, 0, width, height);
            g2.setComposite(oldComposite);
            g2.setColor(this.color);
            g2.fillRect(border, border + border / 6, width - border * 2, height - border * 2);
            g2.dispose();

            FastBlurFilter blur = new FastBlurFilter(this.thickness);
            shadow = blur.filter(shadow, null);
            shadow = blur.filter(shadow, null);
            shadow = blur.filter(shadow, null);
            shadow = blur.filter(shadow, null);
            CACHE.put(pair, list = shadow);
        }
        g.drawImage(list, 0, 0, width, height, null);
    }
}
