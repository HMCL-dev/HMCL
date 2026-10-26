// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.javafx;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

/**
 * @author yushijinhun
 */
public final class AutomatedToggleGroup extends ToggleGroup {

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
