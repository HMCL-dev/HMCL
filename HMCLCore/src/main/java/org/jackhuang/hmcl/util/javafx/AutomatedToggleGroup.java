/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.javafx;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

/**
 * @author yushijinhun
 */
public class AutomatedToggleGroup extends ToggleGroup {

    private final ObservableList<? extends Toggle> toggles;
    private final ListChangeListener<Toggle> listListener;

    public AutomatedToggleGroup(ObservableList<? extends Toggle> toggles) {
        this.toggles = toggles;

        listListener = change -> {
            while (change.next()) {
                change.getRemoved().forEach(it -> it.setToggleGroup(null));
                change.getAddedSubList().forEach(it -> it.setToggleGroup(this));
            }
        };
        toggles.addListener(listListener);

        toggles.forEach(it -> it.setToggleGroup(this));
    }

    public void disconnect() {
        toggles.removeListener(listListener);
        toggles.forEach(it -> it.setToggleGroup(null));
    }
}
