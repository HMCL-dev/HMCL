/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package com.jfoenix.adapters.skins;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.skin.ProgressIndicatorSkin;

public abstract class ProgressIndicatorSkinAdapter extends ProgressIndicatorSkin {
    public ProgressIndicatorSkinAdapter(ProgressIndicator control) {
        super(control);
    }

    protected final void __registerChangeListener(ObservableValue<?> property, String key) {
        this.registerChangeListener(property, ignored -> __handleControlPropertyChanged(key));
    }

    protected abstract void __handleControlPropertyChanged(String key);
}
