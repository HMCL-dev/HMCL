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
package org.jackhuang.hellominecraft.util.ui.wizard.spi;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.jackhuang.hellominecraft.util.func.Consumer;

/**
 *
 * @author huang
 */
public abstract class AbstractWizard implements WizardImplementation {

    protected final List<WizardObserver> listenerList = Collections.synchronizedList(new LinkedList<>());
    
    protected void fireChanged(Consumer<WizardObserver> r) {
        WizardObserver[] listeners = listenerList.toArray(new WizardObserver[listenerList.size()]);

        for (int i = listeners.length - 1; i >= 0; i--) {
            WizardObserver l = (WizardObserver) listeners[i];
            r.accept(l);
        }
    }
}
