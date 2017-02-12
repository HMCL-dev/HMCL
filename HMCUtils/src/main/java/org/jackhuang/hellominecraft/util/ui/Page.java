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

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 *
 * @author huangyuhui
 */
public class Page extends JPanel implements Selectable {

    boolean selected = false;

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void onSelect() {
        if (!selected)
            animate();
        selected = true;
    }

    @Override
    public void onLeave() {
        selected = false;
    }

    boolean created = false;

    @Override
    public void onCreate() {
        created = true;
    }

    @Override
    public boolean isCreated() {
        return created;
    }

    // -------------------
    //    Animation
    // -------------------
    private static final int ANIMATION_LENGTH = 10;

    public Page() {
        timer = new Timer(1, (e) -> {
            SwingUtilities.invokeLater(() -> {
                Page.this.repaint();
                offsetX += 0.15;
                if (offsetX >= ANIMATION_LENGTH) {
                    timer.stop();
                    Page.this.repaint();
                }
            });
        });
    }

    BufferedImage cache = null;

    @Override
    public void paint(Graphics g) {
        if (!(g instanceof Graphics2D)) {
            super.paint(g);
            return;
        }
        double pgs = 1 - Math.sin(Math.PI / 2 / ANIMATION_LENGTH * offsetX);
        if (Math.abs(ANIMATION_LENGTH - offsetX) < 0.1) {
            super.paint(g);
            return;
        }

        if (offsetX == 0) {
            cache = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = cache.createGraphics();
            if (isOpaque()) {
                g2d.setColor(getBackground());
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paint(g2d);
            g2d.dispose();
        }
        if (pgs > 1)
            pgs = 1;
        if (pgs < 0)
            pgs = 0;
        Graphics2D gg = (Graphics2D) g;
        gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, (float) (1 - pgs)));
        g.drawImage(cache, (int) (pgs * 50), 0, this);
    }

    double offsetX = ANIMATION_LENGTH;

    Timer timer;

    protected boolean animationEnabled = true;

    public void animate() {
        if (animationEnabled) {
            offsetX = 0;
            timer.start();
        }
    }
}
