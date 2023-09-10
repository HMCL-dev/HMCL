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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXRadioButton;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.MenuSeparator;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.Lazy;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameListItemSkin extends SkinBase<GameListItem> {
    private static GameListItem currentSkinnable;
    private static Lazy<JFXPopup> popup = new Lazy<>(() -> {
        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);

        menu.getContent().setAll(
                new IconedMenuItem(SVG.ROCKET_LAUNCH_OUTLINE, i18n("version.launch.test"), () -> currentSkinnable.launch(), popup),
                new IconedMenuItem(SVG.SCRIPT, i18n("version.launch_script"), () -> currentSkinnable.generateLaunchScript(), popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.GEAR_OUTLINE, i18n("version.manage.manage"), () -> currentSkinnable.modifyGameSettings(), popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.PENCIL_OUTLINE, i18n("version.manage.rename"), () -> currentSkinnable.rename(), popup),
                new IconedMenuItem(SVG.COPY, i18n("version.manage.duplicate"), () -> currentSkinnable.duplicate(), popup),
                new IconedMenuItem(SVG.DELETE_OUTLINE, i18n("version.manage.remove"), () -> currentSkinnable.remove(), popup),
                new IconedMenuItem(SVG.EXPORT, i18n("modpack.export"), () -> currentSkinnable.export(), popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.FOLDER_OUTLINE, i18n("folder.game"), () -> currentSkinnable.browse(), popup));
        return popup;
    });

    public GameListItemSkin(GameListItem skinnable) {
        super(skinnable);

        BorderPane root = new BorderPane();

        JFXRadioButton chkSelected = new JFXRadioButton();
        BorderPane.setAlignment(chkSelected, Pos.CENTER);
        chkSelected.setUserData(skinnable);
        chkSelected.selectedProperty().bindBidirectional(skinnable.selectedProperty());
        chkSelected.setToggleGroup(skinnable.getToggleGroup());
        root.setLeft(chkSelected);

        GameItem gameItem = new GameItem(skinnable.getProfile(), skinnable.getVersion());
        gameItem.setMouseTransparent(true);
        root.setCenter(gameItem);

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);
        if (skinnable.canUpdate()) {
            JFXButton btnUpgrade = new JFXButton();
            btnUpgrade.setOnMouseClicked(e -> skinnable.update());
            btnUpgrade.getStyleClass().add("toggle-icon4");
            btnUpgrade.setGraphic(FXUtils.limitingSize(SVG.UPDATE.createIcon(Theme.blackFill(), 24, 24), 24, 24));
            runInFX(() -> FXUtils.installFastTooltip(btnUpgrade, i18n("version.update")));
            right.getChildren().add(btnUpgrade);
        }

        {
            JFXButton btnLaunch = new JFXButton();
            btnLaunch.setOnMouseClicked(e -> skinnable.launch());
            btnLaunch.getStyleClass().add("toggle-icon4");
            BorderPane.setAlignment(btnLaunch, Pos.CENTER);
            btnLaunch.setGraphic(FXUtils.limitingSize(SVG.ROCKET_LAUNCH_OUTLINE.createIcon(Theme.blackFill(), 24, 24), 24, 24));
            runInFX(() -> FXUtils.installFastTooltip(btnLaunch, i18n("version.launch.test")));
            right.getChildren().add(btnLaunch);
        }

        {
            JFXButton btnManage = new JFXButton();
            btnManage.setOnMouseClicked(e -> {
                currentSkinnable = skinnable;
                popup.get().show(root, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, root.getHeight());
            });
            btnManage.getStyleClass().add("toggle-icon4");
            BorderPane.setAlignment(btnManage, Pos.CENTER);
            btnManage.setGraphic(FXUtils.limitingSize(SVG.DOTS_VERTICAL.createIcon(Theme.blackFill(), 24, 24), 24, 24));
            runInFX(() -> FXUtils.installFastTooltip(btnManage, i18n("settings.game.management")));
            right.getChildren().add(btnManage);
        }

        root.setRight(right);

        root.getStyleClass().add("md-list-cell");
        root.setStyle("-fx-padding: 8 8 8 0");

        RipplerContainer container = new RipplerContainer(root);
        getChildren().setAll(container);

        root.setCursor(Cursor.HAND);
        container.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 1) {
                    skinnable.modifyGameSettings();
                }
            } else if (e.getButton() == MouseButton.SECONDARY) {
                currentSkinnable = skinnable;
                popup.get().show(root, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, e.getX(), e.getY());
            }
        });
    }
}
