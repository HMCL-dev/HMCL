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
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays a game instance with selection, update, settings, and management controls.
public final class GameListCell extends ListCell<GameListItem> {

    /// Provides ripple feedback around the cell content.
    private final RipplerContainer graphic;

    /// Displays the current instance's icon.
    private final ImageContainer imageView;
    /// Displays the instance title, subtitle, tag, and controls.
    private final TwoLineListItem content;

    /// Reflects and changes the repository's selected instance.
    private final JFXRadioButton chkSelected;
    /// Starts an update for an instance that supports updates.
    private final JFXButton btnUpgrade;
    /// Opens the settings for this cell's game instance.
    private final JFXButton btnEditInstance;
    /// Toggles the current instance's management menu.
    private final JFXButton btnManage;

    /// Contains the actions available for the current instance.
    private final HBox right;

    /// Holds the tag text bound to the current instance.
    private final StringProperty tag = new SimpleStringProperty();

    /// Caches the management popup until the cell item or empty state changes.
    private @Nullable JFXPopup managementPopup;

    /// Identifies the item whose actions are captured by the cached popup, or is null if none is cached.
    private @Nullable GameListItem managementPopupItem;

    /// Creates a game instance cell constrained to the supplied list's viewport width.
    public GameListCell(ListView<GameListItem> listView) {
        FXUtils.limitCellWidth(listView, this);

        TwoLineListItem root = this.content = new TwoLineListItem();
        root.getStyleClass().add("md-list-cell");
        root.setPadding(new Insets(8, 8, 8, 0));

        RipplerContainer container = new RipplerContainer(root);
        this.graphic = container;

        {
            this.chkSelected = new JFXRadioButton() {
                /// Selects this cell's game instance when the radio button is enabled and unselected.
                @Override
                public void fire() {
                    if (!isDisable() && !isSelected()) {
                        fireEvent(new ActionEvent());
                        @Nullable GameListItem item = GameListCell.this.getItem();
                        if (item != null) {
                            item.getRepository().setSelectedInstance(item.getGameInstance());
                        }
                    }
                }
            };
        }

        {
            this.imageView = new ImageContainer(32);
            imageView.setMouseTransparent(true);
            HBox leading = new HBox(8, chkSelected, imageView);
            leading.setAlignment(Pos.CENTER_LEFT);
            leading.setPickOnBounds(false);
            TwoLineListItem.setMargin(leading, new Insets(0, 8, 0, 0));
            content.setLeading(leading);

            FXUtils.onChangeAndOperate(tag, tag -> {
                if (StringUtils.isNotBlank(tag))
                    content.getTags().setAll(new Tag(tag));
                else
                    content.getTags().clear();
            });
        }

        {
            this.right = new HBox();
            content.setTrailing(right);
            right.setAlignment(Pos.CENTER_RIGHT);
            right.setPickOnBounds(false);

            this.btnUpgrade = FXUtils.newToggleButton4(SVG.UPDATE);
            btnUpgrade.setOnAction(e -> {
                @Nullable GameListItem item = this.getItem();
                if (item != null)
                    item.update();
            });
            FXUtils.installFastTooltip(btnUpgrade, i18n("instance.update"));
            right.getChildren().add(btnUpgrade);

            this.btnEditInstance = FXUtils.newToggleButton4(SVG.SETTINGS);
            btnEditInstance.setOnAction(e -> {
                @Nullable GameListItem item = this.getItem();
                if (item != null)
                    item.modifyGameSettings();
            });
            FXUtils.installFastTooltip(btnEditInstance, i18n("instance.manage.manage"));
            right.getChildren().add(btnEditInstance);

            this.btnManage = FXUtils.newToggleButton4(SVG.MORE_VERT);
            btnManage.setOnAction(e -> {
                @Nullable GameListItem item = this.getItem();
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
            FXUtils.installFastTooltip(btnManage, i18n("settings.game.management"));
            right.getChildren().add(btnManage);
        }

        root.setCursor(Cursor.HAND);
        container.setOnMouseClicked(e -> {
            @Nullable GameListItem item = getItem();
            if (item == null)
                return;

            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 1) {
                    item.getRepository().setSelectedInstance(item.getGameInstance());
                }
            } else if (e.getButton() == MouseButton.SECONDARY) {
                JFXPopup popup = getManagementPopup(item);
                JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(root, popup);
                // Repeated right-clicks reopen and reposition even during the closing animation
                popup.show(root, vPosition, JFXPopup.PopupHPosition.LEFT, e.getX(), vPosition == JFXPopup.PopupVPosition.TOP ? e.getY() : e.getY() - root.getHeight());
            }
        });
    }

    /// Rebinds the displayed instance and hides the cached menu when the item or empty state changes.
    @Override
    public void updateItem(@Nullable GameListItem item, boolean empty) {
        @Nullable GameListItem oldItem = getItem();
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
            this.right.getChildren().addAll(btnEditInstance, btnManage);
        }
    }

    /// Returns the cached menu for the item, hiding and replacing a menu for a different item.
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

    /// Creates a management popup whose actions operate on the supplied instance item.
    private static JFXPopup createPopup(GameListItem item) {
        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);

        menu.getContent().setAll(
                new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch.test"), item::testGame, popup),
                new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), item::generateLaunchScript, popup),
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
