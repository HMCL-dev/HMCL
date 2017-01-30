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
package org.jackhuang.hellominecraft.launcher.ui;

import org.jackhuang.hellominecraft.util.ui.IRepaint;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.Arrays;
import java.util.Collection;
import javax.swing.JComponent;

/**
 *
 * @author huang
 */
public class RepaintPage extends Page implements IRepaint {

    public RepaintPage() {
        super();
    }
    
    JComponent repainter;

    @Override
    public JComponent getRepaintComponent() {
        return repainter;
    }

    public void setRepainter(JComponent repainter) {
        this.repainter = repainter;
    }

    @Override
    public Collection<Rectangle> getRepaintRects() {
        return Arrays.asList(this.getBounds());
    }

    @Override
    public Window getRepaintWindow() {
        return null;
    }
}
