/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSpinner;
import com.jfoenix.effects.JFXDepthManager;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.MappedObservableList;
import org.jackhuang.hmcl.util.i18n.I18n;

public class GameListView extends BorderPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(I18n.i18n("version.manage"));

    private static Node wrap(Node node) {
        StackPane stackPane = new StackPane();
        stackPane.setPadding(new Insets(0, 5, 0, 2));
        stackPane.getChildren().setAll(node);
        return stackPane;
    }

    public GameListView(GameListViewModel viewModel) {
        {
            HBox toolbar = new HBox();
            toolbar.getStyleClass().setAll("jfx-tool-bar-second");
            JFXDepthManager.setDepth(toolbar, 1);
            toolbar.setPickOnBounds(false);

            JFXButton btnAddNewGame = new JFXButton();
            btnAddNewGame.getStyleClass().add("jfx-tool-bar-button");
            btnAddNewGame.textFillProperty().bind(Theme.foregroundFillBinding());
            btnAddNewGame.setGraphic(wrap(SVG.plus(Theme.foregroundFillBinding(), -1, -1)));
            btnAddNewGame.setText(I18n.i18n("install.new_game"));
            btnAddNewGame.setOnMouseClicked(e -> viewModel.addNewGame());
            toolbar.getChildren().add(btnAddNewGame);

            JFXButton btnImportModpack = new JFXButton();
            btnImportModpack.getStyleClass().add("jfx-tool-bar-button");
            btnImportModpack.textFillProperty().bind(Theme.foregroundFillBinding());
            btnImportModpack.setGraphic(wrap(SVG.importIcon(Theme.foregroundFillBinding(), -1, -1)));
            btnImportModpack.setText(I18n.i18n("install.modpack"));
            btnImportModpack.setOnMouseClicked(e -> viewModel.importModpack());
            toolbar.getChildren().add(btnImportModpack);

            JFXButton btnRefresh = new JFXButton();
            btnRefresh.getStyleClass().add("jfx-tool-bar-button");
            btnRefresh.textFillProperty().bind(Theme.foregroundFillBinding());
            btnRefresh.setGraphic(wrap(SVG.refresh(Theme.foregroundFillBinding(), -1, -1)));
            btnRefresh.setText(I18n.i18n("button.refresh"));
            btnRefresh.setOnMouseClicked(e -> viewModel.refresh());
            toolbar.getChildren().add(btnRefresh);

            JFXButton btnModify = new JFXButton();
            btnModify.getStyleClass().add("jfx-tool-bar-button");
            btnModify.textFillProperty().bind(Theme.foregroundFillBinding());
            btnModify.setGraphic(wrap(SVG.gear(Theme.foregroundFillBinding(), -1, -1)));
            btnModify.setText(I18n.i18n("settings.type.global.manage"));
            btnModify.setOnMouseClicked(e -> viewModel.modifyGlobalGameSettings());
            toolbar.getChildren().add(btnModify);

            setTop(toolbar);
        }

        {
            StackPane center = new StackPane();

            JFXSpinner spinner = new JFXSpinner();
            spinner.getStyleClass().setAll("first-spinner");

            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setFitToWidth(true);

            VBox gameList = new VBox();
            gameList.maxWidthProperty().bind(scrollPane.widthProperty());
            gameList.setSpacing(10);
            gameList.setStyle("-fx-padding: 10 10 10 10;");

            Bindings.bindContent(gameList.getChildren(),
                    MappedObservableList.create(viewModel.itemsProperty(), model -> {
                        GameListItemView view = new GameListItemView(model);
                        return view;
                    }));

            scrollPane.setContent(gameList);

            FXUtils.onChangeAndOperate(viewModel.loadingProperty(),
                    loading -> center.getChildren().setAll(loading ? spinner : scrollPane));

            setCenter(center);
        }
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }
}
