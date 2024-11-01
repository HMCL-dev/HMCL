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

import javafx.scene.control.TextArea;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.text.Text;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class TextAreaSkinAdapter extends TextAreaSkin {
    private static final VarHandle promptNodeHandle;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(TextAreaSkin.class, MethodHandles.lookup());
            promptNodeHandle = lookup.findVarHandle(TextAreaSkin.class, "promptNode", Text.class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }


    public TextAreaSkinAdapter(TextArea control) {
        super(control);
    }


    protected final Text __getPromptNode() {
        return (Text) promptNodeHandle.get(this);
    }

    protected final void __setPromptNode(Text promptNode) {
        promptNodeHandle.set(this, promptNode);
    }
}
