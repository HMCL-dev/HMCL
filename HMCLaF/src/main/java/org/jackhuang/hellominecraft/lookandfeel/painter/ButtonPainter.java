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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Timer;
import javax.swing.plaf.synth.SynthConstants;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthPainter;
import org.jackhuang.hellominecraft.util.ui.GraphicsUtils;
import org.jackhuang.hellominecraft.lookandfeel.ConstomButton;

/**
 * ButtonPainter - handles painting Nimbus style buttons with Java2D
 *
 * @author Created by Jasper Potts (Jan 4, 2007)
 * @version 1.0
 */
public class ButtonPainter extends SynthPainter {

    private static final String DEFAULT_NORMAL = "D5D5D5";
    private static final Color[] DEFAULT_NORMAL_FG = new Color[]{
        GraphicsUtils.getWebColor(DEFAULT_NORMAL),
        GraphicsUtils.getWebColor(DEFAULT_NORMAL)
    };
    private static final String DEFAULT_PRELIGHT = "A9A9A9";
    private static final Color[] DEFAULT_PRELIGHT_FG = new Color[]{
        GraphicsUtils.getWebColor(DEFAULT_PRELIGHT),
        GraphicsUtils.getWebColor(DEFAULT_PRELIGHT)
    };
    private static final String DEFAULT_ACTIVE = "222222";
    private static final Color[] DEFAULT_ACTIVE_FG = new Color[]{
        GraphicsUtils.getWebColor(DEFAULT_ACTIVE),
        GraphicsUtils.getWebColor(DEFAULT_ACTIVE)
    };

    private static final Color[] DISABLED_BG = new Color[]{
        GraphicsUtils.getWebColor("E3EFE9"),
        GraphicsUtils.getMidWebColor("E3EFE9", "DFE2E6"),
        GraphicsUtils.getWebColor("DFE2E6"),
        GraphicsUtils.getMidWebColor("DFE2E6", "D6D9DF"),
        GraphicsUtils.getWebColor("D6D9DF"),
        GraphicsUtils.getWebColor("D6D9DF"),
        GraphicsUtils.getMidWebColor("D6D9DF", "D8DBE1"),
        GraphicsUtils.getWebColor("D8DBE1"),
        GraphicsUtils.getWebColor("DADDE3")
    };
    private static final Color[] DISABLED_FG = new Color[]{
        GraphicsUtils.getWebColor("C9CCD2"),
        GraphicsUtils.getWebColor("C9CCD2"),
        GraphicsUtils.getWebColor("BCBFC5"),
        GraphicsUtils.getWebColor("BCBFC5")
    };

