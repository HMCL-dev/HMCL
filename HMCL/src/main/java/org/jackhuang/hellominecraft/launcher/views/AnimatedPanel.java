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
package org.jackhuang.hellominecraft.launcher.views;

import java.awt.AlphaComposite;
import java.awt.Color;
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
public class AnimatedPanel extends JPanel {

    private static final int ANIMATION_LENGTH = 10;

    public AnimatedPanel() {
        timer = new Timer(1, (e) -> {
                          SwingUtilities.invokeLater(() -> {
                              AnimatedPanel.this.repaint();
                              offsetX += 0.15;
                              if (offsetX >= ANIMATION_LENGTH) {
                                  timer.stop();
                              AnimatedPanel.this.repaint();
                              }
                          });
                      });
    }

    double offsetX = ANIMATION_LENGTH;

    Timer timer;

    public void animate() {
        offsetX = 0;
        timer.start();
    }

    @Override
    public void paint(Graphics g) {
        double pgs = 1 - Math.sin(Math.PI / 2 / ANIMATION_LENGTH * offsetX);
        if(Math.abs(ANIMATION_LENGTH - offsetX) < 0.1) {
            super.paint(g);
            return;
        }
        if (pgs > 1)
            pgs = 1;
        if (pgs < 0)
            pgs = 0;
        Graphics2D gg = (Graphics2D) g;
        int width = this.getWidth();
        int height = this.getHeight();
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, width, height);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.translate((int) (pgs * 50), 0);
        super.paint(g2d);
        g2d.dispose();
        gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, (float) (1 - pgs)));
        g.drawImage(image, 0, 0, this);
    }
}
