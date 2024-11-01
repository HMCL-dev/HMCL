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

import javafx.animation.Animation;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.skin.ProgressBarSkin;
import javafx.scene.control.skin.ProgressIndicatorSkin;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class ProgressBarSkinAdapter extends ProgressBarSkin {
    private static final VarHandle indeterminateTransitionFieldHandle;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ProgressIndicatorSkin.class, MethodHandles.lookup());
            indeterminateTransitionFieldHandle = lookup.findVarHandle(ProgressIndicatorSkin.class, "indeterminateTransition", Animation.class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public ProgressBarSkinAdapter(ProgressBar control) {
        super(control);
    }

    protected void __registerChangeListener(ObservableValue<?> property, String key) {
        this.registerChangeListener(property, (property2) -> __handleControlPropertyChanged(key));
    }

    protected void __handleControlPropertyChanged(String p) {

    }

    protected Animation __getIndeterminateTransition() {
        return (Animation) indeterminateTransitionFieldHandle.get(this);
    }

    protected void __setIndeterminateTransition(Animation animation) {
        indeterminateTransitionFieldHandle.set(this, animation);
    }
}
