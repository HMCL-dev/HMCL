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
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;

import java.util.List;
import java.util.function.Supplier;

public class ComponentSublist extends Control implements NoPaddingComponent {

    private final ComponentList contentList = new ComponentList();
    Supplier<List<? extends Node>> lazyInitializer;

    public ComponentSublist() {
        contentList.getStyleClass().remove("options-list");
        contentList.getStyleClass().add("options-sublist");
    }

    public ComponentSublist(Supplier<List<? extends Node>> lazyInitializer) {
        this();
        this.lazyInitializer = lazyInitializer;
    }

    public ObservableList<Node> getContent() {
        return contentList.getContent();
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected javafx.scene.control.Skin<?> createDefaultSkin() {
        return new Skin(this);
    }

    private static final class Skin extends ControlSkinBase<ComponentSublist> {
        Skin(ComponentSublist control) {
            super(control);
            node = control.contentList;
        }
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

    /// The node displayed immediately after the default title text.
    private final ObjectProperty<Node> titleRight = new SimpleObjectProperty<>(this, "titleRight");

    /// Returns the node displayed immediately after the default title text.
    public ObjectProperty<Node> titleRightProperty() {
        return titleRight;
    }

    /// Returns the node displayed immediately after the default title text.
    public Node getTitleRight() {
        return titleRightProperty().get();
    }

    /// Sets the node displayed immediately after the default title text.
    public void setTitleRight(Node titleRight) {
        titleRightProperty().set(titleRight);
    }

    private boolean componentPadding = true;

    public boolean hasComponentPadding() {
        return componentPadding;
    }

    public void setComponentPadding(boolean componentPadding) {
        this.componentPadding = componentPadding;
    }

    private final StringProperty tip = new SimpleStringProperty(this, "tip");

    public StringProperty tipProperty() {
        return tip;
    }

    public String getTip() {
        return tip.get();
    }

    public void setTip(String tip) {
        this.tip.set(tip);
    }
}
