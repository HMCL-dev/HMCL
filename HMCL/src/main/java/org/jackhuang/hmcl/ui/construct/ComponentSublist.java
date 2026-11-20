// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.glavo.m3fx.controls.M3ExpandableSettingItem;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/// A collapsible group that retains its child settings between expansions.
public class ComponentSublist extends StackPane implements NoPaddingComponent {

    /// The nested rows displayed while expanded.
    private final ComponentList contentList = new ComponentList();
    /// The deferred rows, cleared after their first use.
    private @Nullable Supplier<List<? extends Node>> lazyInitializer;

    /// Creates an initially collapsed group with an editable list of child rows.
    public ComponentSublist() {
        contentList.getStyleClass().remove("options-list");
        contentList.getStyleClass().add("options-sublist");
        getChildren().setAll(createExpandableItem());
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

    /// Builds a Material header and initializes the nested controls on first expansion.
    private M3ExpandableSettingItem createExpandableItem() {
        var item = new M3ExpandableSettingItem();
        item.headlineTextProperty().bind(titleProperty());
        item.supportingTextProperty().bind(subtitleProperty());
        item.leadingProperty().bind(leadingProperty());
        item.trailingSupportingTextProperty().bind(descriptionProperty());
        item.expandedProperty().addListener((observable, wasExpanded, expanded) -> {
            if (expanded && item.getContent() == null) {
                doLazyInit();
                item.setContent(contentList);
            }
        });

        item.headlineTrailingProperty().bind(Bindings.createObjectBinding(() -> {
            @Nullable Node action = getTitleTrailing();
            @Nullable String tip = getTip();
            if (StringUtils.isBlank(tip)) return action;
            var info = new StackPane(SVG.INFO.createIcon(16));
            FXUtils.installFastTooltip(info, tip);
            if (action == null) return info;
            var group = new HBox(4, action, info);
            group.setAlignment(Pos.CENTER_LEFT);
            return group;
        }, titleTrailingProperty(), tipProperty()));

        Node disclosure = Objects.requireNonNull(item.getTrailing());
        var actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPickOnBounds(false);
        item.setTrailing(actions);
        FXUtils.onChangeAndOperate(trailingProperty(), action -> {
            if (action == null) actions.getChildren().setAll(disclosure);
            else actions.getChildren().setAll(action, disclosure);
        });
        return item;
    }

    /// Populates deferred rows once.
    private void doLazyInit() {
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