    private static boolean processCustomButton(final ConstomButton c, int add) {
        if (c.drawPercent == 0 || c.drawPercent == 100) {
            Timer t = new Timer(1, null);
            t.addActionListener(x -> {
                c.drawPercent += add;
                if (c.drawPercent > 100 && add > 0) {
                    c.drawPercent = 100;
                    t.stop();
                } else if (c.drawPercent < 0 && add < 0) {
                    c.drawPercent = 0;
                    t.stop();
                } else
                    c.updateUI();
            });
            t.start();
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void paintButtonBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color[] fg, bg;
        if ((context.getComponentState() & SynthConstants.DEFAULT) != 0)
            if ((context.getComponentState() & SynthConstants.PRESSED) != 0)
                if (context.getComponent() instanceof ConstomButton) {
                    ConstomButton c = (ConstomButton) context.getComponent();
                    fg = new Color[]{c.activeFg, c.activeFg};
                    bg = new Color[]{c.activeFg, c.activeFg};
                } else {
                    fg = DEFAULT_ACTIVE_FG;
                    bg = DEFAULT_ACTIVE_FG;
                }
            else if ((context.getComponentState() & SynthConstants.DISABLED) != 0)
                return; //fg = DISABLED_FG;
            //bg = DISABLED_BG;
            else if ((context.getComponentState() & SynthConstants.MOUSE_OVER) != 0)
                if (context.getComponent() instanceof ConstomButton) {
                    final ConstomButton c = (ConstomButton) context.getComponent();
                    if (!processCustomButton(c, 1))
                        return;
                    Color fgs = GraphicsUtils.getMidWebColor(c.normalFg, c.prelightFg, c.drawPercent);
                    Color bgs = GraphicsUtils.getMidWebColor(c.normalBg, c.prelightBg, c.drawPercent);
                    fg = new Color[]{fgs, fgs};
                    bg = new Color[]{bgs, bgs};
                } else {
                    fg = DEFAULT_PRELIGHT_FG;
                    bg = DEFAULT_PRELIGHT_FG;
                }
            else if (context.getComponent() instanceof ConstomButton) {
                final ConstomButton c = (ConstomButton) context.getComponent();
                if (!processCustomButton(c, -1))
                    return;
                Color fgs = GraphicsUtils.getMidWebColor(c.normalFg, c.prelightFg, c.drawPercent);
                Color bgs = GraphicsUtils.getMidWebColor(c.normalBg, c.prelightBg, c.drawPercent);
                fg = new Color[]{fgs, fgs};
                bg = new Color[]{bgs, bgs};
            } else {
                fg = DEFAULT_NORMAL_FG;
                bg = DEFAULT_NORMAL_FG;
            }
        else if ((context.getComponentState() & SynthConstants.PRESSED) != 0)
            if (context.getComponent() instanceof ConstomButton) {
                ConstomButton c = (ConstomButton) context.getComponent();
                fg = new Color[]{c.activeFg, c.activeFg};
                bg = new Color[]{c.activeFg, c.activeFg};
            } else {
                fg = DEFAULT_ACTIVE_FG;
                bg = DEFAULT_ACTIVE_FG;
            }
        else if ((context.getComponentState() & SynthConstants.DISABLED) != 0)
            return; //fg = DISABLED_FG;
        //bg = DISABLED_BG;
        else if ((context.getComponentState() & SynthConstants.MOUSE_OVER) != 0)
            if (context.getComponent() instanceof ConstomButton) {
                final ConstomButton c = (ConstomButton) context.getComponent();
                if (!processCustomButton(c, 1))
                    return;
                Color fgs = GraphicsUtils.getMidWebColor(c.normalFg, c.prelightFg, c.drawPercent);
                Color bgs = GraphicsUtils.getMidWebColor(c.normalBg, c.prelightBg, c.drawPercent);
                fg = new Color[]{fgs, fgs};
                bg = new Color[]{bgs, bgs};
            } else if (context.getComponent() instanceof ConstomButton) {
                ConstomButton c = (ConstomButton) context.getComponent();
                fg = new Color[]{c.prelightFg, c.prelightFg};
                bg = new Color[]{c.prelightBg, c.prelightBg};
            } else {
                fg = DEFAULT_PRELIGHT_FG;
                bg = DEFAULT_PRELIGHT_FG;
            }
        else if (context.getComponent() instanceof ConstomButton) {
            final ConstomButton c = (ConstomButton) context.getComponent();
            if (!processCustomButton(c, -1))
                return;
            Color fgs = GraphicsUtils.getMidWebColor(c.normalFg, c.prelightFg, c.drawPercent);
            Color bgs = GraphicsUtils.getMidWebColor(c.normalBg, c.prelightBg, c.drawPercent);
            fg = new Color[]{fgs, fgs};
            bg = new Color[]{bgs, bgs};
        } else {
            fg = DEFAULT_NORMAL_FG;
            bg = DEFAULT_NORMAL_FG;
        }
        /*w = w - 2;
         h = h - 2;

         g2.setPaint(new LinearGradientPaint(x, y, x, y + h,
         new float[]{0, 1}, bg));
         g2.fillRect(x, y, w, h);

         g2.setPaint(new LinearGradientPaint(x, y, x, y + h,
         new float[]{0, 1}, fg));
         g2.drawRect(x, y, w, h);*/

        int radix = (context.getComponent() instanceof ConstomButton) ? ((ConstomButton) context.getComponent()).radix : 0;

        g2.setColor(fg[0]);
        RoundRectangle2D fgshape = new RoundRectangle2D.Float(x, y, w, h, radix, radix);
        g2.draw(fgshape);
        g2.setColor(bg[0]);
        RoundRectangle2D bgshape = new RoundRectangle2D.Float(x, y, w, h, radix, radix);
        g2.fill(bgshape);
    }

    @Override
    public void paintToggleButtonBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color[] fg, bg;
        //if ((context.getComponentState() & SynthConstants.DEFAULT) != 0)
        if ((context.getComponentState() & SynthConstants.PRESSED) != 0 || (context.getComponentState() & SynthConstants.SELECTED) != 0) {
            fg = DEFAULT_ACTIVE_FG;
            bg = DEFAULT_ACTIVE_FG;
        } else if ((context.getComponentState() & SynthConstants.DISABLED) != 0) {
            fg = DISABLED_FG;
            bg = DISABLED_BG;
        } else if ((context.getComponentState() & SynthConstants.MOUSE_OVER) != 0) {
            fg = DEFAULT_PRELIGHT_FG;
            bg = DEFAULT_PRELIGHT_FG;
        } else {
            fg = DEFAULT_NORMAL_FG;
            bg = DEFAULT_NORMAL_FG;
        }
        /*else if ((context.getComponentState() & SynthConstants.PRESSED) != 0 || (context.getComponentState() & SynthConstants.SELECTED) != 0) {
            fg = DEFAULT_ACTIVE_FG;
            bg = DEFAULT_ACTIVE_FG;
        } else if ((context.getComponentState() & SynthConstants.DISABLED) != 0) {
            fg = DISABLED_FG;
            bg = DISABLED_BG;
        } else if ((context.getComponentState() & SynthConstants.MOUSE_OVER) != 0) {
            fg = DEFAULT_PRELIGHT_FG;
            bg = DEFAULT_PRELIGHT_FG;
        } else {
            fg = DEFAULT_NORMAL_FG;
            bg = DEFAULT_NORMAL_FG;
        }*/
        g2.setColor(fg[0]);
        Rectangle2D fgshape = new Rectangle2D.Float(x, y, w, h);
        g2.draw(fgshape);
        g2.setColor(bg[0]);
        Rectangle2D bgshape = new Rectangle2D.Float(x, y, w, h);
        g2.fill(bgshape);

        /*g2.setPaint(new LinearGradientPaint(x, y, x, y + h,
         new float[]{0, 1}, bg));
         g2.fillRect(x, y, w, h);

         g2.setPaint(new LinearGradientPaint(x, y, x, y + h,
         new float[]{0, 1}, fg));
         g2.drawRect(x, y, w, h);*/
    }
}
