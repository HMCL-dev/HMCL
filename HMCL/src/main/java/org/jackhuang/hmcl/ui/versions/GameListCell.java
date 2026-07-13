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
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.ui.FXUtils.determineOptimalPopupPosition;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Renders either an instance card or a collapsible group header.
@NotNullByDefault
public final class GameListCell extends ListCell<GameListEntry> {
    /// Full height of an instance row before group animation is applied.
    private static final double INSTANCE_CELL_HEIGHT = 56;

    private final Region graphic;
    private final Region groupGraphic;
    private final Label groupTitle;
    private final Label groupCount;
    private final JFXButton groupToggle;
    private final JFXButton groupRename;
    private final JFXButton groupDelete;

    private final ImageContainer imageView;
    private final TwoLineListItem content;

    private final JFXRadioButton chkSelected;
    private final JFXButton btnUpgrade;
    private final JFXButton btnLaunch;
    private final JFXButton btnManage;

    private final HBox right;

    private final StringProperty tag = new SimpleStringProperty();

    public GameListCell() {
        FXUtils.setOverflowHidden(this);
        setPadding(Insets.EMPTY);

        BorderPane groupRoot = new BorderPane();
        groupRoot.getStyleClass().add("md-list-cell");
        groupRoot.setPadding(new Insets(4, 8, 4, 4));
        groupToggle = FXUtils.newToggleButton4(SVG.KEYBOARD_ARROW_DOWN);
        groupTitle = new Label();
        groupTitle.setStyle("-fx-font-weight: bold;");
        groupCount = new Label();
        groupCount.setStyle("-fx-text-fill: -monet-on-surface-variant;");
        HBox groupLeft = new HBox(8, groupToggle, groupTitle, groupCount);
        groupLeft.setAlignment(Pos.CENTER_LEFT);
        groupRoot.setLeft(groupLeft);
        groupRename = FXUtils.newToggleButton4(SVG.EDIT);
        groupDelete = FXUtils.newToggleButton4(SVG.DELETE);
        FXUtils.installFastTooltip(groupRename, i18n("version.group.rename"));
        FXUtils.installFastTooltip(groupDelete, i18n("version.group.delete"));
        HBox groupRight = new HBox(groupRename, groupDelete);
        groupRight.setAlignment(Pos.CENTER_RIGHT);
        groupRoot.setRight(groupRight);
        groupToggle.setOnAction(event -> {
            if (getItem() instanceof GameListGroupItem group) group.toggle();
        });
        groupRename.setOnAction(event -> {
            if (getItem() instanceof GameListGroupItem group) group.rename();
        });
        groupDelete.setOnAction(event -> {
            if (getItem() instanceof GameListGroupItem group) group.delete();
        });
        groupRoot.setOnMouseClicked(event -> {
            if (event.getTarget() instanceof Node target
                    && !isInside(target, groupToggle)
                    && !isInside(target, groupRight)
                    && getItem() instanceof GameListGroupItem group) {
                group.toggle();
            }
        });
        groupGraphic = groupRoot;

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
                        GameListItem item = getGameItem();
                        if (item != null) {
                            item.getRepository().setSelectedInstance(item.getId());
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
                GameListItem item = getGameItem();
                if (item != null)
                    item.update();
            });
            FXUtils.installFastTooltip(btnUpgrade, i18n("version.update"));
            right.getChildren().add(btnUpgrade);

            this.btnLaunch = FXUtils.newToggleButton4(SVG.ROCKET_LAUNCH);
            btnLaunch.setOnAction(e -> {
                GameListItem item = getGameItem();
                if (item != null)
                    item.testGame();
            });
            BorderPane.setAlignment(btnLaunch, Pos.CENTER);
            FXUtils.installFastTooltip(btnLaunch, i18n("version.launch.test"));
            right.getChildren().add(btnLaunch);

