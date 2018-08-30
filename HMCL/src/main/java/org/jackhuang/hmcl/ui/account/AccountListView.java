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
package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.MappedObservableList;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AccountListView extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(i18n("account.manage"));

    public AccountListView(AccountListViewModel viewModel) {
        ScrollPane scrollPane = new ScrollPane();
        {
            scrollPane.setFitToWidth(true);

            VBox accountList = new VBox();
            accountList.maxWidthProperty().bind(scrollPane.widthProperty());
            accountList.setSpacing(10);
            accountList.setStyle("-fx-padding: 10 10 10 10;");

            Bindings.bindContent(accountList.getChildren(),
                    MappedObservableList.create(viewModel.itemsProperty(), AccountListItemView::new));

            scrollPane.setContent(accountList);
        }

        VBox vBox = new VBox();
        {
            vBox.setAlignment(Pos.BOTTOM_RIGHT);
            vBox.setPickOnBounds(false);
            vBox.setPadding(new Insets(15));
            vBox.setSpacing(15);

            JFXButton btnAdd = new JFXButton();
            FXUtils.setLimitWidth(btnAdd, 40);
            FXUtils.setLimitHeight(btnAdd, 40);
            btnAdd.getStyleClass().setAll("jfx-button-raised-round");
            btnAdd.setButtonType(JFXButton.ButtonType.RAISED);
            btnAdd.setGraphic(SVG.plus(Theme.whiteFillBinding(), -1, -1));
            btnAdd.setOnMouseClicked(e -> viewModel.addNewAccount());

            vBox.getChildren().setAll(btnAdd);
        }

        getChildren().setAll(scrollPane, vBox);
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }
}
