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
package org.jackhuang.hmcl.ui;

import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jackhuang.hmcl.api.ui.TopTabPage;

/**
 *
 * @author huangyuhui
 */
public class NewTabPane extends JTabbedPane implements ChangeListener {

    public NewTabPane() {
        super();
        addChangeListener(this);
    }

    public boolean initializing;

    @Override
    public void stateChanged(ChangeEvent e) {
        if (initializing)
            return;
        for (int i = 0; i < getComponentCount(); ++i)
            if (getComponent(i) instanceof TopTabPage) {
                TopTabPage comp = (TopTabPage) getComponent(i);
                comp.setId(i);
                if (getSelectedIndex() != i)
                    ((TopTabPage) getComponent(i)).onLeave();
            }
        if (getSelectedComponent() instanceof TopTabPage) {
            if (page == null && getComponentCount() > 0 && getComponent(0) instanceof TopTabPage)
                page = (TopTabPage) getComponent(0);
            ((TopTabPage) getSelectedComponent()).onSelect(page);
            page = (TopTabPage) getSelectedComponent();
        }
    }

    TopTabPage page = null;

}
