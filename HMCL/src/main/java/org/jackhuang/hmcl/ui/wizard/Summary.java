// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.wizard;

import com.jfoenix.controls.JFXListView;
import javafx.scene.Node;
import org.glavo.m3fx.controls.M3TextArea;
import org.jackhuang.hmcl.ui.FXUtils;

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
        M3TextArea area = new M3TextArea(text);
        area.setWrapText(false);
        FXUtils.useJFXContextMenu(area);
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
