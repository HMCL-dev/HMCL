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

    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle", "");

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public String getSubtitle() {
        return subtitleProperty().get();
    }

    public void setSubtitle(String subtitle) {
        subtitleProperty().set(subtitle);
    }

    private boolean hasSubtitle;

    public boolean isHasSubtitle() {
        return hasSubtitle;
    }

    public void setHasSubtitle(boolean hasSubtitle) {
        this.hasSubtitle = hasSubtitle;
    }

    private final ObjectProperty<Node> leading = new SimpleObjectProperty<>(this, "leading");

    public ObjectProperty<Node> leadingProperty() {
        return leading;
    }

    public Node getLeading() {
        return leadingProperty().get();
    }

    public void setLeading(Node leading) {
        leadingProperty().set(leading);
    }

    private final ObjectProperty<Node> headerLeft = new SimpleObjectProperty<>(this, "headerLeft");

    public ObjectProperty<Node> headerLeftProperty() {
        return headerLeft;
    }

    public Node getHeaderLeft() {
        return headerLeftProperty().get();
    }

    public void setHeaderLeft(Node headerLeft) {
        headerLeftProperty().set(headerLeft);
    }

    private final ObjectProperty<Node> trailing = new SimpleObjectProperty<>(this, "trailing");

    public ObjectProperty<Node> trailingProperty() {
        return trailing;
    }

    public Node getTrailing() {
        return trailingProperty().get();
    }

    public void setTrailing(Node trailing) {
        trailingProperty().set(trailing);
    }

    public Node getHeaderRight() {
        return getTrailing();
    }

    public void setHeaderRight(Node headerRight) {
        setTrailing(headerRight);
    }

    /// The node displayed immediately after the default title text.
    private final ObjectProperty<Node> titleTrailing = new SimpleObjectProperty<>(this, "titleTrailing");

    /// Returns the node displayed immediately after the default title text.
    public ObjectProperty<Node> titleTrailingProperty() {
        return titleTrailing;
    }

    /// Returns the node displayed immediately after the default title text.
    public Node getTitleTrailing() {
        return titleTrailingProperty().get();
    }

    /// Sets the node displayed immediately after the default title text.
    public void setTitleTrailing(Node titleTrailing) {
        titleTrailingProperty().set(titleTrailing);
    }

    /// Returns the node displayed immediately after the default title text.
    public ObjectProperty<Node> titleRightProperty() {
        return titleTrailingProperty();
    }

    /// Returns the node displayed immediately after the default title text.
    public Node getTitleRight() {
        return getTitleTrailing();
    }

    /// Sets the node displayed immediately after the default title text.
    public void setTitleRight(Node titleRight) {
        setTitleTrailing(titleRight);
    }

    private final StringProperty description = new SimpleStringProperty(this, "description", "");

    public StringProperty descriptionProperty() {
        return description;
    }

    public String getDescription() {
        return descriptionProperty().get();
    }

    public void setDescription(String description) {
        descriptionProperty().set(description);
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
