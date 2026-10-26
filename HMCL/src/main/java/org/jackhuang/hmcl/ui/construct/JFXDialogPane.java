// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class JFXDialogPane extends StackPane {
    private final ArrayList<Node> stack = new ArrayList<>();

    public int size() {
        return stack.size();
    }

    public Optional<Node> peek() {
        if (stack.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(stack.get(stack.size() - 1));
        }
    }

    public void push(Node node) {
        stack.add(node);
        getChildren().setAll(node);

        LOG.info(this + " " + stack);
    }

    public void pop(Node node) {
        boolean flag = stack.remove(node);
        if (stack.isEmpty())
            getChildren().setAll();
        else
            getChildren().setAll(stack.get(stack.size() - 1));

        LOG.info(this + " " + stack + ", removed: " + flag + ", object: " + node);
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
