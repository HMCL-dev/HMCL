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
import com.jfoenix.effects.JFXDepthManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author huangyuhui
 */
public class InstallerItem extends BorderPane {

    public InstallerItem(String libraryId, String libraryVersion, @Nullable Runnable upgrade, @Nullable Consumer<InstallerItem> deleteCallback) {
        getStyleClass().addAll("two-line-list-item", "card");
        JFXDepthManager.setDepth(this, 1);

        String[] urls = new String[]{"/assets/img/grass.png", "/assets/img/fabric.png", "/assets/img/forge.png", "/assets/img/chicken.png", "/assets/img/command.png"};
        String[] libraryIds = new String[]{"game", "fabric", "forge", "liteloader", "optifine"};

        boolean regularLibrary = false;
        for (int i = 0; i < 5; ++i) {
            if (libraryIds[i].equals(libraryId)) {
                setLeft(FXUtils.limitingSize(new ImageView(new Image(urls[i], 32, 32, true, true)), 32, 32));
                Label label = new Label();
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                BorderPane.setMargin(label, new Insets(0, 0, 0, 8));
                if (libraryVersion == null) {
                    label.setText(i18n("install.installer.not_installed", i18n("install.installer." + libraryId)));
                } else {
                    label.setText(i18n("install.installer.version", i18n("install.installer." + libraryId), libraryVersion));
                }
                setCenter(label);
                regularLibrary = true;
                break;
            }
        }

        if (!regularLibrary) {
            String title = I18n.hasKey("install.installer." + libraryId) ? i18n("install.installer." + libraryId) : libraryId;
            if (libraryVersion != null) {
                TwoLineListItem item = new TwoLineListItem();
                item.setTitle(title);
                item.setSubtitle(i18n("archive.version") + ": " + libraryVersion);
                setCenter(item);
            } else {
                Label label = new Label();
                label.setStyle("-fx-font-size: 15px;");
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                setCenter(label);
            }
        }

        {
            HBox hBox = new HBox();

            if (upgrade != null) {
                JFXButton upgradeButton = new JFXButton();
                if (libraryVersion == null) {
                    upgradeButton.setGraphic(SVG.arrowRight(Theme.blackFillBinding(), -1, -1));
                } else {
                    upgradeButton.setGraphic(SVG.update(Theme.blackFillBinding(), -1, -1));
                }
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
