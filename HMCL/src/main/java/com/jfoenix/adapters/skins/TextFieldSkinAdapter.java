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

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class TextFieldSkinAdapter extends javafx.scene.control.skin.TextFieldSkin {

    private static final VarHandle textNodeHandle;
    private static final VarHandle textTranslateXHandle;
    private static final VarHandle textRightHandle;
    private static final VarHandle usePromptTextHandle;
    private static final VarHandle promptNodeHandle;

    static {
        try {
            Class<javafx.scene.control.skin.TextFieldSkin> clazz = javafx.scene.control.skin.TextFieldSkin.class;
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());

            textNodeHandle = lookup.findVarHandle(clazz, "textNode", Text.class);
            textTranslateXHandle = lookup.findVarHandle(clazz, "textTranslateX", DoubleProperty.class);
            textRightHandle = lookup.findVarHandle(clazz, "textRight", ObservableDoubleValue.class);
            usePromptTextHandle = lookup.findVarHandle(clazz, "usePromptText", ObservableBooleanValue.class);
            promptNodeHandle = lookup.findVarHandle(clazz, "promptNode", Text.class);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public TextFieldSkinAdapter(TextField textField) {
        super(textField);
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
    }

    protected final ObjectProperty<Paint> __promptTextFillProperty() {
        return super.promptTextFillProperty();
    }

    protected void __registerChangeListener(ObservableValue<?> property, String key) {
        this.registerChangeListener(property, (property2) -> __handleControlPropertyChanged(key));
    }

    protected void __handleControlPropertyChanged(String propertyReference) {
    }

    protected final Text __getTextNode() {
        return (Text) textNodeHandle.get(this);
    }

    protected final DoubleProperty __getTextTranslateX() {
        return (DoubleProperty) textTranslateXHandle.get(this);
    }

    protected final ObservableDoubleValue __getTextRightHandle() {
        return (ObservableDoubleValue) textRightHandle.get(this);
    }

    protected final void __setUsePromptText(ObservableBooleanValue value) {
        usePromptTextHandle.set(this, value);
    }

    protected final Text __getPromptNode() {
        return (Text) promptNodeHandle.get(this);
    }

    protected final void __setPromptNode(Text promptNode) {
        promptNodeHandle.set(this, promptNode);
    }

}

