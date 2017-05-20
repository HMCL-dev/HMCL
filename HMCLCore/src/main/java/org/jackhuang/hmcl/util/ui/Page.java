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

import org.jackhuang.hmcl.api.ui.TopTabPage;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 *
 * @author huangyuhui
 */
public class Page extends TopTabPage {

    boolean selected = false;
    public int id;

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void onSelect(TopTabPage lastSelectedPage) {
        if (!selected) {
            lastPage = (Page) lastSelectedPage;
            animate();
        }
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
    private static final int ANIMATION_LENGTH = 5;

    public Page() {
        timer = new Timer(1, (e) -> {
            SwingUtilities.invokeLater(() -> {
                Page.this.repaint();
                offsetX += 0.14;
                if (offsetX >= ANIMATION_LENGTH) {
                    timer.stop();
                    Page.this.repaint();
                }
            });
        });
    }

    BufferedImage cache = null, lastCache = null;

    @Override
    public void paint(Graphics g) {
        if (!(g instanceof Graphics2D)) {
            super.paint(g);
            return;
        }
        Graphics2D gg = (Graphics2D) g;
        double pgs = Math.sin(Math.PI / 2 / ANIMATION_LENGTH * offsetX);
        if (Math.abs(ANIMATION_LENGTH - offsetX) < 0.1) {
            super.paint(g);
            return;
        }

        if (offsetX == 0) {
            cache = cacheImpl(this);
            if (lastPage != null)
                lastCache = cacheImpl(lastPage);
        }
        int ori = lastPage != null ? (lastPage.getId() < getId() ? 1 : -1) : 1;
        if (pgs >= 0.5)
            animateImpl(gg, cache, (int) (((1 - (pgs - 0.5) * 2)) * totalOffset * ori), (float) ((pgs - 0.5) * 2));
        else
            animateImpl(gg, lastCache, (int) (((- pgs * 2)) * totalOffset * ori), (float) (1 - pgs * 2));
    }

    BufferedImage cacheImpl(Page page) {
        BufferedImage image = new BufferedImage(page.getWidth(), page.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        if (isOpaque()) {
            g2d.setColor(page.getBackground());
            g2d.fillRect(0, 0, page.getWidth(), page.getHeight());
        }
        page.superPaint(g2d);
        g2d.dispose();
        return image;
    }

    void animateImpl(Graphics2D g, BufferedImage image, int left, float alpha) {
        if (image == null)
            return;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha));
        g.drawImage(image, left, 0, this);
    }

    protected void superPaint(Graphics2D g) {
        super.paint(g);
    }

    double offsetX = ANIMATION_LENGTH, totalOffset = 20;
    Page lastPage;
    Timer timer;

    protected boolean animationEnabled = true;
    
    public Page setAnimationEnabled(boolean a) {
        animationEnabled = a;
        return this;
    }

    public void animate() {
        if (animationEnabled) {
            offsetX = 0;
            timer.start();
        }
    }
}
