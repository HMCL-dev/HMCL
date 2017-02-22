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
package org.jackhuang.hmcl.laf.radio$cb_btn;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalRadioButtonUI;

/**
 *
 * @author huang
 */
public class BERadioButtonUI extends MetalRadioButtonUI {

    private static final BERadioButtonUI INSTANCE = new BERadioButtonUI();

    public static ComponentUI createUI(JComponent b) {
        return INSTANCE;
    }
    
    @Override
    public void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        
        LookAndFeel.installProperty(b, "opaque", false);
    }
    
}
