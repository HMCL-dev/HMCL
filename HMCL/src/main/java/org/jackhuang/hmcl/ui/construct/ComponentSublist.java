/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;

@DefaultProperty("content")
public class ComponentSublist extends ComponentList {

    private final ObjectProperty<Node> headerLeft = new SimpleObjectProperty<>(this, "headerLeft");
    private final ObjectProperty<Node> headerRight = new SimpleObjectProperty<>(this, "headerRight");
    private final BooleanProperty margin = new SimpleBooleanProperty(this, "padding", true);

    public ComponentSublist() {
        super();
    }

    public Node getHeaderLeft() {
        return headerLeft.get();
    }

    public ObjectProperty<Node> headerLeftProperty() {
        return headerLeft;
    }

    public void setHeaderLeft(Node headerLeft) {
        this.headerLeft.set(headerLeft);
    }

    public Node getHeaderRight() {
        return headerRight.get();
    }

    public ObjectProperty<Node> headerRightProperty() {
        return headerRight;
    }

    public void setHeaderRight(Node headerRight) {
        this.headerRight.set(headerRight);
    }

    public boolean hasMargin() {
        return margin.get();
    }

    public BooleanProperty marginProperty() {
        return margin;
    }

    public void setMargin(boolean margin) {
        this.margin.set(margin);
    }
}
