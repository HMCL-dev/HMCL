/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.ui.wizard;

import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextArea;
import javafx.scene.Node;

/**
 * @author huangyuhui
 */
public final class Summary {
    private final Node component;
    private final Object result;

    public Summary(String[] items, Object result) {
        JFXListView<String> view = new JFXListView<>();
        view.getItems().addAll(items);

        this.component = view;
        this.result = result;
    }

    public Summary(String text, Object result) {
        JFXTextArea area = new JFXTextArea(text);
        area.setEditable(false);

        this.component = area;
        this.result = result;
    }

    public Summary(Node component, Object result) {
        this.component = component;
        this.result = result;
    }

    /**
     * The component that will display the summary information
     */
    public Node getComponent() {
        return component;
    }

    /**
     * The object that represents the actual result of whatever that Wizard
     * that created this Summary object computes, or null.
     */
    public Object getResult() {
        return result;
    }
}
