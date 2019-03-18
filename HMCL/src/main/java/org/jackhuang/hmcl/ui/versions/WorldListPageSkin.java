/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXScrollPane;
import com.jfoenix.effects.JFXDepthManager;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import static org.jackhuang.hmcl.ui.SVG.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldListPageSkin extends SkinBase<WorldListPage> {

    public WorldListPageSkin(WorldListPage skinnable) {
        super(skinnable);

        SpinnerPane spinnerPane = new SpinnerPane();
        spinnerPane.getStyleClass().add("large-spinner-pane");

        BorderPane contentPane = new BorderPane();

        {
            HBox toolbar = new HBox();
            toolbar.getStyleClass().add("jfx-tool-bar-second");
            JFXDepthManager.setDepth(toolbar, 1);
            toolbar.setPickOnBounds(false);

            JFXCheckBox chkShowAll = new JFXCheckBox();
            chkShowAll.getStyleClass().add("jfx-tool-bar-checkbox");
            chkShowAll.textFillProperty().bind(Theme.foregroundFillBinding());
            chkShowAll.setText(i18n("world.show_all"));
            chkShowAll.selectedProperty().bindBidirectional(skinnable.showAllProperty());
            toolbar.getChildren().add(chkShowAll);

            JFXButton btnRefresh = new JFXButton();
            btnRefresh.getStyleClass().add("jfx-tool-bar-button");
            btnRefresh.textFillProperty().bind(Theme.foregroundFillBinding());
            btnRefresh.setGraphic(wrap(SVG.refresh(Theme.foregroundFillBinding(), -1, -1)));
            btnRefresh.setText(i18n("button.refresh"));
            btnRefresh.setOnMouseClicked(e -> skinnable.refresh());
            toolbar.getChildren().add(btnRefresh);

            JFXButton btnAdd = new JFXButton();
            btnAdd.getStyleClass().add("jfx-tool-bar-button");
            btnAdd.textFillProperty().bind(Theme.foregroundFillBinding());
            btnAdd.setGraphic(wrap(SVG.plus(Theme.foregroundFillBinding(), -1, -1)));
            btnAdd.setText(i18n("world.add"));
            btnAdd.setOnMouseClicked(e -> skinnable.add());
            toolbar.getChildren().add(btnAdd);

            contentPane.setTop(toolbar);
        }

        {
            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setFitToWidth(true);

            VBox content = new VBox();
            content.setSpacing(10);
            content.setPadding(new Insets(10));

            Bindings.bindContent(content.getChildren(), skinnable.itemsProperty());

            scrollPane.setContent(content);
            JFXScrollPane.smoothScrolling(scrollPane);

            contentPane.setCenter(scrollPane);
        }

        spinnerPane.loadingProperty().bind(skinnable.loadingProperty());
        spinnerPane.setContent(contentPane);

        getChildren().setAll(spinnerPane);
    }
}
