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
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * This component will allow some area blured to provide better UI.
 * @author huangyuhui
 */
public class GaussionPanel extends JPanel {

    private transient BufferedImage aeroBuffer;
    private transient Image backgroundImage;
    private final List<JPanel> aeroObject = new ArrayList<>();
    private transient Graphics2D aeroGraphics;
    private static final int RADIUS = 10;
    private transient final StackBlurFilter stackBlurFilter = new StackBlurFilter(RADIUS);
    private transient BufferedImage cache = null;
    private boolean drawBackgroundLayer = false;

    public boolean isDrawBackgroundLayer() {
        return drawBackgroundLayer;
    }

    public void setDrawBackgroundLayer(boolean drawBackgroundLayer) {
        this.drawBackgroundLayer = drawBackgroundLayer;
    }

    public void setBackgroundImage(Image backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    /**
     * The background will be blured under the aero object.
     * @param aeroObject just need its bounds, keep it not opaque.
     */
    public void addAeroObject(JPanel aeroObject) {
        this.aeroObject.add(aeroObject);
        cache = null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (backgroundImage == null)
            return;
        
        // If we cache the processed background image, the CPU ratio will reduce 5%.
        if (cache == null || getWidth() != cache.getWidth() || getHeight() != cache.getHeight()) {
            cache = new BufferedImage(getWidth(), getHeight(), 2);
            Graphics2D g2 = cache.createGraphics();
            g2.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
            for (JPanel panel : aeroObject) {
                Rectangle aeroRect = panel.getBounds();
                if (aeroBuffer == null || aeroBuffer.getWidth() != aeroRect.width + RADIUS || aeroBuffer.getHeight() != aeroRect.height + RADIUS) {
                    if (aeroBuffer != null && aeroGraphics != null) {
                        aeroBuffer.flush();
                        aeroGraphics.dispose();
                    }
                    aeroBuffer = new BufferedImage(aeroRect.width + RADIUS, aeroRect.height + RADIUS, BufferedImage.TRANSLUCENT);
                }

                aeroGraphics = aeroBuffer.createGraphics();
                aeroGraphics.setComposite(AlphaComposite.Src);
                aeroGraphics.drawImage(backgroundImage, 0, 0, aeroBuffer.getWidth(), aeroBuffer.getHeight(), aeroRect.x, aeroRect.y, aeroRect.x + aeroRect.width, aeroRect.y + aeroRect.height, null);
                aeroBuffer = stackBlurFilter.filter(aeroBuffer, null);
                g2.drawImage(aeroBuffer, aeroRect.x, aeroRect.y, aeroRect.x + aeroRect.width, aeroRect.y + aeroRect.height, RADIUS / 2, RADIUS / 2, RADIUS / 2 + aeroRect.width, RADIUS / 2 + aeroRect.height, null);

                // Moved from GameSettingsPanel and LauncherSettingsPanel
                if (drawBackgroundLayer) {
                    g2.setColor(Color.white);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.5f));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            }
            g2.dispose();
        }
        g.drawImage(cache, 0, 0, getWidth(), getHeight(), null);
    }

}