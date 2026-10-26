// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
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

    /// Whether the sublist header should use a larger title font.
    private static final PseudoClass PSEUDO_LARGE_TITLE = PseudoClass.getPseudoClass("large-title");

    /// The property controlling whether the sublist header uses a larger title font.
    private final BooleanProperty largeTitle = new SimpleBooleanProperty(this, "largeTitle", false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PSEUDO_LARGE_TITLE, get());
        }
    };

    /// Returns the property controlling whether the sublist header uses a larger title font.
    public BooleanProperty largeTitleProperty() {
        return largeTitle;
    }

    /// Returns whether the sublist header uses a larger title font.
    public final boolean isLargeTitle() {
        return largeTitle.get();
    }

    /// Sets whether the sublist header uses a larger title font.
    public final void setLargeTitle(boolean value) {
        largeTitle.set(value);
    }
}
