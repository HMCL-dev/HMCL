/*
* $Id: NimbusGraphicsUtils.java,v 1.9 2005/12/05 15:00:55 kizune Exp $
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

import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthPainter;
import java.awt.Color;
import java.awt.Graphics;
import javax.swing.plaf.synth.SynthConstants;
import org.jackhuang.hellominecraft.util.ui.GraphicsUtils;

/**
 * TextFieldPainter
 *
 * @author Created by Jasper Potts (Jan 4, 2007)
 * @version 1.0
 */
public class TextFieldPainter extends SynthPainter {

    private boolean fill = true;

    private static final Color DISABLED = GraphicsUtils.getWebColor("F3F3F3"),
        NORMAL = GraphicsUtils.getWebColor("CCCCCC"),
        FOCUSED = GraphicsUtils.getWebColor("000000"),
        OVER = GraphicsUtils.getWebColor("7F7F7F");

    public TextFieldPainter() {
    }

    public TextFieldPainter(boolean fill) {
        this.fill = fill;
    }

    private void paintFieldBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        w--;
        h--;
        if (fill) {
            g.setColor(Color.WHITE);
            g.fillRect(x, y, w, h);
        }
        Color color;
        if ((context.getComponentState() & SynthConstants.MOUSE_OVER) != 0)
            color = OVER;
        else if ((context.getComponentState() & SynthConstants.DISABLED) != 0)
            color = DISABLED;
        else if ((context.getComponentState() & SynthConstants.FOCUSED) != 0)
            color = FOCUSED;
        else
            color = NORMAL;
        g.setColor(color);
        g.drawLine(x, y, x + w, y);
        g.drawLine(x, y, x, y + w);
        g.drawLine(x + w, y, x + w, y + h);
        g.drawLine(x, y + h, x + w, y + h);
    }

    @Override
    public void paintPasswordFieldBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        paintFieldBackground(context, g, x, y, w, h);
    }

    @Override
    public void paintTextAreaBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        paintFieldBackground(context, g, x, y, w, h);
    }

    @Override
    public void paintTextFieldBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        paintFieldBackground(context, g, x, y, w, h);
    }

    @Override
    public void paintTextPaneBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        paintFieldBackground(context, g, x, y, w, h);
    }

    @Override
    public void paintScrollPaneBackground(SynthContext context, Graphics g, int x, int y, int w, int h) {
        paintFieldBackground(context, g, x, y, w, h);
    }
}
