/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class GameListCell extends ListCell<GameListItem> {

    private final Region graphic;

    private final ImageView imageView;
    private final TwoLineListItem content;

    private final JFXRadioButton chkSelected;
    private final JFXButton btnUpgrade;
    private final JFXButton btnLaunch;
    private final JFXButton btnManage;

    private final HBox right;

    private final StringProperty tag = new SimpleStringProperty();

    public GameListCell() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("md-list-cell");
        root.setPadding(new Insets(8, 8, 8, 0));

        RipplerContainer container = new RipplerContainer(root);
        this.graphic = container;

        {
            this.chkSelected = new JFXRadioButton();
            root.setLeft(chkSelected);
            BorderPane.setAlignment(chkSelected, Pos.CENTER);
        }

        {
            HBox center = new HBox();
            root.setCenter(center);
            center.setSpacing(8);
            center.setAlignment(Pos.CENTER_LEFT);

            StackPane imageViewContainer = new StackPane();
            FXUtils.setLimitWidth(imageViewContainer, 32);
            FXUtils.setLimitHeight(imageViewContainer, 32);

            this.imageView = new ImageView();
            FXUtils.limitSize(imageView, 32, 32);
            imageViewContainer.getChildren().setAll(imageView);

            this.content = new TwoLineListItem();
            BorderPane.setAlignment(content, Pos.CENTER);

            FXUtils.onChangeAndOperate(tag, tag -> {
                content.getTags().clear();
                if (StringUtils.isNotBlank(tag))
                    content.addTag(tag);
            });

            center.getChildren().setAll(imageView, content);
        }

        {
            this.right = new HBox();
            root.setRight(right);

            right.setAlignment(Pos.CENTER_RIGHT);

            this.btnUpgrade = new JFXButton();
            btnUpgrade.setOnAction(e -> {
                GameListItem item = this.getItem();
                if (item != null)
                    item.update();
            });
            btnUpgrade.getStyleClass().add("toggle-icon4");
            btnUpgrade.setGraphic(FXUtils.limitingSize(SVG.UPDATE.createIcon(24), 24, 24));
            FXUtils.installFastTooltip(btnUpgrade, i18n("version.update"));
            right.getChildren().add(btnUpgrade);

            this.btnLaunch = new JFXButton();
            btnLaunch.setOnAction(e -> {
                GameListItem item = this.getItem();
                if (item != null)
                    item.testGame();
            });
            btnLaunch.getStyleClass().add("toggle-icon4");
            BorderPane.setAlignment(btnLaunch, Pos.CENTER);
            btnLaunch.setGraphic(FXUtils.limitingSize(SVG.ROCKET_LAUNCH.createIcon(24), 24, 24));
            FXUtils.installFastTooltip(btnLaunch, i18n("version.launch.test"));
            right.getChildren().add(btnLaunch);

            this.btnManage = new JFXButton();
            btnManage.setOnAction(e -> {
                GameListItem item = this.getItem();
                if (item == null)
                    return;

                preparePopupMenu(item);

                JFXPopup popup = getPopup(getListView());
                JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(root, popup);
                popup.show(root, vPosition, JFXPopup.PopupHPosition.RIGHT, 0, vPosition == JFXPopup.PopupVPosition.TOP ? root.getHeight() : -root.getHeight());
            });
            btnManage.getStyleClass().add("toggle-icon4");
            BorderPane.setAlignment(btnManage, Pos.CENTER);
            btnManage.setGraphic(FXUtils.limitingSize(SVG.MORE_VERT.createIcon(24), 24, 24));
            FXUtils.installFastTooltip(btnManage, i18n("settings.game.management"));
            right.getChildren().add(btnManage);
        }

        root.setCursor(Cursor.HAND);
        container.setOnMouseClicked(e -> {
            GameListItem item = getItem();
            if (item == null)
                return;

            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 1) {
                    item.modifyGameSettings();
                }
            } else if (e.getButton() == MouseButton.SECONDARY) {
                preparePopupMenu(item);

                JFXPopup popup = getPopup(getListView());
                JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(root, popup);
                popup.show(root, vPosition, JFXPopup.PopupHPosition.LEFT, e.getX(), vPosition == JFXPopup.PopupVPosition.TOP ? e.getY() : e.getY() - root.getHeight());
            }
        });
    }

    @Override
    public void updateItem(GameListItem item, boolean empty) {
        super.updateItem(item, empty);

        this.imageView.imageProperty().unbind();
        this.content.titleProperty().unbind();
        this.content.subtitleProperty().unbind();
        this.tag.unbind();
        this.right.getChildren().clear();
        this.chkSelected.selectedProperty().unbind();

        if (empty || item == null) {
            setGraphic(null);
        } else {
            setGraphic(this.graphic);

            this.chkSelected.selectedProperty().bindBidirectional(item.selectedProperty());

            this.imageView.imageProperty().bind(item.imageProperty());
            this.content.titleProperty().bind(item.titleProperty());
            this.content.subtitleProperty().bind(item.subtitleProperty());
            this.tag.bind(item.tagProperty());
            if (item.canUpdate()) {
                this.right.getChildren().add(btnUpgrade);
            }
            this.right.getChildren().addAll(btnLaunch, btnManage);
        }
    }

    // Popup Menu

    private static final String POPUP_ITEM_KEY = GameListCell.class.getName() + ".popup.item";

    private void preparePopupMenu(GameListItem item) {
        this.getListView().getProperties().put(POPUP_ITEM_KEY, item);
    }

    private static Runnable getAction(ListView<GameListItem> listView, Consumer<GameListItem> action) {
        return () -> {
            if (listView.getProperties().get(POPUP_ITEM_KEY) instanceof GameListItem item) {
                action.accept(item);
            }
        };
    }

    private static JFXPopup getPopup(ListView<GameListItem> listView) {
        return (JFXPopup) listView.getProperties().computeIfAbsent(GameListCell.class.getName() + ".popup", k -> {
            PopupMenu menu = new PopupMenu();
            JFXPopup popup = new JFXPopup(menu);

            menu.getContent().setAll(
                    new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("version.launch.test"), getAction(listView, GameListItem::testGame), popup),
                    new IconedMenuItem(SVG.SCRIPT, i18n("version.launch_script"), getAction(listView, GameListItem::generateLaunchScript), popup),
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.SETTINGS, i18n("version.manage.manage"), getAction(listView, GameListItem::modifyGameSettings), popup),
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.EDIT, i18n("version.manage.rename"), getAction(listView, GameListItem::rename), popup),
                    new IconedMenuItem(SVG.FOLDER_COPY, i18n("version.manage.duplicate"), getAction(listView, GameListItem::duplicate), popup),
                    new IconedMenuItem(SVG.DELETE, i18n("version.manage.remove"), getAction(listView, GameListItem::remove), popup),
                    new IconedMenuItem(SVG.OUTPUT, i18n("modpack.export"), getAction(listView, GameListItem::export), popup),
                    new MenuSeparator(),
                    new IconedMenuItem(SVG.FOLDER_OPEN, i18n("folder.game"), getAction(listView, GameListItem::browse), popup));

            popup.setOnHidden(event -> listView.getProperties().remove(POPUP_ITEM_KEY));
            return popup;
        });
    }
}
