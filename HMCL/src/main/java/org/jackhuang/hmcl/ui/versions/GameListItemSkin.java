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
                new IconedMenuItem(FXUtils.limitingSize(SVG.rocketLaunchOutline(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.launch.test"), FXUtils.withJFXPopupClosing(() -> currentSkinnable.launch(), popup)),
                new IconedMenuItem(FXUtils.limitingSize(SVG.script(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.launch_script"), FXUtils.withJFXPopupClosing(() -> currentSkinnable.generateLaunchScript(), popup)),
                new MenuSeparator(),
                new IconedMenuItem(FXUtils.limitingSize(SVG.gearOutline(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.manage.manage"), FXUtils.withJFXPopupClosing(() -> currentSkinnable.modifyGameSettings(), popup)),
                new MenuSeparator(),
                new IconedMenuItem(FXUtils.limitingSize(SVG.pencilOutline(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.manage.rename"), FXUtils.withJFXPopupClosing(() -> currentSkinnable.rename(), popup)),
                new IconedMenuItem(FXUtils.limitingSize(SVG.copy(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.manage.duplicate"), FXUtils.withJFXPopupClosing(() -> currentSkinnable.duplicate(), popup)),
                new IconedMenuItem(FXUtils.limitingSize(SVG.deleteOutline(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.manage.remove"), FXUtils.withJFXPopupClosing(() -> currentSkinnable.remove(), popup)),
                new IconedMenuItem(FXUtils.limitingSize(SVG.export(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("modpack.export"), FXUtils.withJFXPopupClosing(() -> currentSkinnable.export(), popup)),
                new MenuSeparator(),
                new IconedMenuItem(FXUtils.limitingSize(SVG.folderOutline(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("folder.game"), FXUtils.withJFXPopupClosing(() -> currentSkinnable.browse(), popup)));
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
            btnUpgrade.setGraphic(FXUtils.limitingSize(SVG.update(Theme.blackFillBinding(), 24, 24), 24, 24));
            runInFX(() -> FXUtils.installFastTooltip(btnUpgrade, i18n("version.update")));
            right.getChildren().add(btnUpgrade);
        }

        {
            JFXButton btnLaunch = new JFXButton();
            btnLaunch.setOnMouseClicked(e -> skinnable.launch());
            btnLaunch.getStyleClass().add("toggle-icon4");
            BorderPane.setAlignment(btnLaunch, Pos.CENTER);
            btnLaunch.setGraphic(FXUtils.limitingSize(SVG.rocketLaunchOutline(Theme.blackFillBinding(), 24, 24), 24, 24));
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
            btnManage.setGraphic(FXUtils.limitingSize(SVG.dotsVertical(Theme.blackFillBinding(), 24, 24), 24, 24));
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
