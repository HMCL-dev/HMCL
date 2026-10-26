// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