            this.btnManage = FXUtils.newToggleButton4(SVG.MORE_VERT);
            btnManage.setOnAction(e -> {
                GameListItem item = getGameItem();
                if (item == null)
                    return;

                JFXPopup popup = getPopup(item);
                JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(root, popup);
                popup.show(root, vPosition, JFXPopup.PopupHPosition.RIGHT, 0, vPosition == JFXPopup.PopupVPosition.TOP ? root.getHeight() : -root.getHeight());
            });
            BorderPane.setAlignment(btnManage, Pos.CENTER);
            FXUtils.installFastTooltip(btnManage, i18n("settings.game.management"));
            right.getChildren().add(btnManage);
        }

        root.setCursor(Cursor.HAND);
        container.setOnMouseClicked(e -> {
            GameListItem item = getGameItem();
            if (item == null)
                return;

            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 1) {
                    item.modifyGameSettings();
                }
            } else if (e.getButton() == MouseButton.SECONDARY) {
                JFXPopup popup = getPopup(item);
                JFXPopup.PopupVPosition vPosition = determineOptimalPopupPosition(root, popup);
                popup.show(root, vPosition, JFXPopup.PopupHPosition.LEFT, e.getX(), vPosition == JFXPopup.PopupVPosition.TOP ? e.getY() : e.getY() - root.getHeight());
            }
        });
    }

    @Override
    public void updateItem(GameListEntry entry, boolean empty) {
        super.updateItem(entry, empty);

        minHeightProperty().unbind();
        prefHeightProperty().unbind();
        maxHeightProperty().unbind();
        opacityProperty().unbind();
        groupToggle.rotateProperty().unbind();
        setMinHeight(Region.USE_COMPUTED_SIZE);
        setPrefHeight(Region.USE_COMPUTED_SIZE);
        setMaxHeight(Region.USE_COMPUTED_SIZE);
        setOpacity(1);
        this.imageView.imageProperty().unbind();
        this.content.titleProperty().unbind();
        this.content.subtitleProperty().unbind();
        this.tag.unbind();
        this.right.getChildren().clear();
        this.chkSelected.selectedProperty().unbind();

        if (empty || entry == null) {
            setGraphic(null);
        } else if (entry instanceof GameListGroupItem group) {
            groupTitle.setText(group.getName());
            groupCount.setText("(" + group.getSize() + ")");
            groupToggle.rotateProperty().bind(group.expansionProgressProperty().multiply(90).subtract(90));
            groupRename.setVisible(group.isManageable());
            groupRename.setManaged(group.isManageable());
            groupDelete.setVisible(group.isManageable());
            groupDelete.setManaged(group.isManageable());
            setGraphic(groupGraphic);
        } else {
            GameListItem item = (GameListItem) entry;
            setGraphic(this.graphic);

            minHeightProperty().bind(item.groupVisibilityProperty().multiply(INSTANCE_CELL_HEIGHT));
            prefHeightProperty().bind(item.groupVisibilityProperty().multiply(INSTANCE_CELL_HEIGHT));
            maxHeightProperty().bind(item.groupVisibilityProperty().multiply(INSTANCE_CELL_HEIGHT));
            opacityProperty().bind(item.groupVisibilityProperty());
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

    /// Returns the instance item currently displayed by this cell, or `null` for a group header.
    private @Nullable GameListItem getGameItem() {
        return getItem() instanceof GameListItem item ? item : null;
    }

    /// Returns whether a clicked node is inside the given control.
    private static boolean isInside(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static JFXPopup getPopup(GameListItem item) {
        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);

        menu.getContent().setAll(
                new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("version.launch.test"), item::testGame, popup),
                new IconedMenuItem(SVG.SCRIPT, i18n("version.launch_script"), item::generateLaunchScript, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.SETTINGS, i18n("version.manage.manage"), item::modifyGameSettings, popup),
                new IconedMenuItem(SVG.FOLDER, i18n("version.group.join"), item::joinGroup, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.EDIT, i18n("version.manage.rename"), item::rename, popup),
                new IconedMenuItem(SVG.FOLDER_COPY, i18n("version.manage.duplicate"), item::duplicate, popup),
                new IconedMenuItem(SVG.DELETE, i18n("version.manage.remove"), item::remove, popup),
                new IconedMenuItem(SVG.OUTPUT, i18n("modpack.export"), item::export, popup),
                new MenuSeparator(),
                new IconedMenuItem(SVG.FOLDER_OPEN, i18n("folder.game"), item::browse, popup));
        return popup;
    }
}
