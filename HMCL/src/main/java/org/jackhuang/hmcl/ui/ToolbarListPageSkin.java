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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import java.util.List;

public abstract class ToolbarListPageSkin<E, P extends ListPageBase<E>> extends SkinBase<P> {

    protected final JFXListView<E> listView;

    public ToolbarListPageSkin(P skinnable) {
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
            root.getContent().add(toolbar);
        }

        SpinnerPane spinnerPane = new SpinnerPane();
        spinnerPane.loadingProperty().bind(skinnable.loadingProperty());
        spinnerPane.failedReasonProperty().bind(skinnable.failedReasonProperty());
        spinnerPane.onFailedActionProperty().bind(skinnable.onFailedActionProperty());

        ComponentList.setVgrow(spinnerPane, Priority.ALWAYS);

        {
            this.listView = new JFXListView<>();
            this.listView.setPadding(Insets.EMPTY);
            this.listView.setCellFactory(listView -> createListCell((JFXListView<E>) listView));
            this.listView.getStyleClass().add("no-horizontal-scrollbar");
            Bindings.bindContent(this.listView.getItems(), skinnable.itemsProperty());
            FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

            spinnerPane.setContent(listView);
        }

        root.getContent().add(spinnerPane);

        getChildren().setAll(container);
    }

    public static JFXButton createToolbarButton2(String text, SVG svg, Runnable onClick) {
        JFXButton ret = new JFXButton();
        ret.getStyleClass().add("jfx-tool-bar-button");
        ret.setGraphic(svg.createIcon(20));
        ret.setText(text);
        ret.setOnAction(e -> onClick.run());
        return ret;
    }

    public static JFXButton createDecoratorButton(String tooltip, SVG svg, Runnable onClick) {
        JFXButton ret = new JFXButton();
        ret.getStyleClass().add("jfx-decorator-button");
        ret.setGraphic(svg.createIcon(20));
        FXUtils.installFastTooltip(ret, tooltip);
        ret.setOnAction(e -> onClick.run());
        return ret;
    }

    protected abstract List<Node> initializeToolbar(P skinnable);

    protected ListCell<E> createListCell(JFXListView<E> listView) {
        return new ListCell<>() {
            @Override
            protected void updateItem(E item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item instanceof Node node) {
                    setGraphic(node);
                    setText(null);
                } else {
                    setGraphic(null);
                    setText(null);
                }
            }
        };
    }
}
