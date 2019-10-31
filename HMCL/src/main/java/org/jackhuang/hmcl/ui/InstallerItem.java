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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.effects.JFXDepthManager;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author huangyuhui
 */
public class InstallerItem extends BorderPane {

    public InstallerItem(String artifact, String version, @Nullable Runnable upgrade, @Nullable Consumer<InstallerItem> deleteCallback) {
        getStyleClass().add("two-line-list-item");
        setStyle("-fx-background-radius: 2; -fx-background-color: white; -fx-padding: 8;");
        JFXDepthManager.setDepth(this, 1);

        if (version != null) {
            TwoLineListItem item = new TwoLineListItem();
            item.setTitle(artifact);
            item.setSubtitle(i18n("archive.version") + ": " + version);
            setCenter(item);
        } else {
            Label label = new Label(artifact);
            label.setStyle("-fx-font-size: 15px;");
            BorderPane.setAlignment(label, Pos.CENTER_LEFT);
            setCenter(label);
        }

        {
            HBox hBox = new HBox();

            if (upgrade != null) {
                JFXButton upgradeButton = new JFXButton();
                upgradeButton.setGraphic(SVG.update(Theme.blackFillBinding(), -1, -1));
                upgradeButton.getStyleClass().add("toggle-icon4");
                FXUtils.installFastTooltip(upgradeButton, i18n("install.change_version"));
                upgradeButton.setOnMouseClicked(e -> upgrade.run());
                hBox.getChildren().add(upgradeButton);
            }

            if (deleteCallback != null) {
                JFXButton deleteButton = new JFXButton();
                deleteButton.setGraphic(SVG.close(Theme.blackFillBinding(), -1, -1));
                deleteButton.getStyleClass().add("toggle-icon4");
                deleteButton.setOnMouseClicked(e -> deleteCallback.accept(this));
                hBox.getChildren().add(deleteButton);
            }

            hBox.setAlignment(Pos.CENTER_RIGHT);
            setRight(hBox);
        }
    }

}
