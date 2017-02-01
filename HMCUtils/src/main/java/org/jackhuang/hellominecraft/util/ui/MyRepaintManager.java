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

import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.RepaintManager;

/**
 *
 * @author huangyuhui
 */
public class MyRepaintManager extends RepaintManager {

    @Override
    public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
        super.addDirtyRegion(c, x, y, w, h);

        for (Container parent = c.getParent(); (parent instanceof JComponent || parent instanceof Window) && parent.isVisible(); parent = parent.getParent()) {
            if (parent instanceof IRepaint) {
                IRepaint d = (IRepaint) parent;
                for (Rectangle r : d.getRepaintRects()) {
                    if (d.getRepaintComponent() != null)
                        super.addDirtyRegion(d.getRepaintComponent(), r.x, r.y, r.width, r.height);
                    if (d.getRepaintWindow() != null)
                        super.addDirtyRegion(d.getRepaintWindow(), r.x, r.y, r.width, r.height);
                }
            }
        }
    }
}
