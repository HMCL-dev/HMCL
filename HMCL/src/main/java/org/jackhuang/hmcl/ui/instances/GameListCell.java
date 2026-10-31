// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXRadioButton;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class GameListCell extends ListCell<GameListItem> {

    private final RipplerContainer graphic;

    private final ImageContainer imageView;
    private final TwoLineListItem content;

    private final JFXRadioButton chkSelected;
    private final JFXButton btnUpgrade;
    private final JFXButton btnLaunch;
    private final JFXButton btnManage;

    private final HBox right;

    private final StringProperty tag = new SimpleStringProperty();

    private JFXPopup managementPopup;

    private GameListItem managementPopupItem;

    public GameListCell() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("md-list-cell");
        root.setPadding(new Insets(8, 8, 8, 0));

        RipplerContainer container = new RipplerContainer(root);
        this.graphic = container;

        {
            this.chkSelected = new JFXRadioButton() {
                @Override
                public void fire() {
                    if (!isDisable() && !isSelected()) {
                        fireEvent(new ActionEvent());
                        GameListItem item = GameListCell.this.getItem();
                        if (item != null) {
                            item.getRepository().setSelectedInstance(item.getGameInstance());
                        }
                    }
                }
            };
            root.setLeft(chkSelected);
            BorderPane.setAlignment(chkSelected, Pos.CENTER);
        }

        {
            HBox center = new HBox();
            BorderPane.setMargin(center, new Insets(0, 0, 0, 8));
            center.setMouseTransparent(true);
            root.setCenter(center);
            center.setPrefWidth(Region.USE_PREF_SIZE);
            center.setSpacing(8);
            center.setAlignment(Pos.CENTER_LEFT);

            this.imageView = new ImageContainer(32);

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

            this.btnUpgrade = FXUtils.newToggleButton4(SVG.UPDATE);
            btnUpgrade.setOnAction(e -> {
                GameListItem item = this.getItem();
                if (item != null)
                    item.update();
            });
            FXUtils.installFastTooltip(btnUpgrade, i18n("instance.update"));
            right.getChildren().add(btnUpgrade);

            this.btnLaunch = FXUtils.newToggleButton4(SVG.ROCKET_LAUNCH);
            btnLaunch.setOnAction(e -> {
                GameListItem item = this.getItem();
                if (item != null)
                    item.testGame();
            });
            BorderPane.setAlignment(btnLaunch, Pos.CENTER);
            FXUtils.installFastTooltip(btnLaunch, i18n("instance.launch.test"));
            right.getChildren().add(btnLaunch);

            this.btnManage = FXUtils.newToggleButton4(SVG.MORE_VERT);
            btnManage.setOnAction(e -> {
                GameListItem item = this.getItem();
                if (item == null)
                    return;

                JFXPopup popup = getManagementPopup(item);
                if (popup.isShowing()) {
                    popup.hide();
                    return;
                }

                JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(root, popup);
                boolean below = vPosition == JFXPopup.PopupVPosition.TOP;
                Point2D offset = root.localToScene(0, 0).subtract(btnManage.localToScene(0, 0));
                double offsetX = offset.getX() + root.getWidth() - btnManage.getWidth();
                double offsetY = offset.getY() + (below ? root.getHeight() : -btnManage.getHeight());
                if (btnManage.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT)
                    offsetX = -offsetX;
                // Only this button bypasses auto-hide, so its action can toggle the menu.
                // Keep the popup aligned to the cell while making the button its owner.
                popup.show(btnManage, vPosition, JFXPopup.PopupHPosition.RIGHT, offsetX, offsetY, true);
            });
            BorderPane.setAlignment(btnManage, Pos.CENTER);
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
                JFXPopup popup = getManagementPopup(item);
                JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(root, popup);
                // Repeated right-clicks reopen and reposition even during the closing animation
                popup.show(root, vPosition, JFXPopup.PopupHPosition.LEFT, e.getX(), vPosition == JFXPopup.PopupVPosition.TOP ? e.getY() : e.getY() - root.getHeight());
            }
        });
    }

    @Override
    public void updateItem(GameListItem item, boolean empty) {
        GameListItem oldItem = getItem();
        boolean oldEmpty = isEmpty();

        super.updateItem(item, empty);

        if (oldItem == item && oldEmpty == empty) return;

        if (managementPopup != null) {
            managementPopup.hide();
            managementPopup = null;
            managementPopupItem = null;
        }

        this.graphic.resetFeedback();

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

            this.chkSelected.selectedProperty().bind(item.selectedProperty());
            this.imageView.imageProperty().bind(item.imageProperty());
            this.content.titleProperty().bind(item.titleProperty());
            this.content.subtitleProperty().bind(item.subtitleProperty());
            this.tag.bind(item.tagProperty());
            if (item.canUpdate())
                this.right.getChildren().add(btnUpgrade);
            this.right.getChildren().addAll(btnLaunch, btnManage);
        }
    }

    private JFXPopup getManagementPopup(GameListItem item) {
        if (managementPopup == null || managementPopupItem != item) {
            if (managementPopup != null) {
                managementPopup.hide();
            }
            managementPopup = createPopup(item);
            managementPopupItem = item;
        }
        return managementPopup;
    }

    private static JFXPopup createPopup(GameListItem item) {
        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);

        menu.getContent().setAll(
                new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch.test"), item::testGame, popup),
                new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), item::generateLaunchScript, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.SETTINGS, i18n("instance.manage.manage"), item::modifyGameSettings, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.EDIT, i18n("instance.manage.rename"), item::rename, popup),
                new IconedMenuItem(SVG.FOLDER_COPY, i18n("instance.manage.duplicate"), item::duplicate, popup),
                new IconedMenuItem(SVG.DELETE, i18n("instance.manage.remove"), item::remove, popup),
                new IconedMenuItem(SVG.OUTPUT, i18n("modpack.export"), item::export, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.FOLDER_OPEN, i18n("folder.game"), item::browse, popup));
        return popup;
    }
}
