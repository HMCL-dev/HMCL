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
import com.jfoenix.controls.JFXScrollPane;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import java.util.List;

public abstract class ToolbarListPageSkin<T extends ListPageBase<? extends Node>> extends SkinBase<T> {

    public ToolbarListPageSkin(T skinnable) {
        super(skinnable);

        SpinnerPane spinnerPane = new SpinnerPane();
        spinnerPane.loadingProperty().bind(skinnable.loadingProperty());
        spinnerPane.failedReasonProperty().bind(skinnable.failedReasonProperty());
        spinnerPane.onActionProperty().bind(skinnable.onActionProperty());
        spinnerPane.getStyleClass().add("large-spinner-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");
        StackPane.setMargin(root, new Insets(10));

        List<Node> toolbarButtons = initializeToolbar(skinnable);
        if (!toolbarButtons.isEmpty()) {
            HBox toolbar = new HBox();
            toolbar.setAlignment(Pos.CENTER_LEFT);
            toolbar.setPickOnBounds(false);
            toolbar.getChildren().setAll(toolbarButtons);
            root.getContent().add(toolbar);
        }

        {
            ScrollPane scrollPane = new ScrollPane();
            ComponentList.setVgrow(scrollPane, Priority.ALWAYS);
            scrollPane.setFitToWidth(true);

            VBox content = new VBox();

            Bindings.bindContent(content.getChildren(), skinnable.itemsProperty());

            scrollPane.setContent(content);
            JFXScrollPane.smoothScrolling(scrollPane);

            root.getContent().add(scrollPane);
        }

        spinnerPane.setContent(root);

        getChildren().setAll(spinnerPane);
    }

    public static Node wrap(Node node) {
        StackPane stackPane = new StackPane();
        stackPane.setPadding(new Insets(0, 5, 0, 2));
        stackPane.getChildren().setAll(node);
        return stackPane;
    }

    public static JFXButton createToolbarButton(String text, SVG.SVGIcon creator, Runnable onClick) {
        JFXButton ret = new JFXButton();
        ret.getStyleClass().add("jfx-tool-bar-button");
        ret.textFillProperty().bind(Theme.foregroundFillBinding());
        ret.setGraphic(wrap(creator.createIcon(Theme.foregroundFillBinding(), -1, -1)));
        ret.setText(text);
        ret.setOnMouseClicked(e -> onClick.run());
        return ret;
    }

    public static JFXButton createToolbarButton2(String text, SVG.SVGIcon creator, Runnable onClick) {
        JFXButton ret = new JFXButton();
        ret.getStyleClass().add("jfx-tool-bar-button");
        ret.setGraphic(wrap(creator.createIcon(Theme.blackFillBinding(), -1, -1)));
        ret.setText(text);
        ret.setOnMouseClicked(e -> onClick.run());
        return ret;
    }

    public static JFXButton createDecoratorButton(String tooltip, SVG.SVGIcon creator, Runnable onClick) {
        JFXButton ret = new JFXButton();
        ret.getStyleClass().add("jfx-decorator-button");
        ret.textFillProperty().bind(Theme.foregroundFillBinding());
        ret.setGraphic(wrap(creator.createIcon(Theme.foregroundFillBinding(), -1, -1)));
        FXUtils.installFastTooltip(ret, tooltip);
        ret.setOnMouseClicked(e -> onClick.run());
        return ret;
    }

    protected abstract List<Node> initializeToolbar(T skinnable);
}
