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

import javafx.beans.property.*;
import javafx.scene.Node;

import java.util.List;
import java.util.function.Supplier;

public class ComponentSublist extends ComponentList {

    Supplier<List<? extends Node>> lazyInitializer;

    public ComponentSublist() {
        super();
    }

    public ComponentSublist(Supplier<List<? extends Node>> lazyInitializer) {
        this.lazyInitializer = lazyInitializer;
    }

    void doLazyInit() {
        if (lazyInitializer != null) {
            this.getContent().setAll(lazyInitializer.get());
            setNeedsLayout(true);
            lazyInitializer = null;
        }
    }

    private final StringProperty title = new SimpleStringProperty(this, "title", "Group");

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return titleProperty().get();
    }

    public void setTitle(String title) {
        titleProperty().set(title);
    }

    private StringProperty subtitle;

    public StringProperty subtitleProperty() {
        if (subtitle == null)
            subtitle = new SimpleStringProperty(this, "subtitle", "");

        return subtitle;
    }

    public String getSubtitle() {
        return subtitleProperty().get();
    }

    public void setSubtitle(String subtitle) {
        subtitleProperty().set(subtitle);
    }

    private boolean hasSubtitle = false;

    public boolean isHasSubtitle() {
        return hasSubtitle;
    }

    public void setHasSubtitle(boolean hasSubtitle) {
        this.hasSubtitle = hasSubtitle;
    }

    private Node headerLeft;

    public Node getHeaderLeft() {
        return headerLeft;
    }

    public void setHeaderLeft(Node headerLeft) {
        this.headerLeft = headerLeft;
    }

    private Node headerRight;

    public Node getHeaderRight() {
        return headerRight;
    }

    public void setHeaderRight(Node headerRight) {
        this.headerRight = headerRight;
    }

    private boolean componentPadding = true;

    public boolean hasComponentPadding() {
        return componentPadding;
    }

    public void setComponentPadding(boolean componentPadding) {
        this.componentPadding = componentPadding;
    }
}
