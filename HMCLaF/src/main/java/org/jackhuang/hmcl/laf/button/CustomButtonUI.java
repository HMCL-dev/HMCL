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
package org.jackhuang.hmcl.laf.button;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractButton; 
import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.Timer;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import org.jackhuang.hmcl.util.ui.GraphicsUtils;
import org.jackhuang.hmcl.laf.button.BEButtonUI;

/**
 *
 * @author huang
 */
public class CustomButtonUI extends BasicButtonUI {

    private static final String DEFAULT_NORMAL = "D5D5D5";
    private static final Color[] DEFAULT_NORMAL_FG = new Color[] {
        GraphicsUtils.getWebColor(DEFAULT_NORMAL),
        GraphicsUtils.getWebColor(DEFAULT_NORMAL)
    };
    private static final String DEFAULT_PRELIGHT = "A9A9A9";
    private static final Color[] DEFAULT_PRELIGHT_FG = new Color[] {
        GraphicsUtils.getWebColor(DEFAULT_PRELIGHT),
        GraphicsUtils.getWebColor(DEFAULT_PRELIGHT)
    };
    private static final String DEFAULT_ACTIVE = "222222";
    private static final Color[] DEFAULT_ACTIVE_FG = new Color[] {
        GraphicsUtils.getWebColor(DEFAULT_ACTIVE),
        GraphicsUtils.getWebColor(DEFAULT_ACTIVE)
    };

    private static final Color[] DISABLED_BG = new Color[] {
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
    private static final Color[] DISABLED_FG = new Color[] {
        GraphicsUtils.getWebColor("C9CCD2"),
        GraphicsUtils.getWebColor("C9CCD2"),
        GraphicsUtils.getWebColor("BCBFC5"),
        GraphicsUtils.getWebColor("BCBFC5")
    };

    private static boolean processCustomButton(final CustomButton c, int add) {
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

    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        LookAndFeel.installProperty(b, "opaque", Boolean.FALSE);
    }

    @Override
    public void paint(Graphics g, JComponent component) {
        CustomButton c = (CustomButton) component;
        ButtonModel model = c.getModel();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color[] fg, bg;
        if (model.isPressed()) {
            fg = new Color[] { c.activeFg, c.activeFg };
            bg = new Color[] { c.activeFg, c.activeFg };
        } else if (!c.isEnabled())
            return;
        else if (model.isRollover()) {
            if (!processCustomButton(c, 1))
                return;
            Color fgs = GraphicsUtils.getMidWebColor(c.normalFg, c.prelightFg, c.drawPercent);
            Color bgs = GraphicsUtils.getMidWebColor(c.normalBg, c.prelightBg, c.drawPercent);
            fg = new Color[] { fgs, fgs };
            bg = new Color[] { bgs, bgs };
        } else {
            if (!processCustomButton(c, -1))
                return;
            Color fgs = GraphicsUtils.getMidWebColor(c.normalFg, c.prelightFg, c.drawPercent);
            Color bgs = GraphicsUtils.getMidWebColor(c.normalBg, c.prelightBg, c.drawPercent);
            fg = new Color[] { fgs, fgs };
            bg = new Color[] { bgs, bgs };
        }

        int radix = c.radix;
        int x = 0, y = 0, w = c.getWidth(), h = c.getHeight();

        g2.setColor(fg[0]);
        RoundRectangle2D fgshape = new RoundRectangle2D.Float(x, y, w, h, radix, radix);
        g2.draw(fgshape);
        g2.setColor(bg[0]);
        RoundRectangle2D bgshape = new RoundRectangle2D.Float(x, y, w, h, radix, radix);
        g2.fill(bgshape);

        super.paint(g, c);
    }
}
