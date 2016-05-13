/*
 * $Id: MetroGraphicsUtils.java,v 1.9 2005/12/05 15:00:55 kizune Exp $
 *
 * Copyright 2004 Sun Microsystems, Inc., 4150 Network Circle,
 * Santa Clara, California 95054, U.S.A. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.jackhuang.hellominecraft.lookandfeel.painter;

import org.jackhuang.hellominecraft.util.ui.GraphicsUtils;

import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthPainter;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;

/**
 * ProgressPainter - Synth painter for Nimbus progressbars
 *
 * @author Created by Jasper Potts (Jan 3, 2007)
 * @version 1.0
 */
public class ProgressPainter extends SynthPainter {

    private static final float[] NORMAL_BG_PTS = new float[]{0, 1};
    private static final Color[] NORMAL_BG = new Color[]{
        GraphicsUtils.getWebColor("c6c6c6"),
        GraphicsUtils.getWebColor("c6c6c6")
    };
    private static final float[] BAR_FG_PTS = new float[]{0, 1};
    private static final Color[] BAR_FG = new Color[]{
        GraphicsUtils.getWebColor("41B1E1"),
        GraphicsUtils.getWebColor("41B1E1")
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public void paintProgressBarBackground(SynthContext context, Graphics g, int x, int y, int w, int h,
                                           int orientation) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new LinearGradientPaint(x, y + 2, x, y + h - 4, NORMAL_BG_PTS, NORMAL_BG));
        if (x + 2 < w - 5 && y + 2 < h - 5)
            g2.fillRect(x + 2, y + 2, w - 5, h - 5);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void paintProgressBarForeground(SynthContext context, Graphics g, int x, int y, int w, int h,
                                           int orientation) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new LinearGradientPaint(x, y + 2, x, y + h - 2, BAR_FG_PTS, BAR_FG));
        if (x + 2 < w - 5 && y + 2 < h - 5)
            g2.fillRect(x + 2, y + 2, w - 5, h - 5);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void paintProgressBarBorder(SynthContext context, Graphics g, int x, int y, int w, int h, int orientation) {
    }
}
