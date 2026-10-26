// SPDX-FileCopyrightText: Copyright (C) 2022 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.util.Objects;

public abstract class ControlSkinBase<C extends Control> implements Skin<C> {
    private final C control;

    protected Node node;

    /**
     * Constructor for all SkinBase instances.
     *
     * @param control The control for which this Skin should attach to.
     */
    protected ControlSkinBase(C control) {
        this.control = control;
    }

    @Override
    public C getSkinnable() {
        return control;
    }

    @Override
    public Node getNode() {
        Objects.requireNonNull(node);
        return node;
    }

    @Override
    public void dispose() {

    }
}
