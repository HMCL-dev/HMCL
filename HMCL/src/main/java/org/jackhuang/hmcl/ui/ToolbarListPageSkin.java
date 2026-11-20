// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3ListCell;
import org.glavo.m3fx.controls.M3ListView;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.GraphicListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ToolbarListPageSkin<E, P extends ListPageBase<E>> extends SkinBase<P> {

    protected final M3ListView<E> listView;

    /// Creates a toolbar list without an empty-state message.
    public ToolbarListPageSkin(P skinnable) {
        this(skinnable, null);
    }

    /// Creates a toolbar list with an optional message shown while it has no items.
    protected ToolbarListPageSkin(P skinnable, @Nullable String emptyMessage) {
        super(skinnable);

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");

        StackPane container = new StackPane();
        container.getChildren().add(root);
        StackPane.setMargin(root, new Insets(10));

        List<Node> toolbarButtons = initializeToolbar(skinnable);
        if (!toolbarButtons.isEmpty()) {
            HBox toolbar = new HBox();
            toolbar.setAlignment(Pos.CENTER_LEFT);
            toolbar.setPickOnBounds(false);
            toolbar.getChildren().setAll(toolbarButtons);
            FXUtils.setOverflowHidden(toolbar, 8);
            root.getContent().add(toolbar);
        }

        SpinnerPane spinnerPane = new SpinnerPane();
        spinnerPane.loadingProperty().bind(skinnable.loadingProperty());
        spinnerPane.failedReasonProperty().bind(skinnable.failedReasonProperty());
        spinnerPane.onFailedActionProperty().bind(skinnable.onFailedActionProperty());

        ComponentList.setVgrow(spinnerPane, Priority.ALWAYS);

        {
            this.listView = new M3ListView<>();
            this.listView.setPadding(Insets.EMPTY);
            this.listView.setCellFactory(this::createListCell);
            Bindings.bindContent(this.listView.getItems(), skinnable.itemsProperty());

            if (emptyMessage != null) {
                var placeholder = new StackPane(new Label(emptyMessage));
                placeholder.getStyleClass().add("notice-pane");
                spinnerPane.setContent(FXUtils.withListPlaceholder(listView, placeholder));
            } else {
                spinnerPane.setContent(listView);
            }
        }

        root.getContent().add(spinnerPane);

        getChildren().setAll(container);
    }

    /// Creates a text action with a leading toolbar icon.
    public static M3Button createToolbarButton2(String text, SVG svg, Runnable onClick) {
        M3Button ret = new M3Button(text, svg.createIcon(20), M3ButtonVariant.TEXT);
        ret.getStyleClass().add("jfx-tool-bar-button");
        ret.setOnAction(e -> onClick.run());
        return ret;
    }

    /// Creates an icon-only toolbar action.
    public static M3IconButton createToolbarButton2(SVG svg, Runnable onClick) {
        M3IconButton button = new M3IconButton(svg.createIcon(20));
        button.getStyleClass().add("jfx-tool-bar-button");
        button.setOnAction(e -> onClick.run());
        return button;
    }

    /// Creates a window navigation action with a tooltip.
    public static M3IconButton createDecoratorButton(String tooltip, SVG svg, Runnable onClick) {
        M3IconButton ret = new M3IconButton();
        ret.getStyleClass().add("jfx-decorator-button");
        ret.setGraphic(svg.createIcon(20));
        FXUtils.installFastTooltip(ret, tooltip);
        ret.setOnAction(e -> onClick.run());
        return ret;
    }

    protected abstract List<Node> initializeToolbar(P skinnable);

    protected M3ListCell<E> createListCell(M3ListView<E> listView) {
        return new GraphicListCell<>(listView) {
            @Override
            protected void updateItem(E item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item instanceof Node node) {
                    setRowGraphic(node);
                    setText(null);
                } else {
                    setRowGraphic(null);
                    setText(null);
                }
            }
        };
    }
}
