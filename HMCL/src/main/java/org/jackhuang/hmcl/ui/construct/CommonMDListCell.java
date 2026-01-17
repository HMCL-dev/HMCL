/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXListView;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;

public abstract class CommonMDListCell<T> extends MDListCell<T> {

    public CommonMDListCell(JFXListView<T> listView) {
        super(listView);
    }

    public <N extends Event> void addCellEventHandler(EventType<N> eventType, EventHandler<? super N> eventHandler) {
        getContainer().getParent().addEventHandler(eventType, eventHandler);
    }

    @Override
    public void setSelectable() {
        super.setSelectable();
    }
}
