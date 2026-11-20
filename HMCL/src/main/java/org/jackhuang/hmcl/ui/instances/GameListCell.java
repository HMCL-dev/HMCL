// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import org.glavo.m3fx.controls.M3Divider;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3ListView;
import org.glavo.m3fx.controls.M3RadioButton;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays a game instance with selection, update, settings, and management controls.
public final class GameListCell extends GraphicListCell<GameListItem> {

    /// Provides ripple feedback around the cell content.
    private final RipplerContainer graphic;

    /// Displays the current instance's icon.
    private final ImageContainer imageView;
    /// Displays the instance title, subtitle, tag, and controls.
    private final TwoLineListItem content;

    /// Starts an update for an instance that supports updates.
    private final M3IconButton btnUpgrade;
    /// Opens the settings for this cell's game instance.
    private final M3IconButton btnEditInstance;
    /// Toggles the current instance's management menu.
    private final M3IconButton btnManage;

    /// Contains the actions available for the current instance.
    private final HBox right;

    /// Holds the tag text bound to the current instance.
    private final StringProperty tag = new SimpleStringProperty();

    /// Caches the management popup until the cell item or empty state changes.
    private @Nullable PopupMenu managementPopup;

    /// Identifies the item whose actions are captured by the cached popup, or is null if none is cached.
    private @Nullable GameListItem managementPopupItem;

    /// Creates a game instance cell constrained to the supplied list's viewport width.
    public GameListCell(M3ListView<GameListItem> listView) {
        super(listView);

        TwoLineListItem root = this.content = new TwoLineListItem();
        root.getStyleClass().add("md-list-cell");
        root.setPadding(new Insets(8, 8, 8, 0));

        RipplerContainer container = new RipplerContainer(root);
        this.graphic = container;

        {
            M3RadioButton chkSelected = new M3RadioButton();
            // Grouped radios ignore repeat activation of the selected instance.
            chkSelected.setToggleGroup(new ToggleGroup());
            var instanceSelected = itemProperty().flatMap(GameListItem::selectedProperty).orElse(false);
            instanceSelected.subscribe(chkSelected::setSelected);
            chkSelected.setOnAction(event -> {
                @Nullable GameListItem item = getItem();
                if (item != null)
                    item.getRepository().setSelectedInstance(item.getGameInstance());
                chkSelected.setSelected(instanceSelected.getValue());
            });

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

            this.btnUpgrade = new M3IconButton(SVG.UPDATE.createIcon());
            btnUpgrade.setOnAction(e -> {
                @Nullable GameListItem item = this.getItem();
                if (item != null)
                    item.update();
            });
            FXUtils.installFastTooltip(btnUpgrade, i18n("instance.update"));
            right.getChildren().add(btnUpgrade);

            this.btnEditInstance = new M3IconButton(SVG.SETTINGS.createIcon());
            btnEditInstance.setOnAction(e -> {
                @Nullable GameListItem item = this.getItem();
                if (item != null)
                    item.modifyGameSettings();
            });
            FXUtils.installFastTooltip(btnEditInstance, i18n("instance.manage.manage"));
            right.getChildren().add(btnEditInstance);

            this.btnManage = new M3IconButton(SVG.MORE_VERT.createIcon());
            btnManage.setOnAction(e -> {
                @Nullable GameListItem item = this.getItem();
                if (item == null)
                    return;

                PopupMenu popup = getManagementPopup(item);
                if (popup.isShowing()) {
                    popup.hide();
                    return;
                }

                popup.showBelow(btnManage, root);
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
                PopupMenu popup = getManagementPopup(item);
                popup.showAt(root, e.getScreenX(), e.getScreenY());
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

        if (empty || item == null) {
            setRowGraphic(null);
        } else {
            setRowGraphic(this.graphic);

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
    private PopupMenu getManagementPopup(GameListItem item) {
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
    private static PopupMenu createPopup(GameListItem item) {
        PopupMenu popup = new PopupMenu();

        popup.getItems().setAll(
                PopupMenu.item(SVG.ROCKET_LAUNCH, i18n("instance.launch.test"), item::testGame),
                PopupMenu.item(SVG.SCRIPT, i18n("instance.launch_script"), item::generateLaunchScript),
                new M3Divider(),
                PopupMenu.item(SVG.EDIT, i18n("instance.manage.rename"), item::rename),
                PopupMenu.item(SVG.FOLDER_COPY, i18n("instance.manage.duplicate"), item::duplicate),
                PopupMenu.item(SVG.DELETE, i18n("instance.manage.remove"), item::remove),
                PopupMenu.item(SVG.OUTPUT, i18n("modpack.export"), item::export),
                new M3Divider(),
                PopupMenu.item(SVG.FOLDER_OPEN, i18n("folder.game"), item::browse),
                new M3Divider(),
                PopupMenu.item(SVG.SETTINGS, i18n("instance.manage.manage"), item::modifyGameSettings));
        return popup;
    }
}
