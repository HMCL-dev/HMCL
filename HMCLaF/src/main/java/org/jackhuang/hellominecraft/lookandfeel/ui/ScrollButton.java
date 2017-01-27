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
package org.jackhuang.hellominecraft.lookandfeel.ui;

import javax.swing.plaf.metal.MetalScrollButton;
import java.awt.Dimension;
import java.awt.Graphics;

/**
 * NimbusScrollButton - a fixed size 15x17 vertical 17x15 horizontal transparent
 * button.
 *
 * @author Created by Jasper Potts (Jan 17, 2007)
 * @version 1.0
 */
public class ScrollButton extends MetalScrollButton {

    private final int btnWidth, btnHeight;

    ScrollButton(int direction, int width, boolean freeStanding) {
        super(direction, width, freeStanding);
        setOpaque(false);
        if (direction == NORTH || direction == SOUTH) {
            btnWidth = 15;
            btnHeight = 17;
        } else {
            btnWidth = 17;
            btnHeight = 15;
        }
    }

    @Override
    public Dimension getMaximumSize() {
        return this.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return this.getPreferredSize();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(btnWidth, btnHeight);
    }

    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
        if (getParent() != null) getParent().repaint();
    }

    /**
     * Don't paint anything as all painting is done by the scrollbar
     *
     * @param g {@inheritDoc}
     */
    @Override
    public void paint(Graphics g) {
    }
}
