/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.util.Logging;

import java.util.Optional;
import java.util.Stack;

public class StackContainerPane extends StackPane {
    private final Stack<Node> stack = new Stack<>();

    public Optional<Node> peek() {
        if (stack.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(stack.peek());
        }
    }

    public void push(Node node) {
        stack.push(node);
        getChildren().setAll(node);

        Logging.LOG.info(this + " " + stack);
    }

    public void pop(Node node) {
        boolean flag = stack.remove(node);
        if (stack.isEmpty())
            getChildren().setAll();
        else
            getChildren().setAll(stack.peek());

        Logging.LOG.info(this + " " + stack + ", removed: " + flag + ", object: " + node);
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
