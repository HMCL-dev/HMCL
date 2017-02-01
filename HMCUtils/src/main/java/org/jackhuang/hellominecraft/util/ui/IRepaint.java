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

import java.awt.Rectangle;
import java.awt.Window;
import java.util.Collection;
import javax.swing.JComponent;

/**
 *
 * @author huang
 */
public interface IRepaint {
    
    /**
     * addDirtyRegion to?
     * @return the component which needs repainting.
     */
    JComponent getRepaintComponent();
    
    /**
     * addDirtyRegion to?
     * @return the window which needs repainting.
     */
    Window getRepaintWindow();
    
    /**
     * Repaint the component/window you want.
     * @return the region where you want to repaint.
     */
    Collection<Rectangle> getRepaintRects();
}
